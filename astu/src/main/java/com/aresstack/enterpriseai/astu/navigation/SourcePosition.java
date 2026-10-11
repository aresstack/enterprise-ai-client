package com.aresstack.enterpriseai.astu.navigation;

/**
 * Wo ein Navigationsabschnitt im Originaldokument steht: Ressource (stabil über Fassungen), Capture (voller SHA-256
 * der konkreten Fassung, siehe {@code KnowledgePassageId.captureOf}) und Zeichenpositionen in deren kanonischer
 * Textfassung ({@code KnowledgeDocument.text()}, genau der Text, den der Lesebereich zeigt). {@link #anchor()} sind die
 * ersten Worte an der Startposition, damit die Ansicht die Stelle auch innerhalb eines gerenderten Absatzes findet.
 */
public final class SourcePosition {

    private final String resourceId;
    private final String captureId;
    private final int start;
    private final int end;
    private final String anchor;

    /**
     * @param end Endposition (exklusiv) oder {@code -1}, wenn offen
     */
    public SourcePosition(String resourceId, String captureId, int start, int end, String anchor) {
        if (resourceId == null || resourceId.isEmpty() || captureId == null) {
            throw new IllegalArgumentException("resourceId und captureId sind Pflicht");
        }
        if (start < 0 || (end >= 0 && end < start)) {
            throw new IllegalArgumentException("ungültige Position " + start + ".." + end);
        }
        this.resourceId = resourceId;
        this.captureId = captureId;
        this.start = start;
        this.end = end;
        this.anchor = anchor == null ? "" : anchor;
    }

    public String resourceId() {
        return resourceId;
    }

    public String captureId() {
        return captureId;
    }

    /** Startposition (Zeichen, ab 0) in der kanonischen Textfassung. */
    public int start() {
        return start;
    }

    /** Endposition (exklusiv) oder {@code -1}. */
    public int end() {
        return end;
    }

    public boolean hasEnd() {
        return end >= 0;
    }

    public String anchor() {
        return anchor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourcePosition)) {
            return false;
        }
        SourcePosition that = (SourcePosition) other;
        return start == that.start && end == that.end && resourceId.equals(that.resourceId)
                && captureId.equals(that.captureId) && anchor.equals(that.anchor);
    }

    @Override
    public int hashCode() {
        return ((resourceId.hashCode() * 31 + captureId.hashCode()) * 31 + start) * 31 + end;
    }

    @Override
    public String toString() {
        return resourceId + "@" + (captureId.length() > 12 ? captureId.substring(0, 12) : captureId) + "[" + start
                + ".." + end + "]";
    }
}
