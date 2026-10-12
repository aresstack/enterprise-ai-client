package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

/**
 * Eine Quelle, auf die sich eine Assistentenantwort stützt, so wie die Oberfläche sie zeigt: Nummer wie im
 * Kontextblock ({@code [1]}, {@code [2]} ...), Titel, optionale Überschrift innerhalb der Ressource, Ort, Stand und
 * Score. Reiner Anzeigewert ohne Use-Case- oder Port-Typen; befüllt wird er von der Anbindung in {@code app.chat}
 * aus den Quellen des RAG-Turns.
 *
 * <p>Der Ort ist laut Domänenregel frei von Zugangsdaten und wird unverändert angezeigt; alle Texte sind für
 * Menschen bestimmt und enthalten keine Secrets.
 */
public final class SourceReference {

    private final int number;
    private final String title;
    private final String heading;
    private final String location;
    private final String revision;
    private final double score;
    private final int keywordRank;
    private final int semanticRank;
    private final String sourceId;
    private final String resourceId;
    private final String chunkId;

    /**
     * @param number       Nummer ab 1, wie das Modell die Quelle zitiert
     * @param title        Titel der Ressource (nicht leer)
     * @param heading      Überschriftenkette des Abschnitts oder leer
     * @param location     aufrufbarer Ort oder, wenn die Quelle keinen nennt, ihre Kennung; nie leer
     * @param revision     Stand (Version und/oder Zeitpunkt) als Text oder leer, wenn unbekannt
     * @param score        fusionierter Score, nur innerhalb einer Antwort vergleichbar
     * @param keywordRank  Rang in der Volltextsuche, 0 wenn dort nicht gefunden
     * @param semanticRank Rang in der semantischen Suche, 0 wenn dort nicht gefunden
     */
    public SourceReference(int number, String title, String heading, String location, String revision, double score,
                           int keywordRank, int semanticRank) {
        this(number, title, heading, location, revision, score, keywordRank, semanticRank, "", "", "");
    }

    /**
     * Wie oben, dazu die Kennungen, unter denen die Anwendung die Quelle wieder findet (als Text; leer, wenn
     * unbekannt): Wissensquelle, Ressource und Abschnitt. Die Shell zeigt sie nicht, sie reicht sie nur durch.
     */
    public SourceReference(int number, String title, String heading, String location, String revision, double score,
                           int keywordRank, int semanticRank, String sourceId, String resourceId, String chunkId) {
        if (number < 1) {
            throw new IllegalArgumentException("number must be >= 1: " + number);
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("location must not be blank");
        }
        if (keywordRank < 0 || semanticRank < 0) {
            throw new IllegalArgumentException("ranks must not be negative");
        }
        this.number = number;
        this.title = title.trim();
        this.heading = heading == null ? "" : heading.trim();
        this.location = location.trim();
        this.revision = revision == null ? "" : revision.trim();
        this.score = score;
        this.keywordRank = keywordRank;
        this.semanticRank = semanticRank;
        this.sourceId = sourceId == null ? "" : sourceId.trim();
        this.resourceId = resourceId == null ? "" : resourceId.trim();
        this.chunkId = chunkId == null ? "" : chunkId.trim();
    }

    /** Kennung der Wissensquelle oder leer. */
    public String getSourceId() {
        return sourceId;
    }

    /** Kennung der Ressource oder leer (ältere gespeicherte Chats). */
    public String getResourceId() {
        return resourceId;
    }

    /** Kennung des Abschnitts oder leer. */
    public String getChunkId() {
        return chunkId;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    /** Überschriftenkette des Abschnitts ({@code Installation > Linux}) oder leer. */
    public String getHeading() {
        return heading;
    }

    public String getLocation() {
        return location;
    }

    /** Stand der Ressource als Text oder leer. */
    public String getRevision() {
        return revision;
    }

    public double getScore() {
        return score;
    }

    /** Rang in der Volltextsuche ab 1, oder 0, wenn die Volltextsuche den Abschnitt nicht fand. */
    public int getKeywordRank() {
        return keywordRank;
    }

    /** Rang in der semantischen Suche ab 1, oder 0, wenn sie den Abschnitt nicht fand. */
    public int getSemanticRank() {
        return semanticRank;
    }

    @Override
    public String toString() {
        // Titel und Ort gehören nicht in Logs; die Nummer und die Ränge reichen zur Diagnose.
        return "SourceReference{[" + number + "], keyword=" + keywordRank + ", semantic=" + semanticRank + '}';
    }
}
