package com.aresstack.enterpriseai.astu.acropolis.rag;

/**
 * Wann die Wissenssuche eines Chat-Turns genug Evidenz hat ({@link RagChatUseCase}, adaptiver Modus). Drei Grenzen:
 *
 * <ul>
 *   <li><b>Max. Quellen im Kontext</b>: harte Obergrenze, {@link ContextSettings#maxSources()} ({@code context.maxSources}).</li>
 *   <li><b>Mindestzahl unabhängiger Quellen</b> ({@link #minSources()}, {@code retrieval.minIndependentSources}): so viele
 *       verschiedene Ressourcen (resourceId, nicht Abschnitte derselben Seite) müssen die Antwort stützen, bevor
 *       „reicht“ gilt. 0 heißt: keine Mindestzahl, nur die Prüfung entscheidet. Standard 1.</li>
 *   <li><b>Max. Suchaufwand</b> ({@link #maxFollowUps()}, {@code retrieval.maxFollowUps}): Nachsuchen mit genauerer
 *       Anfrage, wenn die Treffer nicht reichen. Standard {@link FollowUpResearch#DEFAULT_MAX_FOLLOW_UPS}.</li>
 * </ul>
 *
 * Dazu, ob die Treffer vor der Übernahme gegen die Nachricht bewertet werden ({@link #reranking()},
 * {@code retrieval.rerank}), und wie viele Kandidaten ein gewählter Cross-Encoder an diese Bewertung weitergibt
 * ({@link #rerankTopK()}, {@code retrieval.rerankTopK}). Unveränderlich.
 */
public final class EvidencePolicy {

    public static final int DEFAULT_MIN_SOURCES = 1;
    public static final int MAX_MIN_SOURCES = 20;
    public static final int MAX_FOLLOW_UPS = 5;
    /** Candidates the cross-encoder passes on to the evaluator unless configured otherwise. */
    public static final int DEFAULT_RERANK_TOP_K = 10;
    public static final int MAX_RERANK_TOP_K = 40;

    private final int minSources;
    private final int maxFollowUps;
    private final boolean reranking;
    private final int rerankTopK;

    private EvidencePolicy(int minSources, int maxFollowUps, boolean reranking, int rerankTopK) {
        if (minSources < 0 || minSources > MAX_MIN_SOURCES) {
            throw new IllegalArgumentException("minSources must be within 0.." + MAX_MIN_SOURCES);
        }
        if (maxFollowUps < 0 || maxFollowUps > MAX_FOLLOW_UPS) {
            throw new IllegalArgumentException("maxFollowUps must be within 0.." + MAX_FOLLOW_UPS);
        }
        if (rerankTopK < 1 || rerankTopK > MAX_RERANK_TOP_K) {
            throw new IllegalArgumentException("rerankTopK must be within 1.." + MAX_RERANK_TOP_K);
        }
        this.minSources = minSources;
        this.maxFollowUps = maxFollowUps;
        this.reranking = reranking;
        this.rerankTopK = rerankTopK;
    }

    public static EvidencePolicy defaults() {
        return new EvidencePolicy(DEFAULT_MIN_SOURCES, FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS, true,
                DEFAULT_RERANK_TOP_K);
    }

    public EvidencePolicy withMinSources(int sources) {
        return new EvidencePolicy(sources, maxFollowUps, reranking, rerankTopK);
    }

    public EvidencePolicy withMaxFollowUps(int followUps) {
        return new EvidencePolicy(minSources, followUps, reranking, rerankTopK);
    }

    public EvidencePolicy withReranking(boolean enabled) {
        return new EvidencePolicy(minSources, maxFollowUps, enabled, rerankTopK);
    }

    public EvidencePolicy withRerankTopK(int topK) {
        return new EvidencePolicy(minSources, maxFollowUps, reranking, topK);
    }

    public int minSources() {
        return minSources;
    }

    public int maxFollowUps() {
        return maxFollowUps;
    }

    public boolean reranking() {
        return reranking;
    }

    /** How many candidates a selected cross-encoder passes on to the evaluator ({@link CrossEncoderRanking}). */
    public int rerankTopK() {
        return rerankTopK;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EvidencePolicy)) {
            return false;
        }
        EvidencePolicy that = (EvidencePolicy) other;
        return minSources == that.minSources && maxFollowUps == that.maxFollowUps && reranking == that.reranking
                && rerankTopK == that.rerankTopK;
    }

    @Override
    public int hashCode() {
        return ((minSources * 31 + maxFollowUps) * 2 + (reranking ? 1 : 0)) * 31 + rerankTopK;
    }

    @Override
    public String toString() {
        return "EvidencePolicy[minSources=" + minSources + ", maxFollowUps=" + maxFollowUps + ", reranking="
                + reranking + ", rerankTopK=" + rerankTopK + "]";
    }
}
