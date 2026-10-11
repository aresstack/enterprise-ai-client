package com.aresstack.enterpriseai.proasteion.application.agent;

/**
 * Eine übernommene Quelle des Research-Projekts, wie {@code sources.tsv} sie hält (corenth: Referenz statt
 * Textkopie): Ressourcen-ID, Titel, URL, Bookmark, Revision und Inhalts-Hash. Der Inhalt liegt im Wissensindex.
 * Unveränderlich.
 */
public final class ResearchSourceRef {

    private final String resourceId;
    private final String title;
    private final String url;
    private final String bookmark;
    private final String revision;
    private final String contentHash;

    public ResearchSourceRef(String resourceId, String title, String url, String bookmark, String revision,
                             String contentHash) {
        this.resourceId = nonNull(resourceId);
        this.title = nonNull(title);
        this.url = nonNull(url);
        this.bookmark = nonNull(bookmark);
        this.revision = nonNull(revision);
        this.contentHash = nonNull(contentHash);
    }

    /** Liest eine Zeile aus {@code sources.tsv} (fehlende Spalten bleiben leer). */
    static ResearchSourceRef parse(String line) {
        String[] parts = line.split("\t", -1);
        return new ResearchSourceRef(part(parts, 0), part(parts, 1), part(parts, 2), part(parts, 3), part(parts, 4),
                part(parts, 5));
    }

    private static String part(String[] parts, int index) {
        return parts.length > index ? parts[index] : "";
    }

    public String resourceId() {
        return resourceId;
    }

    /** Der Titel, sonst die Adresse, sonst die ID. */
    public String title() {
        return !title.isEmpty() ? title : !url.isEmpty() ? url : resourceId;
    }

    public String url() {
        return url;
    }

    public String bookmark() {
        return bookmark;
    }

    public String revision() {
        return revision;
    }

    public String contentHash() {
        return contentHash;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return resourceId + " " + title();
    }
}
