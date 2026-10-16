package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateOccurrence;
import com.aresstack.enterpriseai.searchrun.api.CandidateOrigin;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import com.aresstack.enterpriseai.searchrun.api.SearchRunStoreException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #153: every live search leaves a complete run; duplicates keep their occurrences; the log never fails a search. */
public class SearchRunRecordingTest {

    private static final Instant NOW = Instant.parse("2026-10-16T04:00:00Z");

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(new InMemoryKnowledgeIndex(), embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final KnowledgePrefetchService prefetch = new KnowledgePrefetchService(indexing,
            KnowledgeDocumentReader.direct(), KnowledgePrefetchService.Settings::defaults);
    private final MemoryRuns runs = new MemoryRuns();

    @After
    public void stop() {
        prefetch.shutdown();
    }

    private SearchDrivenIndexingUseCase useCase(KnowledgeSearchRunPort port) {
        return new SearchDrivenIndexingUseCase(prefetch, SourceCalls.direct(), port, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void everyReceivedHitIsACandidateInMergedOrder() {
        InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki");
        for (int i = 1; i <= 42; i++) {
            wiki.add("p" + i, "Antrag " + i, "Der Urlaubsantrag Nummer " + i + " wird genehmigt.");
        }
        SearchDrivenIndexingUseCase.Result result = useCase(runs)
                .search("chat", Collections.<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>singletonList(
                        wiki), "Urlaubsantrag", 50);

        assertTrue(result.runRecorded());
        KnowledgeSearchRun run = runs.find(result.run().id()).get();
        assertEquals("Urlaubsantrag", run.query());
        assertEquals(NOW, run.startedAt());
        assertEquals(Collections.singletonList(wiki.sourceId()), run.sources());
        assertEquals(42, run.candidates().size());
        assertEquals(result.hits().size(), run.candidates().size());
        for (int i = 0; i < run.candidates().size(); i++) {
            KnowledgeCandidate candidate = run.candidates().get(i);
            assertEquals(i + 1, candidate.number());
            assertEquals(result.hits().get(i).resourceId(), candidate.resourceId());
            assertEquals(result.hits().get(i).snippet(), candidate.snippet());
            assertEquals(wiki.sourceId(), candidate.sourceId());
            assertEquals(CandidateOrigin.SOURCE, candidate.origin());
            assertEquals(Collections.singletonList(new CandidateOccurrence(wiki.sourceId(), i)),
                    candidate.occurrences());
        }
        assertEquals("Antrag 18", run.candidate(18).get().title());
        for (CandidateEvent event : runs.events(run.id())) {
            assertTrue("without a reranker only queueing and loading are recorded: " + event,
                    event.kind() != com.aresstack.enterpriseai.searchrun.api.CandidateEventKind.EXCERPT_SCORED);
        }
    }

    @Test
    public void duplicateAcrossSourcesIsOneCandidateWithBothOccurrences() {
        KnowledgeResourceId shared = KnowledgeResourceId.of("doc:handbuch");
        FixedSource first = new FixedSource("a://one", Arrays.asList(
                new SourceSearchHit(KnowledgeResourceId.of("doc:eins"), "Eins", "x"),
                new SourceSearchHit(shared, "Handbuch", "aus a")));
        FixedSource second = new FixedSource("b://two", Arrays.asList(
                new SourceSearchHit(shared, "Handbuch", "aus b")));

        SearchDrivenIndexingUseCase.Result result = useCase(runs).search("chat",
                Arrays.<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>asList(first, second),
                "Handbuch", 10);

        KnowledgeSearchRun run = result.run();
        assertEquals(2, run.candidates().size());
        KnowledgeCandidate handbook = run.candidate(2).get();
        assertEquals(shared, handbook.resourceId());
        assertEquals("aus b", handbook.snippet());
        assertEquals(Arrays.asList(new CandidateOccurrence(second.sourceId(), 0),
                new CandidateOccurrence(first.sourceId(), 1)), handbook.occurrences());
    }

    @Test
    public void failedSourceIsPartOfTheRunAndFailingLogNeverFailsTheSearch() {
        FixedSource ok = new FixedSource("a://one", Collections.singletonList(
                new SourceSearchHit(KnowledgeResourceId.of("doc:eins"), "Eins", "x")));
        FixedSource broken = new FixedSource("b://two", null);
        KnowledgeSearchRunPort failing = new MemoryRuns() {
            @Override
            public void record(KnowledgeSearchRun run) {
                throw new SearchRunStoreException("disk full");
            }
        };

        SearchDrivenIndexingUseCase.Result result = useCase(failing).search("chat",
                Arrays.<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>asList(ok, broken), "Eins", 10);

        assertFalse(result.runRecorded());
        assertEquals(1, result.hits().size());
        assertEquals(Arrays.asList(ok.sourceId(), broken.sourceId()), result.run().sources());
        assertTrue(result.run().failures().containsKey(broken.sourceId()));
        assertEquals(1, result.run().candidates().size());
    }

    @Test
    public void withoutRunLogTheRunIsStillBuilt() {
        FixedSource ok = new FixedSource("a://one", Collections.singletonList(
                new SourceSearchHit(KnowledgeResourceId.of("doc:eins"), "Eins", "x")));
        SearchDrivenIndexingUseCase.Result result = new SearchDrivenIndexingUseCase(prefetch).search("chat",
                Collections.<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>singletonList(ok), "Eins", 10);
        assertFalse(result.runRecorded());
        assertEquals(1, result.run().candidates().size());
    }

    /** A searchable source with fixed hits; {@code null} hits: the search fails. */
    static final class FixedSource implements SearchableKnowledgeSource {

        private final SourceAddress id;
        private final List<SourceSearchHit> hits;

        FixedSource(String id, List<SourceSearchHit> hits) {
            this.id = SourceAddress.of(id);
            this.hits = hits;
        }

        @Override
        public SourceAddress sourceId() {
            return id;
        }

        @Override
        public List<SourceSearchHit> search(SourceQuery query) throws KnowledgeSourceException {
            if (hits == null) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE, "down");
            }
            return hits;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "not loadable in this test");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }
    }

    static class MemoryRuns implements KnowledgeSearchRunPort {

        private final Map<SearchRunId, KnowledgeSearchRun> runs = new LinkedHashMap<SearchRunId, KnowledgeSearchRun>();
        private final List<CandidateEvent> events = new ArrayList<CandidateEvent>();

        @Override
        public synchronized void record(KnowledgeSearchRun run) {
            runs.put(run.id(), run);
        }

        @Override
        public synchronized Optional<KnowledgeSearchRun> find(SearchRunId id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public synchronized void append(CandidateEvent event) {
            events.add(event);
        }

        @Override
        public synchronized void appendAll(List<CandidateEvent> list) {
            events.addAll(list);
        }

        @Override
        public synchronized List<CandidateEvent> events(SearchRunId id) {
            List<CandidateEvent> result = new ArrayList<CandidateEvent>();
            for (CandidateEvent event : events) {
                if (event.run().equals(id)) {
                    result.add(event);
                }
            }
            return result;
        }
    }
}
