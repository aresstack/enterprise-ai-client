package com.aresstack.enterpriseai.application.passage;

/** Eine Passage einer Quelle: Abschnittspfad, Text und ihr eigenes Embedding. */
public final class SourcePassage {

    private final String passageId;
    private final String sourceId;
    private final String headingPath;
    private final String text;
    private final int firstSentence;
    private final String embeddingFingerprint;
    private final float[] vector;

    public SourcePassage(String passageId, String sourceId, String headingPath, String text, int firstSentence,
                         String embeddingFingerprint, float[] vector) {
        this.passageId = passageId;
        this.sourceId = sourceId;
        this.headingPath = headingPath == null ? "" : headingPath;
        this.text = text;
        this.firstSentence = firstSentence;
        this.embeddingFingerprint = embeddingFingerprint == null ? "" : embeddingFingerprint;
        this.vector = vector == null ? new float[0] : vector.clone();
    }

    public String passageId() {
        return passageId;
    }

    public String sourceId() {
        return sourceId;
    }

    public String headingPath() {
        return headingPath;
    }

    public String text() {
        return text;
    }

    public int firstSentence() {
        return firstSentence;
    }

    public String embeddingFingerprint() {
        return embeddingFingerprint;
    }

    public float[] vector() {
        return vector.clone();
    }
}
