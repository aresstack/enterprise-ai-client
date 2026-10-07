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
 * <p>Unveränderlich; Änderungen über {@link #toBuilder()}.
 */
public final class RetrievalSettings {

    public static final int DEFAULT_CANDIDATES = 20;
    public static final int DEFAULT_MAX_RESULTS = 10;
    public static final int DEFAULT_RANK_CONSTANT = 60;
    /** Obergrenze je Pfad, gleich der Obergrenze des Index-Ports. */
    public static final int MAX_CANDIDATES = 1000;

    private final boolean keywordEnabled;
    private final boolean semanticEnabled;
    private final int keywordCandidates;
    private final int semanticCandidates;
    private final double keywordWeight;
    private final double semanticWeight;
    private final int rankConstant;
    private final int maxResults;
    private final double minSemanticScore;

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

        private Builder() {
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
