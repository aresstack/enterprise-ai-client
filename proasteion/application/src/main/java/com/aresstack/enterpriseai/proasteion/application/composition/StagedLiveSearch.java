package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPreparation;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalProgress;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.application.config.LiveSearchConfig;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Live-Suche vor dem Retrieval des Chats, gestuft: Eine Suche wartet zuerst nur, bis
 * {@link LiveSearchConfig#initialPages()} Treffer geladen und lokal durchsuchbar sind (Rohindex), und erst dann, wenn
 * die Evidenz nicht reicht ({@link Stage#awaitMore()}), auf höchstens {@link LiveSearchConfig#maxPages()}. Auf
 * Passagen und Embeddings wartet sie nie; die bereitet der Vorlader im Hintergrund auf, ebenso die übrigen Treffer.
 * Die Grenzen werden je Suche gelesen (ohne Neustart).
 *
 * <p>Since #154 the series is in load order: with a reranker, {@code initialPages} are the best-scored hits by title +
 * excerpt, not the first ones of the source; {@code maxPages} likewise counts in that order. Without a reranker both
 * keep their old meaning (source order).
 */
final class StagedLiveSearch implements RetrievalPreparation {

    /** Durchsucht die Quellen und lädt die Treffer (Chat-Serie, vor dem übrigen Vorladen). */
    interface Searcher {
        Found search(Collection<SourceAddress> sources, String query, int hitsPerSource);
    }

    /** Ergebnis einer Live-Suche, deren Treffer noch geladen werden. */
    interface Found {

        int hits();

        /**
         * Wartet, bis die ersten {@code count} Treffer lokal durchsuchbar sind.
         *
         * @param listener erfährt jede verfügbar gewordene Seite (Anzahl bisher)
         */
        void awaitAvailable(int count, IntConsumer listener);

        /** The id of this search in the run log (#153); {@code null} when it was not recorded. */
        default String runId() {
            return null;
        }
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
        prepare(query, sources, RetrievalProgress.none());
    }

    @Override
    public void prepare(String query, Collection<SourceAddress> sources, RetrievalProgress progress) {
        LiveSearchConfig current = limits.get();
        Found found = searcher.search(sources, query, current.hitsPerSource());
        progress.liveHitsFound(found.hits());
        if (found.runId() != null) {
            progress.liveSearchRecorded(found.runId());
        }
        await(found, Math.min(found.hits(), current.maxPages()), progress);
    }

    @Override
    public Stage begin(String query, Collection<SourceAddress> sources) {
        return begin(query, sources, RetrievalProgress.none());
    }

    @Override
    public Stage begin(String query, Collection<SourceAddress> sources, final RetrievalProgress progress) {
        final LiveSearchConfig current = limits.get();
        final Found found = searcher.search(sources, query, current.hitsPerSource());
        progress.liveHitsFound(found.hits());
        if (found.runId() != null) {
            progress.liveSearchRecorded(found.runId());
        }
        await(found, Math.min(found.hits(), current.initialPages()), progress);
        if (found.hits() <= current.initialPages() || current.maxPages() <= current.initialPages()) {
            return Stage.done();
        }
        final AtomicBoolean waited = new AtomicBoolean();
        return () -> {
            if (waited.getAndSet(true)) {
                return false;
            }
            await(found, Math.min(found.hits(), current.maxPages()), progress);
            return true;
        };
    }

    private static void await(Found found, final int count, final RetrievalProgress progress) {
        if (count <= 0) {
            return;
        }
        progress.pagesAvailable(0, count);
        found.awaitAvailable(count, available -> progress.pagesAvailable(available, count));
    }
}
