package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Candidate reranking on title + excerpt before any full text is loaded (#154). It scores every received live hit
 * against the query with the selected cross-encoder (model category RERANK) and turns the scores into a load
 * priority. It never drops a hit: every candidate stays in the order, only later. The score means "promising for
 * deepening", never "evidence" or "used".
 *
 * <p>Optional by design: without a selected model, or when the reranker fails or answers with the wrong number of
 * scores, the source order is used as is ({@link Priority#ranked()} is false). A search never fails because of it.
 */
public final class ExcerptRanking {

    /**
     * Scores documents against a query: one score per document, in document order, higher is better. The composition
     * root adapts the reranker port to it; any exception means "no ranking this time".
     */
    public interface Scorer {
        double[] score(String modelId, String query, List<String> documents) throws Exception;
    }

    private final Scorer scorer;
    private final Supplier<String> modelId;

    /**
     * @param scorer  the reranker; {@code null} = never rank
     * @param modelId the selected RERANK model per call (settings without restart); {@code null} or empty = none
     */
    public ExcerptRanking(Scorer scorer, Supplier<String> modelId) {
        this.scorer = scorer;
        this.modelId = modelId;
    }

    /** No ranking: the source order stays the load order. */
    public static ExcerptRanking off() {
        return new ExcerptRanking(null, null);
    }

    /** Load priority of the hits of one search. */
    public static final class Priority {

        private final List<Integer> order;
        private final double[] scores;
        private final String fallback;

        private Priority(List<Integer> order, double[] scores, String fallback) {
            this.order = Collections.unmodifiableList(order);
            this.scores = scores;
            this.fallback = fallback;
        }

        /** Positions in the discovery order, best first; always a permutation of all hits. */
        public List<Integer> order() {
            return order;
        }

        /** Whether the reranker scored the hits; {@code false}: source order (no model, failure). */
        public boolean ranked() {
            return scores != null;
        }

        /** Score of the hit at discovery position {@code index}; only when {@link #ranked()}. */
        public double score(int index) {
            if (scores == null) {
                throw new IllegalStateException("not ranked");
            }
            return scores[index];
        }

        /** Why the source order was used ({@code null} when ranked, or when there was nothing to rank). */
        public String fallback() {
            return fallback;
        }
    }

    /** Scores all hits together and orders them best first; equal scores keep the discovery order. */
    public Priority prioritize(String query, List<FoundResource> hits) {
        int size = hits == null ? 0 : hits.size();
        List<Integer> sourceOrder = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            sourceOrder.add(i);
        }
        if (size == 0) {
            return new Priority(sourceOrder, null, null);
        }
        String model = model();
        if (scorer == null || model == null) {
            return new Priority(sourceOrder, null, "no reranker model selected");
        }
        List<String> documents = new ArrayList<String>(size);
        for (FoundResource hit : hits) {
            documents.add(hit.snippet().isEmpty() ? hit.title() : hit.title() + "\n" + hit.snippet());
        }
        final double[] scores;
        try {
            scores = scorer.score(model, query, documents);
        } catch (Exception e) {
            return new Priority(sourceOrder, null, "reranker failed (" + e.getClass().getSimpleName() + ")");
        }
        if (scores == null || scores.length != size) {
            return new Priority(sourceOrder, null, "reranker answered with a wrong number of scores");
        }
        for (double score : scores) {
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                return new Priority(sourceOrder, null, "reranker answered with an invalid score");
            }
        }
        List<Integer> order = new ArrayList<Integer>(sourceOrder);
        // List.sort is stable: equal scores keep the discovery order
        order.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(scores[b], scores[a]);
            }
        });
        return new Priority(order, scores.clone(), null);
    }

    private String model() {
        String id = modelId == null ? null : modelId.get();
        return id == null || id.trim().isEmpty() ? null : id.trim();
    }
}
