package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

/**
 * Ein Verweis auf etwas Lesbares in einer Wissensquelle, wie Trefferliste, Quellenübersicht und Favoriten ihn zeigen:
 * Quelle, Ressource (die Adresse mit Präfix, z. B. {@code wiki:intranet/Handbuch}; leer = die Quelle selbst), Titel,
 * Auszug und Fundstelle. Reine Anzeigedaten ohne Port- oder Adaptertypen; unveränderlich.
 */
public final class ReaderLink {

    private final String sourceId;
    private final String resourceId;
    private final String title;
    private final String snippet;
    private final String location;

    public ReaderLink(String sourceId, String resourceId, String title, String snippet, String location) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        this.sourceId = sourceId.trim();
        this.resourceId = resourceId == null ? "" : resourceId.trim();
        this.title = title == null || title.trim().isEmpty() ? (this.resourceId.isEmpty() ? this.sourceId
                : this.resourceId) : title.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
        this.location = location == null || location.trim().isEmpty() ? this.resourceId : location.trim();
    }

    /** Die Quelle selbst (Übersicht ihrer Ressourcen). */
    public static ReaderLink source(String sourceId, String description) {
        return new ReaderLink(sourceId, "", sourceId, "", description);
    }

    public String sourceId() {
        return sourceId;
    }

    /** Die Ressourcen-ID mit Präfix; leer, wenn der Verweis die Quelle selbst meint. */
    public String resourceId() {
        return resourceId;
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
        return sourceId + "|" + resourceId;
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
