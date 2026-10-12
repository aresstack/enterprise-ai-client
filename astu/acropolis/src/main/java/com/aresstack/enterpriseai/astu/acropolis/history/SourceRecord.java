package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Gespeicherte Quelle einer Antwort, wie die Oberfläche sie zeigte: Nummer im Kontextblock, Titel, Überschrift,
 * Ort (oder Kennung der Ressource), Stand, fusionierter Score und die Ränge in Volltext- und semantischer Suche.
 * Nie der Text der Quelle. Für die Diagnose (Chat-Export) und, über die fachlichen Kennungen (Quelle, Ressource,
 * Abschnitt), für spätere Verweise auf genau diese Ressource; die Kennungen fehlen bei Chats ohne diese Angaben.
 */
public final class SourceRecord {

    private int number;
    private String title;
    private String heading;
    private String location;
    private String revision;
    private double score;
    private int keywordRank;
    private int semanticRank;
    /** Kennung der Wissensquelle ({@code KnowledgeSourceId}); {@code null} bei älteren Chats. */
    private String sourceId;
    /** Kennung der Ressource ({@code KnowledgeResourceId}), z. B. {@code confluence:47110815}; {@code null} bei älteren Chats. */
    private String resourceId;
    /** Kennung des Abschnitts ({@code KnowledgeChunkId}); {@code null} bei älteren Chats. */
    private String chunkId;

    /** Für die Deserialisierung. */
    public SourceRecord() {
    }

    public SourceRecord(int number, String title, String heading, String location, String revision, double score,
                        int keywordRank, int semanticRank) {
        this.number = number;
        this.title = title;
        this.heading = heading;
        this.location = location;
        this.revision = revision;
        this.score = score;
        this.keywordRank = keywordRank;
        this.semanticRank = semanticRank;
    }

    /** Wie oben, dazu die fachlichen Kennungen aus dem Index (leer oder {@code null}, wenn unbekannt). */
    public SourceRecord(int number, String title, String heading, String location, String revision, double score,
                        int keywordRank, int semanticRank, String sourceId, String resourceId, String chunkId) {
        this(number, title, heading, location, revision, score, keywordRank, semanticRank);
        this.sourceId = blankToNull(sourceId);
        this.resourceId = blankToNull(resourceId);
        this.chunkId = blankToNull(chunkId);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public String getHeading() {
        return heading != null ? heading : "";
    }

    public String getLocation() {
        return location != null ? location : "";
    }

    public String getRevision() {
        return revision != null ? revision : "";
    }

    public double getScore() {
        return score;
    }

    public int getKeywordRank() {
        return keywordRank;
    }

    public int getSemanticRank() {
        return semanticRank;
    }

    /** Kennung der Wissensquelle oder leer. */
    public String getSourceId() {
        return sourceId != null ? sourceId : "";
    }

    /** Kennung der Ressource oder leer (dann lässt sich die Quelle nicht gezielt wieder ansprechen). */
    public String getResourceId() {
        return resourceId != null ? resourceId : "";
    }

    /** Kennung des Abschnitts oder leer. */
    public String getChunkId() {
        return chunkId != null ? chunkId : "";
    }
}
