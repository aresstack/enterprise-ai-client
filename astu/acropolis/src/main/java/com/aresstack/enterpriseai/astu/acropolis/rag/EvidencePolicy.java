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
 * {@code retrieval.rerank}). Unveränderlich.
 */
public final class EvidencePolicy {

    public static final int DEFAULT_MIN_SOURCES = 1;
    public static final int MAX_MIN_SOURCES = 20;
    public static final int MAX_FOLLOW_UPS = 5;

    private final int minSources;
    private final int maxFollowUps;
    private final boolean reranking;

    private EvidencePolicy(int minSources, int maxFollowUps, boolean reranking) {
        if (minSources < 0 || minSources > MAX_MIN_SOURCES) {
            throw new IllegalArgumentException("minSources must be within 0.." + MAX_MIN_SOURCES);
        }
        if (maxFollowUps < 0 || maxFollowUps > MAX_FOLLOW_UPS) {
            throw new IllegalArgumentException("maxFollowUps must be within 0.." + MAX_FOLLOW_UPS);
        }
        this.minSources = minSources;
        this.maxFollowUps = maxFollowUps;
        this.reranking = reranking;
    }

    public static EvidencePolicy defaults() {
        return new EvidencePolicy(DEFAULT_MIN_SOURCES, FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS, true);
    }

    public EvidencePolicy withMinSources(int sources) {
        return new EvidencePolicy(sources, maxFollowUps, reranking);
    }

    public EvidencePolicy withMaxFollowUps(int followUps) {
        return new EvidencePolicy(minSources, followUps, reranking);
    }

    public EvidencePolicy withReranking(boolean enabled) {
        return new EvidencePolicy(minSources, maxFollowUps, enabled);
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

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EvidencePolicy)) {
            return false;
        }
        EvidencePolicy that = (EvidencePolicy) other;
        return minSources == that.minSources && maxFollowUps == that.maxFollowUps && reranking == that.reranking;
    }

    @Override
    public int hashCode() {
        return (minSources * 31 + maxFollowUps) * 2 + (reranking ? 1 : 0);
    }

    @Override
    public String toString() {
        return "EvidencePolicy[minSources=" + minSources + ", maxFollowUps=" + maxFollowUps + ", reranking="
                + reranking + "]";
    }
}
