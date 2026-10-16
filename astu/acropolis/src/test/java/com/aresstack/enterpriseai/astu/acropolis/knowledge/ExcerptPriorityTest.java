package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** #154: excerpts are scored together, the score sets the load order, nothing is dropped, discovery stays intact. */
public class ExcerptPriorityTest {

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(new InMemoryKnowledgeIndex(), embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final KnowledgePrefetchService prefetch = new KnowledgePrefetchService(indexing,
            KnowledgeDocumentReader.direct(), KnowledgePrefetchService.Settings::defaults);
    private final SearchRunRecordingTest.MemoryRuns runs = new SearchRunRecordingTest.MemoryRuns();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("a", "Kantine", "Speiseplan der Kantine und Urlaub")
            .add("b", "Urlaubsantrag", "So stellt man einen Urlaubsantrag")
            .add("c", "Reisekosten", "Reisekosten nach dem Urlaub abrechnen");
    private final List<List<String>> scored = new ArrayList<List<String>>();

    @After
    public void stop() {
        prefetch.shutdown();
    }

    private SearchDrivenIndexingUseCase useCase(ExcerptRanking ranking) {
        SearchDrivenIndexingUseCase useCase = new SearchDrivenIndexingUseCase(prefetch, SourceCalls.direct(), runs,
                Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC));
        useCase.rankExcerptsWith(ranking);
        return useCase;
    }

    private ExcerptRanking scoring(final double... scores) {
        return new ExcerptRanking((model, query, documents) -> {
            scored.add(documents);
            return scores;
        }, () -> "rerank-model");
    }

    @Test
    public void higherScoredHitsLoadFirstAndNothingIsDropped() throws Exception {
        SearchDrivenIndexingUseCase.Result result = useCase(scoring(0.1, 0.9, 0.5))
                .search("chat", Collections.<KnowledgeSourcePort>singletonList(wiki), "Urlaub", 10);

        assertEquals("all excerpts are scored together, title + excerpt", 1, scored.size());
        assertEquals(3, scored.get(0).size());
        assertTrue(scored.get(0).get(1).startsWith("Urlaubsantrag\n"));
        assertTrue(result.priority().ranked());
        assertEquals(Arrays.asList(1, 2, 0), result.priority().order());
        assertEquals(Arrays.asList(wiki.idOf("b"), wiki.idOf("c"), wiki.idOf("a")),
                Arrays.asList(result.hits().get(0).resourceId(), result.hits().get(1).resourceId(),
                        result.hits().get(2).resourceId()));
        assertEquals(3, result.series().size());

        KnowledgeSearchRun run = result.run();
        assertEquals("discovery order is unchanged", wiki.idOf("a"), run.candidate(1).get().resourceId());
        assertEquals(wiki.idOf("b"), run.candidate(2).get().resourceId());

        List<CandidateEvent> scoredEvents = events(run, CandidateEventKind.EXCERPT_SCORED);
        assertEquals(3, scoredEvents.size());
        assertEquals(2, scoredEvents.get(0).candidateNumber());
        assertEquals(0.9, scoredEvents.get(0).score(), 0.0);
        assertEquals("priority 1", scoredEvents.get(0).detail());
        List<CandidateEvent> queued = events(run, CandidateEventKind.PREFETCH_QUEUED);
        assertEquals(Arrays.asList(2, 3, 1), numbers(queued));

        result.series().awaitIndexed(3, 10000);
        long deadline = System.currentTimeMillis() + 5000;
        while (events(run, CandidateEventKind.LOADED).size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(3, events(run, CandidateEventKind.LOADED).size());
    }

    @Test
    public void failingRerankerFallsBackToSourceOrder() {
        ExcerptRanking failing = new ExcerptRanking((model, query, documents) -> {
            throw new IllegalStateException("down");
        }, () -> "rerank-model");
        SearchDrivenIndexingUseCase.Result result = useCase(failing)
                .search("chat", Collections.<KnowledgeSourcePort>singletonList(wiki), "Urlaub", 10);

        assertFalse(result.priority().ranked());
        assertTrue(result.priority().fallback().contains("IllegalStateException"));
        assertEquals(Arrays.asList(0, 1, 2), result.priority().order());
        assertEquals(3, result.hits().size());
        assertTrue(events(result.run(), CandidateEventKind.EXCERPT_SCORED).isEmpty());
    }

    @Test
    public void wrongNumberOfScoresFallsBackAndNoModelMeansNoCall() {
        assertFalse(useCase(scoring(0.5)).search("chat", Collections.<KnowledgeSourcePort>singletonList(wiki),
                "Urlaub", 10).priority().ranked());
        ExcerptRanking noModel = new ExcerptRanking((model, query, documents) -> {
            throw new AssertionError("must not be called without a model");
        }, () -> " ");
        SearchDrivenIndexingUseCase.Result result = useCase(noModel)
                .search("chat", Collections.<KnowledgeSourcePort>singletonList(wiki), "Urlaub", 10);
        assertFalse(result.priority().ranked());
        assertEquals("no reranker model selected", result.priority().fallback());
    }

    @Test
    public void equalScoresKeepTheDiscoveryOrder() {
        ExcerptRanking.Priority priority = scoring(0.5, 0.5, 0.7).prioritize("q", Arrays.asList(
                hit("a"), hit("b"), hit("c")));
        assertEquals(Arrays.asList(2, 0, 1), priority.order());
        assertNull(priority.fallback());
    }

    private FoundResource hit(String key) {
        return new FoundResource(wiki.sourceId(), wiki.idOf(key), key, "", null, FoundResource.Origin.SOURCE);
    }

    private List<CandidateEvent> events(KnowledgeSearchRun run, CandidateEventKind kind) {
        List<CandidateEvent> result = new ArrayList<CandidateEvent>();
        for (CandidateEvent event : runs.events(run.id())) {
            if (event.kind() == kind) {
                result.add(event);
            }
        }
        return result;
    }

    private static List<Integer> numbers(List<CandidateEvent> events) {
        List<Integer> result = new ArrayList<Integer>();
        for (CandidateEvent event : events) {
            result.add(event.candidateNumber());
        }
        return result;
    }
}
