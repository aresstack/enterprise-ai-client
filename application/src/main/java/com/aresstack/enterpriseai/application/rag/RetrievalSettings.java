package com.aresstack.enterpriseai.application.rag;

/**
 * Konfiguration von {@link RetrieveKnowledgeUseCase}: welche Suchpfade laufen, wie viele Kandidaten jeder Pfad
 * liefert und wie die Ranglisten per Reciprocal Rank Fusion zusammengeführt werden.
 *
 * <p>Fusion: {@code score(c) = keywordWeight / (rankConstant + rang_keyword(c)) + semanticWeight / (rankConstant +
 * rang_semantic(c))}, Ränge ab 1, ein fehlender Rang trägt nichts bei. Rohscores von BM25 und Cosine sind nicht
 * vergleichbar; RRF verwendet deshalb nur Ränge. {@code rankConstant = 60} ist der übliche Wert aus der Literatur
 * (Cormack et al. 2009).
 *
 * <p>Nachbarschaft (1-Hop-Erweiterung über {@code KnowledgeRelationsPort}, nur wenn der Use Case damit verdrahtet
 * ist): die besten {@link #relationSeeds()} fusionierten Treffer holen je Kantenart höchstens
 * {@link #relationNeighbors()} Nachbarn (ähnliche Absätze ab {@link #relationMinSimilarity()}, Nachbarabsätze,
 * gleicher Abschnitt, Elternseite); ein Nachbar erhält {@code seedScore * relationWeight} (bei ähnlichen Absätzen
 * zusätzlich mal Cosine), Duplikate entfallen, danach wird wie bisher auf {@link #maxResults()} gekürzt.
 *
 * <p>Unveränderlich; Änderungen über {@link #toBuilder()}.
 */
public final class RetrievalSettings {

    public static final int DEFAULT_CANDIDATES = 20;
    public static final int DEFAULT_MAX_RESULTS = 10;
    public static final int DEFAULT_RANK_CONSTANT = 60;
    /** Obergrenze je Pfad, gleich der Obergrenze des Index-Ports. */
    public static final int MAX_CANDIDATES = 1000;
    public static final int DEFAULT_RELATION_SEEDS = 3;
    public static final int DEFAULT_RELATION_NEIGHBORS = 3;
    public static final double DEFAULT_RELATION_MIN_SIMILARITY = 0.80;
    public static final double DEFAULT_RELATION_WEIGHT = 0.5;

    private final boolean keywordEnabled;
    private final boolean semanticEnabled;
    private final int keywordCandidates;
    private final int semanticCandidates;
    private final double keywordWeight;
    private final double semanticWeight;
    private final int rankConstant;
    private final int maxResults;
    private final double minSemanticScore;
    private final boolean relationsEnabled;
    private final int relationSeeds;
    private final int relationNeighbors;
    private final double relationMinSimilarity;
    private final double relationWeight;

    private RetrievalSettings(Builder b) {
        this.keywordEnabled = b.keywordEnabled;
        this.semanticEnabled = b.semanticEnabled;
        this.keywordCandidates = b.keywordCandidates;
        this.semanticCandidates = b.semanticCandidates;
        this.keywordWeight = b.keywordWeight;
        this.semanticWeight = b.semanticWeight;
        this.rankConstant = b.rankConstant;
        this.maxResults = b.maxResults;
        this.minSemanticScore = b.minSemanticScore;
        this.relationsEnabled = b.relationsEnabled;
        this.relationSeeds = b.relationSeeds;
        this.relationNeighbors = b.relationNeighbors;
        this.relationMinSimilarity = b.relationMinSimilarity;
        this.relationWeight = b.relationWeight;
    }

