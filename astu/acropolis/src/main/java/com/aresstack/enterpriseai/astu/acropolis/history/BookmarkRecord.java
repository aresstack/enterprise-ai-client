package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Gespeicherte Seite einer Nutzernachricht: Bookmark der Ressource und Titel, nie ihr Text
 * ({@link com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark}).
 */
public final class BookmarkRecord {

    private String uri;
    private String title;

    /** Für die Deserialisierung. */
    public BookmarkRecord() {
    }

    public BookmarkRecord(String uri, String title) {
        this.uri = uri;
        this.title = title;
    }

    public String getUri() {
        return uri != null ? uri : "";
    }

    public String getTitle() {
        return title != null ? title : "";
    }
}
