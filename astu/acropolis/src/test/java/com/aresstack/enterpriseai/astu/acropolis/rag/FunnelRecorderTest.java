package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FunnelRecorderTest {

    private static final FunnelResource A = new FunnelResource("wiki", "wiki:a");
    private static final FunnelResource B = new FunnelResource("wiki", "wiki:b");
    private static final FunnelResource C = new FunnelResource("wiki", "wiki:c");

    private final MemoryFunnelLog log = new MemoryFunnelLog();

    @Test
    public void onlyRerankedExcerptsCountAsExaminedAllCandidatesAsDiscovered() {
        // the source may report 20000 total hits; the run received 3 and reranked 2 of them
        SearchRunId run = log.run("wiki", 2, "wiki:a", "wiki:b", "wiki:c");
        FunnelRecorder funnel = new FunnelRecorder(log);

        funnel.liveSearch(run.value());

        KnowledgeFunnel counted = funnel.snapshot();
        assertEquals(3, counted.discovered());
        assertEquals(Arrays.asList(A, B), counted.examined());
        assertTrue("an excerpt score alone is never relevance", counted.relevant().isEmpty());
        assertEquals(Collections.singletonList(run), counted.runs());
    }

    @Test
    public void withoutRerankingTheCandidatesAreDiscoveredButNotExamined() {
        SearchRunId run = log.run("wiki", 0, "wiki:a", "wiki:b");
        FunnelRecorder funnel = new FunnelRecorder(log);

        funnel.liveSearch(run.value());

        assertEquals(2, funnel.snapshot().discovered());
        assertTrue(funnel.snapshot().examined().isEmpty());
    }

    @Test
    public void indexedContentScoredByRetrievalIsExaminedWithoutAnyRun() {
        FunnelRecorder funnel = new FunnelRecorder(log);

        funnel.examined(Arrays.asList(A, B));

        assertEquals(0, funnel.snapshot().discovered());
        assertEquals(2, funnel.snapshot().examined().size());
    }

    @Test
    public void theSameResourceCountsOnceAcrossStagesAndRuns() {
        SearchRunId first = log.run("wiki", 2, "wiki:a", "wiki:b");
        FunnelRecorder funnel = new FunnelRecorder(log);

        funnel.liveSearch(first.value());
        funnel.liveSearch(first.value());
        funnel.examined(Arrays.asList(A, B, A, C));
        funnel.relevant(Arrays.asList(A, A));

        KnowledgeFunnel counted = funnel.snapshot();
        assertEquals("the same run counts once", 2, counted.discovered());
        assertEquals(Arrays.asList(A, B, C), counted.examined());
        assertEquals(Collections.singletonList(A), counted.relevant());
    }

    @Test
    public void usedIsWithinRelevantIsWithinExamined() {
        FunnelRecorder funnel = new FunnelRecorder(log);

        funnel.examined(Collections.singletonList(A));
        funnel.relevant(Collections.singletonList(B));
        funnel.used(Collections.singletonList(C));

        KnowledgeFunnel counted = funnel.finish(true);
        assertTrue(counted.relevant().containsAll(counted.used()));
        assertTrue(counted.examined().containsAll(counted.relevant()));
        assertEquals(Arrays.asList(A, B, C), counted.examined());
        assertEquals(Arrays.asList(B, C), counted.relevant());
        assertEquals(Collections.singletonList(C), counted.used());
    }

    @Test
    public void anAbortedRunKeepsItsCountsAndIsStored() {
        FunnelRecorder funnel = new FunnelRecorder(log);
        funnel.examined(Arrays.asList(A, B));
        funnel.phase(FunnelPhase.EVIDENCE);

        KnowledgeFunnel counted = funnel.finish(false);

        assertFalse(counted.complete());
        assertEquals(FunnelPhase.DONE, counted.phase());
        assertEquals(2, counted.examined().size());
        assertEquals(2, log.funnel(funnel.id()).get().examined().size());
        funnel.examined(Collections.singletonList(C));
        assertSame(counted, funnel.finish(true)); // finished is final
    }

    @Test
    public void aFailingLogNeverFailsTheRun() {
        SearchRunId run = log.run("wiki", 1, "wiki:a");
        log.failing = true;
        FunnelRecorder funnel = new FunnelRecorder(log);

        funnel.liveSearch(run.value());
        funnel.liveSearch("not a recorded run");
        funnel.used(Collections.singletonList(A));
        KnowledgeFunnel counted = funnel.finish(true);

        assertEquals(0, counted.discovered());
        assertEquals(1, counted.used().size());
        assertTrue(counted.complete());
    }

    @Test
    public void withoutLogTheFunnelStillCounts() {
        FunnelRecorder funnel = new FunnelRecorder(null);

        funnel.liveSearch("whatever");
        funnel.relevant(Collections.singletonList(A));

        assertEquals(1, funnel.finish(true).examined().size());
    }

    @Test
    public void funnelTypeEnforcesTheSubsets() {
        KnowledgeFunnel funnel = new KnowledgeFunnel(SearchRunId.random(), Instant.EPOCH, null, 0,
                Collections.<FunnelResource>emptyList(), Collections.<FunnelResource>emptyList(),
                Collections.singletonList(A), FunnelPhase.ANSWER, false);

        assertEquals(1, funnel.relevant().size());
        assertEquals(1, funnel.examined().size());
    }
}