    /** Beide Pfade, je 20 Kandidaten, Gewichte 1, k = 60, 10 Ergebnisse, kein Cosine-Schwellwert. */
    public static RetrievalSettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.keywordEnabled = keywordEnabled;
        b.semanticEnabled = semanticEnabled;
        b.keywordCandidates = keywordCandidates;
        b.semanticCandidates = semanticCandidates;
        b.keywordWeight = keywordWeight;
        b.semanticWeight = semanticWeight;
        b.rankConstant = rankConstant;
        b.maxResults = maxResults;
        b.minSemanticScore = minSemanticScore;
        b.relationsEnabled = relationsEnabled;
        b.relationSeeds = relationSeeds;
        b.relationNeighbors = relationNeighbors;
        b.relationMinSimilarity = relationMinSimilarity;
        b.relationWeight = relationWeight;
        return b;
    }

    public boolean keywordEnabled() {
        return keywordEnabled;
    }

    public boolean semanticEnabled() {
        return semanticEnabled;
    }

    public int keywordCandidates() {
        return keywordCandidates;
    }

    public int semanticCandidates() {
        return semanticCandidates;
    }

    public double keywordWeight() {
        return keywordWeight;
    }

    public double semanticWeight() {
        return semanticWeight;
    }

    public int rankConstant() {
        return rankConstant;
    }

    public int maxResults() {
        return maxResults;
    }

    /** Semantische Treffer unter diesem Cosine-Wert werden vor der Fusion verworfen; {@code -1} heißt keiner. */
    public double minSemanticScore() {
        return minSemanticScore;
    }

    /** Ob die besten Treffer um ihre Nachbarschaft erweitert werden. */
    public boolean relationsEnabled() {
        return relationsEnabled;
    }

    /** Wie viele der besten fusionierten Treffer Startpunkte der Erweiterung sind. */
    public int relationSeeds() {
        return relationSeeds;
    }

    /** Höchstzahl Nachbarn je Startpunkt und Kantenart. */
    public int relationNeighbors() {
        return relationNeighbors;
    }

    /** Cosine-Schwelle für ähnliche Absätze. */
    public double relationMinSimilarity() {
        return relationMinSimilarity;
    }

    /** Anteil des Startpunkt-Scores, den ein Nachbar erhält. */
    public double relationWeight() {
        return relationWeight;
    }

    @Override
    public String toString() {
        return "RetrievalSettings{keyword=" + (keywordEnabled ? keywordCandidates + "x" + keywordWeight : "off")
                + ", semantic=" + (semanticEnabled ? semanticCandidates + "x" + semanticWeight : "off")
                + ", k=" + rankConstant + ", maxResults=" + maxResults + ", minSemanticScore=" + minSemanticScore
                + "}";
    }

    public static final class Builder {

        private boolean keywordEnabled = true;
        private boolean semanticEnabled = true;
        private int keywordCandidates = DEFAULT_CANDIDATES;
        private int semanticCandidates = DEFAULT_CANDIDATES;
        private double keywordWeight = 1.0;
        private double semanticWeight = 1.0;
        private int rankConstant = DEFAULT_RANK_CONSTANT;
        private int maxResults = DEFAULT_MAX_RESULTS;
        private double minSemanticScore = -1.0;
        private boolean relationsEnabled = true;
        private int relationSeeds = DEFAULT_RELATION_SEEDS;
        private int relationNeighbors = DEFAULT_RELATION_NEIGHBORS;
        private double relationMinSimilarity = DEFAULT_RELATION_MIN_SIMILARITY;
        private double relationWeight = DEFAULT_RELATION_WEIGHT;

        private Builder() {
        }

        public Builder relationsEnabled(boolean enabled) {
            this.relationsEnabled = enabled;
            return this;
        }

        /** Startpunkte der Nachbarschaftserweiterung, 1 bis {@value #MAX_CANDIDATES}. */
        public Builder relationSeeds(int count) {
            this.relationSeeds = candidates(count);
            return this;
        }

        /** Nachbarn je Startpunkt und Kantenart, 1 bis {@value #MAX_CANDIDATES}. */
        public Builder relationNeighbors(int count) {
            this.relationNeighbors = candidates(count);
            return this;
        }

        /** Cosine-Schwelle für ähnliche Absätze in [-1, 1]. */
        public Builder relationMinSimilarity(double score) {
            if (Double.isNaN(score) || score < -1.0 || score > 1.0) {
                throw new IllegalArgumentException("relationMinSimilarity muss in [-1, 1] liegen: " + score);
            }
            this.relationMinSimilarity = score;
            return this;
        }

        /** Anteil des Startpunkt-Scores für Nachbarn, endlich und {@code >= 0}. */
        public Builder relationWeight(double weight) {
            this.relationWeight = weight(weight);
            return this;
        }

        public Builder keywordEnabled(boolean enabled) {
            this.keywordEnabled = enabled;
            return this;
        }

        public Builder semanticEnabled(boolean enabled) {
            this.semanticEnabled = enabled;
            return this;
        }

        /** Kandidaten aus der Volltextsuche, 1 bis {@value #MAX_CANDIDATES}. */
        public Builder keywordCandidates(int count) {
            this.keywordCandidates = candidates(count);
            return this;
        }

        /** Kandidaten aus der semantischen Suche, 1 bis {@value #MAX_CANDIDATES}. */
        public Builder semanticCandidates(int count) {
            this.semanticCandidates = candidates(count);
            return this;
        }

        /** Gewicht des Volltextrangs in der Fusion, endlich und {@code >= 0}. */
        public Builder keywordWeight(double weight) {
            this.keywordWeight = weight(weight);
            return this;
        }

        /** Gewicht des semantischen Rangs in der Fusion, endlich und {@code >= 0}. */
        public Builder semanticWeight(double weight) {
            this.semanticWeight = weight(weight);
            return this;
        }

        /** Die Konstante k der Reciprocal Rank Fusion, {@code >= 1}. */
        public Builder rankConstant(int k) {
            if (k < 1) {
                throw new IllegalArgumentException("rankConstant muss >= 1 sein: " + k);
            }
            this.rankConstant = k;
            return this;
        }

        /** Höchstzahl fusionierter Ergebnisse, {@code >= 1}. */
        public Builder maxResults(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("maxResults muss >= 1 sein: " + count);
            }
            this.maxResults = count;
            return this;
        }

        /** Cosine-Schwellwert in [-1, 1]; {@code -1} lässt alle semantischen Treffer zu. */
        public Builder minSemanticScore(double score) {
            if (Double.isNaN(score) || score < -1.0 || score > 1.0) {
                throw new IllegalArgumentException("minSemanticScore muss in [-1, 1] liegen: " + score);
            }
            this.minSemanticScore = score;
            return this;
        }

        /** @throws IllegalArgumentException wenn kein Pfad aktiv ist oder kein aktiver Pfad Gewicht hat */
        public RetrievalSettings build() {
            if (!keywordEnabled && !semanticEnabled) {
                throw new IllegalArgumentException("mindestens ein Suchpfad muss aktiv sein");
            }
            if ((!keywordEnabled || keywordWeight == 0.0) && (!semanticEnabled || semanticWeight == 0.0)) {
                throw new IllegalArgumentException("mindestens ein aktiver Suchpfad braucht ein Gewicht > 0");
            }
            return new RetrievalSettings(this);
        }

        private static int candidates(int count) {
            if (count < 1 || count > MAX_CANDIDATES) {
                throw new IllegalArgumentException("Kandidatenzahl muss in [1, " + MAX_CANDIDATES + "] liegen: "
                        + count);
            }
            return count;
        }

        private static double weight(double weight) {
            if (Double.isNaN(weight) || Double.isInfinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Gewicht muss endlich und >= 0 sein: " + weight);
            }
            return weight;
        }
    }
}
