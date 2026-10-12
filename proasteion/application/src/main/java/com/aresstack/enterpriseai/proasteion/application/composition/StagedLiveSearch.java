package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPreparation;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.application.config.LiveSearchConfig;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Live-Suche vor dem Retrieval des Chats, gestuft: Eine Suche wartet zuerst nur auf
 * {@link LiveSearchConfig#initialPages()} indexierte Treffer und erst dann, wenn die Evidenz nicht reicht
 * ({@link Stage#awaitMore()}), auf höchstens {@link LiveSearchConfig#maxPages()}. Die übrigen lädt der Vorlader im
 * Hintergrund weiter, ohne dass der Turn wartet. Die Grenzen werden je Suche gelesen (ohne Neustart).
 */
final class StagedLiveSearch implements RetrievalPreparation {

    /** Durchsucht die Quellen und wartet, bis die ersten {@code waitFor} Treffer indexiert sind. */
    interface Searcher {
        Found search(Collection<SourceAddress> sources, String query, int hitsPerSource, int waitFor);
    }

    /** Ergebnis einer Live-Suche, deren übrige Treffer noch indexiert werden. */
    interface Found {

        int hits();

        /** Wartet, bis die ersten {@code count} Treffer indexiert sind. */
        void awaitIndexed(int count);
    }

    private final Supplier<LiveSearchConfig> limits;
    private final Searcher searcher;

    StagedLiveSearch(Supplier<LiveSearchConfig> limits, Searcher searcher) {
        if (limits == null || searcher == null) {
            throw new IllegalArgumentException("limits and searcher must not be null");
        }
        this.limits = limits;
        this.searcher = searcher;
    }

    @Override
    public void prepare(String query, Collection<SourceAddress> sources) {
        LiveSearchConfig current = limits.get();
        searcher.search(sources, query, current.hitsPerSource(), current.maxPages());
    }

    @Override
    public Stage begin(String query, Collection<SourceAddress> sources) {
        final LiveSearchConfig current = limits.get();
        final Found found = searcher.search(sources, query, current.hitsPerSource(), current.initialPages());
        if (found.hits() <= current.initialPages() || current.maxPages() <= current.initialPages()) {
            return Stage.done();
        }
        final AtomicBoolean waited = new AtomicBoolean();
        return () -> {
            if (waited.getAndSet(true)) {
                return false;
            }
            found.awaitIndexed(current.maxPages());
            return true;
        };
    }
}
