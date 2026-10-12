package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPreparation;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.application.config.LiveSearchConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Gestufte Live-Suche: erst wenige Seiten, mehr nur auf Nachfrage, Grenzen je Suche aus den Einstellungen. */
public class StagedLiveSearchTest {

    private final List<String> calls = new ArrayList<String>();
    private int hits = 10;

    private final StagedLiveSearch.Searcher searcher = new StagedLiveSearch.Searcher() {
        @Override
        public StagedLiveSearch.Found search(Collection<SourceAddress> sources, String query, int hitsPerSource,
                                             int waitFor) {
            calls.add("search hits=" + hitsPerSource + " wait=" + waitFor);
            final int found = hits;
            return new StagedLiveSearch.Found() {
                @Override
                public int hits() {
                    return found;
                }

                @Override
                public void awaitIndexed(int count) {
                    calls.add("await " + count);
                }
            };
        }
    };

    @Test
    public void firstEvaluationWaitsForTheInitialPagesOnly() {
        StagedLiveSearch live = new StagedLiveSearch(() -> new LiveSearchConfig(1, 4, 10), searcher);

        live.begin("Frage", Collections.<SourceAddress>emptySet());

        assertEquals(Collections.singletonList("search hits=10 wait=1"), calls);
    }

    @Test
    public void moreIsAwaitedOnceUpToMaxPages() {
        StagedLiveSearch live = new StagedLiveSearch(() -> new LiveSearchConfig(1, 6, 10), searcher);

        RetrievalPreparation.Stage stage = live.begin("Frage", Collections.<SourceAddress>emptySet());

        assertTrue(stage.awaitMore());
        assertFalse("höchstens maxPages, nicht mehr", stage.awaitMore());
        assertEquals(java.util.Arrays.asList("search hits=10 wait=1", "await 6"), calls);
    }

    @Test
    public void nothingMoreToAwaitWhenTheSearchFoundOnlyTheInitialPages() {
        hits = 1;
        StagedLiveSearch live = new StagedLiveSearch(() -> new LiveSearchConfig(1, 4, 10), searcher);

        assertFalse(live.begin("Frage", Collections.<SourceAddress>emptySet()).awaitMore());
        assertEquals(1, calls.size());
    }

    @Test
    public void changedLimitsApplyToTheNextSearchWithoutRestart() {
        AtomicReference<LiveSearchConfig> current = new AtomicReference<LiveSearchConfig>(new LiveSearchConfig(1, 4, 10));
        StagedLiveSearch live = new StagedLiveSearch(current::get, searcher);
        live.begin("Frage", Collections.<SourceAddress>emptySet()).awaitMore();

        current.set(new LiveSearchConfig(2, 6, 20));
        live.begin("Frage", Collections.<SourceAddress>emptySet()).awaitMore();

        assertEquals(java.util.Arrays.asList("search hits=10 wait=1", "await 4", "search hits=20 wait=2", "await 6"),
                calls);
    }

    @Test
    public void plainPrepareWaitsForMaxPages() {
        StagedLiveSearch live = new StagedLiveSearch(() -> new LiveSearchConfig(1, 4, 10), searcher);

        live.prepare("Frage", Collections.<SourceAddress>emptySet());

        assertEquals(Collections.singletonList("search hits=10 wait=4"), calls);
    }
}
