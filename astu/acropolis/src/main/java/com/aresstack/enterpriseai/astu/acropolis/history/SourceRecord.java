package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Gespeicherte Quelle einer Antwort, wie die Oberfläche sie zeigte: Nummer im Kontextblock, Titel, Überschrift,
 * Ort (oder Kennung der Ressource), Stand, fusionierter Score und die Ränge in Volltext- und semantischer Suche.
 * Nie der Text der Quelle. Für die Diagnose (Chat-Export).
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
}
