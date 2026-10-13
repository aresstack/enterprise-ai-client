package com.aresstack.enterpriseai.astu.acropolis.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The cross-encoder step of the adaptive knowledge search (model category RERANK, {@code model.rerank}): after
 * retrieval, fusion and relation expansion, and before the {@link EvidenceEvaluator}, it scores every offered candidate
 * on its own against the user's question and passes on only the best {@link EvidencePolicy#rerankTopK()}, best first.
 * The evaluator still decides relevance, sufficiency, minimum evidence, gaps and the follow-up search.
 *
 * <p>Optional by design: without a selected model, or when the reranker fails, the candidates pass unchanged (the path
 * as it was before: fusion, then evaluator). A turn never fails because of the reranker.
 */
public final class CrossEncoderRanking {

    /**
     * Scores documents against a query with a cross-encoder model: one score per document, in document order, higher
     * is better. The composition root adapts the reranker port to it; any exception means "no ranking this time".
     */
    public interface Scorer {
        double[] score(String modelId, String query, List<String> documents) throws Exception;
    }

    private final Scorer scorer;
    private final Supplier<String> modelId;
    private final IntSupplier topK;

    /**
     * @param scorer    the reranker; {@code null} = never rank
     * @param modelId the selected RERANK model per call (settings without restart); {@code null} or empty = none
     * @param topK    how many candidates go on to the evaluator, per call
     */
    public CrossEncoderRanking(Scorer scorer, Supplier<String> modelId, IntSupplier topK) {
        if (topK == null) {
            throw new IllegalArgumentException("topK must not be null");
        }
        this.scorer = scorer;
        this.modelId = modelId;
        this.topK = topK;
    }

    /** No ranking: every call passes the candidates unchanged. */
    public static CrossEncoderRanking off() {
        return new CrossEncoderRanking(null, null, () -> Integer.MAX_VALUE);
    }

    /** Whether a model is selected, so the next call will ask the reranker. */
    public boolean isActive() {
        return scorer != null && model() != null;
    }

    /** The outcome of one ranking call. */
    public static final class Result {
        private final Map<String, RetrievedChunk> candidates;
        private final boolean ranked;

        Result(Map<String, RetrievedChunk> candidates, boolean ranked) {
            this.candidates = Collections.unmodifiableMap(candidates);
            this.ranked = ranked;
        }

        /** The candidates for the evaluator under their stable ids, best first when {@link #ranked()}. */
        public Map<String, RetrievedChunk> candidates() {
            return candidates;
        }

        /** Whether the cross-encoder ranked them; {@code false} = passed unchanged (no model, failure). */
        public boolean ranked() {
            return ranked;
        }
    }

    /**
     * Rank {@code offered} against {@code question} and keep the best {@code topK}; without a model or on any failure
     * return them unchanged.
     */
    public Result rank(String question, Map<String, RetrievedChunk> offered) {
        String model = model();
        if (scorer == null || model == null || offered == null || offered.isEmpty() || question == null
                || question.trim().isEmpty()) {
            return unchanged(offered);
        }
        List<String> ids = new ArrayList<String>(offered.keySet());
        List<String> documents = new ArrayList<String>(ids.size());
        for (String id : ids) {
            documents.add(documentText(offered.get(id)));
        }
        final double[] scores;
        try {
            scores = scorer.score(model, question, documents);
        } catch (Exception e) {
            return unchanged(offered);
        }
        if (scores == null || scores.length != ids.size()) {
            return unchanged(offered);
        }
        for (double score : scores) {
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                return unchanged(offered);
            }
        }
        List<Integer> order = new ArrayList<Integer>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            order.add(i);
        }
        // Stable: equal scores keep the fusion order.
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(scores[b], scores[a]);
            }
        });
        int keep = Math.max(1, Math.min(order.size(), topK.getAsInt()));
        Map<String, RetrievedChunk> best = new LinkedHashMap<String, RetrievedChunk>();
        for (Integer index : order.subList(0, keep)) {
            String id = ids.get(index);
            best.put(id, offered.get(id));
        }
        return new Result(best, true);
    }

    /** The text a candidate is scored by: title, heading path and passage, never its address. */
    static String documentText(RetrievedChunk candidate) {
        String title = candidate.resource().title();
        String text = candidate.chunk().textWithHeading();
        return title == null || title.trim().isEmpty() ? text : title.trim() + "\n" + text;
    }

    private String model() {
        String id = modelId == null ? null : modelId.get();
        return id == null || id.trim().isEmpty() ? null : id.trim();
    }

    private static Result unchanged(Map<String, RetrievedChunk> offered) {
        return new Result(offered == null ? new LinkedHashMap<String, RetrievedChunk>()
                : new LinkedHashMap<String, RetrievedChunk>(offered), false);
    }
}
