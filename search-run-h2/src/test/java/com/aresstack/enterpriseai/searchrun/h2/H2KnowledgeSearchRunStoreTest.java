package com.aresstack.enterpriseai.searchrun.h2;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.CandidateOccurrence;
import com.aresstack.enterpriseai.searchrun.api.CandidateOrigin;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import com.aresstack.enterpriseai.searchrun.api.SearchRunStoreException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class H2KnowledgeSearchRunStoreTest {

    private static final SourceAddress WIKI = SourceAddress.of("confluence://cf.intern/confluence");
    private static final SourceAddress GIT = SourceAddress.of("git://repo.intern/handbook");
    private static final Instant STARTED = Instant.parse("2026-10-16T03:00:00Z");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path directory;
    private H2KnowledgeSearchRunStore store;

    @Before
    public void open() throws Exception {
        directory = folder.newFolder("search-runs").toPath();
        store = new H2KnowledgeSearchRunStore(directory, 1000);
    }

    @After
    public void close() {
        store.close();
    }

    @Test
    public void runWithManyCandidatesAndNoFullTextSurvivesRestart() {
        List<KnowledgeCandidate> candidates = new ArrayList<KnowledgeCandidate>();
        for (int i = 1; i <= 42; i++) {
            candidates.add(candidate(i, "page-" + i, i - 1));
        }
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "Urlaubsantrag Ablauf", STARTED,
                Collections.singletonList(WIKI), null, candidates);
        store.record(run);
        store.close();

        store = new H2KnowledgeSearchRunStore(directory, 1000);
        KnowledgeSearchRun read = store.find(run.id()).get();
        assertEquals("Urlaubsantrag Ablauf", read.query());
        assertEquals(STARTED, read.startedAt());
        assertEquals(42, read.candidates().size());
        KnowledgeCandidate eighteen = read.candidate(18).get();
        assertEquals(KnowledgeResourceId.of("confluence:page-18"), eighteen.resourceId());
        assertEquals(WIKI, eighteen.sourceId());
        assertEquals("Titel page-18", eighteen.title());
        assertEquals("Auszug page-18", eighteen.snippet());
        assertEquals(KnowledgeRevision.of(Instant.parse("2026-01-02T03:04:05Z"), "7"), eighteen.revision());
        assertEquals(CandidateOrigin.SOURCE, eighteen.origin());
        assertNull(eighteen.url());
        assertTrue(store.events(run.id()).isEmpty());
    }

    @Test
    public void occurrencesAndFailuresAreKept() {
        KnowledgeCandidate both = new KnowledgeCandidate(1, WIKI, KnowledgeResourceId.of("confluence:42"), "T", "S",
                "https://cf.intern/x", KnowledgeRevision.unknown(), CandidateOrigin.SOURCE,
                Arrays.asList(new CandidateOccurrence(WIKI, 0), new CandidateOccurrence(GIT, 3)));
        Map<SourceAddress, String> failures = new LinkedHashMap<SourceAddress, String>();
        failures.put(GIT, "Zeitüberschreitung");
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "q", STARTED,
                Arrays.asList(WIKI, GIT), failures, Collections.singletonList(both));
        store.record(run);

        KnowledgeSearchRun read = store.find(run.id()).get();
        assertEquals(Arrays.asList(WIKI, GIT), read.sources());
        assertEquals("Zeitüberschreitung", read.failures().get(GIT));
        assertFalse(read.failures().containsKey(WIKI));
        assertEquals(Arrays.asList(new CandidateOccurrence(WIKI, 0), new CandidateOccurrence(GIT, 3)),
                read.candidate(1).get().occurrences());
        assertEquals("https://cf.intern/x", read.candidate(1).get().url());
        assertFalse(read.candidate(1).get().revision().isKnown());
    }

    @Test
    public void eventsAreAppendedSeparatelyAndLeaveTheCandidateUnchanged() {
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "q", STARTED,
                Collections.singletonList(WIKI), null, Arrays.asList(candidate(1, "a", 0), candidate(2, "b", 1)));
        store.record(run);
        store.append(new CandidateEvent(run.id(), 2, CandidateEventKind.DEEPENING_REQUESTED, STARTED, null, null));
        store.append(new CandidateEvent(run.id(), 2, CandidateEventKind.LOAD_FAILED, STARTED.plusSeconds(1),
                "HTTP 503", 0.25));

        List<CandidateEvent> events = store.events(run.id());
        assertEquals(2, events.size());
        assertEquals(CandidateEventKind.DEEPENING_REQUESTED, events.get(0).kind());
        assertNull(events.get(0).score());
        assertEquals(CandidateEventKind.LOAD_FAILED, events.get(1).kind());
        assertEquals("HTTP 503", events.get(1).detail());
        assertEquals(0.25, events.get(1).score(), 0.0);
        assertEquals("Auszug b", store.find(run.id()).get().candidate(2).get().snippet());
    }

    @Test
    public void eventForUnknownCandidateIsRefused() {
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "q", STARTED,
                Collections.singletonList(WIKI), null, Collections.singletonList(candidate(1, "a", 0)));
        store.record(run);
        try {
            store.append(new CandidateEvent(run.id(), 5, CandidateEventKind.LOADED, STARTED, null, null));
            fail("event for a missing candidate must be refused");
        } catch (SearchRunStoreException expected) {
            // expected
        }
        assertTrue(store.events(run.id()).isEmpty());
    }

    @Test
    public void appendAllKeepsListOrderAndIsAllOrNothing() {
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "q", STARTED,
                Collections.singletonList(WIKI), null, Arrays.asList(candidate(1, "a", 0), candidate(2, "b", 1)));
        store.record(run);
        store.appendAll(Arrays.asList(
                new CandidateEvent(run.id(), 2, CandidateEventKind.EXCERPT_SCORED, STARTED, "priority 1", 0.9),
                new CandidateEvent(run.id(), 1, CandidateEventKind.EXCERPT_SCORED, STARTED, "priority 2", 0.1)));
        try {
            store.appendAll(Arrays.asList(
                    new CandidateEvent(run.id(), 1, CandidateEventKind.PREFETCH_QUEUED, STARTED, null, null),
                    new CandidateEvent(run.id(), 9, CandidateEventKind.PREFETCH_QUEUED, STARTED, null, null)));
            fail("a batch with an unknown candidate must be refused");
        } catch (SearchRunStoreException expected) {
            // expected
        }
        List<CandidateEvent> events = store.events(run.id());
        assertEquals(2, events.size());
        assertEquals(2, events.get(0).candidateNumber());
        assertEquals(1, events.get(1).candidateNumber());
    }

    @Test
    public void runIdIsWrittenOnlyOnce() {
        KnowledgeSearchRun run = new KnowledgeSearchRun(SearchRunId.random(), "q", STARTED,
                Collections.singletonList(WIKI), null, Collections.singletonList(candidate(1, "a", 0)));
        store.record(run);
        try {
            store.record(new KnowledgeSearchRun(run.id(), "other", STARTED, null, null, null));
            fail("a recorded run must not be overwritten");
        } catch (SearchRunStoreException expected) {
            // expected
        }
        assertEquals("q", store.find(run.id()).get().query());
        assertEquals(1, store.find(run.id()).get().candidates().size());
    }

    @Test
    public void unknownRunIsEmpty() {
        assertFalse(store.find(SearchRunId.of("missing")).isPresent());
    }

    private static KnowledgeCandidate candidate(int number, String page, int rank) {
        return new KnowledgeCandidate(number, WIKI, KnowledgeResourceId.of("confluence:" + page), "Titel " + page,
                "Auszug " + page, null, KnowledgeRevision.of(Instant.parse("2026-01-02T03:04:05Z"), "7"),
                CandidateOrigin.SOURCE, Collections.singletonList(new CandidateOccurrence(WIKI, rank)));
    }
}
