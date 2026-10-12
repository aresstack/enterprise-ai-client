package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

/**
 * Ein Verweis auf etwas Lesbares in einer Wissensquelle, wie Trefferliste, Quellenübersicht und Favoriten ihn zeigen:
 * Quelle, Ressource (die Adresse mit Präfix, z. B. {@code wiki:intranet/Handbuch}; leer = die Quelle selbst), Titel,
 * Auszug und Fundstelle. Ohne Ressource kann ein Startpunkt der Quelle ({@link #startPoint()}, z. B. ein Confluence-Space
 * ohne Startseite) den Ausschnitt der Übersicht festlegen. Reine Anzeigedaten ohne Port- oder Adaptertypen;
 * unveränderlich.
 */
public final class ReaderLink {

    private final String sourceId;
    private final String resourceId;
    private final String title;
    private final String snippet;
    private final String location;
    private final String startPoint;
    private final String passageId;

    public ReaderLink(String sourceId, String resourceId, String title, String snippet, String location) {
        this(sourceId, resourceId, title, snippet, location, "", "");
    }

    private ReaderLink(String sourceId, String resourceId, String title, String snippet, String location,
                       String startPoint, String passageId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        this.sourceId = sourceId.trim();
        this.resourceId = resourceId == null ? "" : resourceId.trim();
        this.title = title == null || title.trim().isEmpty() ? (this.resourceId.isEmpty() ? this.sourceId
                : this.resourceId) : title.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
        this.location = location == null || location.trim().isEmpty() ? this.resourceId : location.trim();
        this.startPoint = this.resourceId.isEmpty() && startPoint != null ? startPoint.trim() : "";
        this.passageId = this.resourceId.isEmpty() || passageId == null ? "" : passageId.trim();
    }

    /**
     * Carry the passage (chunk) this link points into, e.g. the chunk a chat answer cited. Keep it out of
     * {@link #key()}: the reader still opens one tab per resource, the passage only says where to look.
     */
    public ReaderLink withPassage(String passage) {
        return new ReaderLink(sourceId, resourceId, title, snippet, location, startPoint, passage);
    }

    /** Die Quelle selbst (Übersicht ihrer Ressourcen). */
    public static ReaderLink source(String sourceId, String description) {
        return new ReaderLink(sourceId, "", sourceId, "", description);
    }

    /**
     * Die Übersicht eines Bereichs der Quelle ohne eigene Startseite (MainframeMate: Seiten des Space anzeigen):
     * die Ressourcen unter dem Startpunkt {@code startPoint}.
     */
    public static ReaderLink partition(String sourceId, String startPoint, String title) {
        return new ReaderLink(sourceId, "", title, "", startPoint, startPoint, "");
    }

    public String sourceId() {
        return sourceId;
    }

    /** Die Ressourcen-ID mit Präfix; leer, wenn der Verweis die Quelle selbst meint. */
    public String resourceId() {
        return resourceId;
    }

    /** Startpunkt der Übersicht; leer = der eingestellte Umfang der Quelle. */
    public String startPoint() {
        return startPoint;
    }

    /** Return the passage (chunk id) inside the resource; empty means the whole resource. */
    public String passageId() {
        return passageId;
    }

    public boolean isSource() {
        return resourceId.isEmpty();
    }

    public String title() {
        return title;
    }

    public String snippet() {
        return snippet;
    }

    public String location() {
        return location;
    }

    /** Eindeutig je Lese-Reiter: Quelle plus Ressource. */
    public String key() {
        return startPoint.isEmpty() ? sourceId + "|" + resourceId : sourceId + "|" + resourceId + "|" + startPoint;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReaderLink && ((ReaderLink) other).key().equals(key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    @Override
    public String toString() {
        return "ReaderLink{" + key() + ", '" + title + "'}";
    }
}
