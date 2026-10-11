package com.aresstack.enterpriseai.astu.acropolis.chat;

import java.util.List;

/**
 * Eine Seite, die der Nutzer einem Chat hinzugefügt hat (corenth: Bookmark statt Textkopie): nur Titel und
 * Bookmark der kanonischen Ressource ({@code BookmarkUri} als Text, vom Aufrufer geprüft), nie ihr Text. Das
 * Modell erfährt beides über {@link #context}; den Inhalt liest es bei Bedarf mit {@code knowledge_read}, das das Bookmark über den Index auflöst.
 */
public final class ChatBookmark {

    /** Das Werkzeug, mit dem das Modell eine hinzugefügte Seite liest. */
    public static final String READ_TOOL = "knowledge_read";

    private final String title;
    private final String uri;

    public ChatBookmark(String title, String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("uri must not be blank");
        }
        this.uri = uri.trim();
        this.title = title == null || title.trim().isEmpty() ? this.uri : title.trim();
    }

    public String title() {
        return title;
    }

    public String uri() {
        return uri;
    }

    /** Hinweis für das Modell auf die Seiten des Chats (Titel und Bookmark) oder {@code null} ohne Seiten. */
    public static String context(List<ChatBookmark> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder("Der Nutzer hat diesem Chat Seiten hinzugefügt. Ihr Inhalt steht "
                + "nicht im Verlauf; lies eine Seite bei Bedarf mit dem Werkzeug " + READ_TOOL
                + " (id = Bookmark):");
        for (ChatBookmark bookmark : bookmarks) {
            context.append("\n- ").append(bookmark.title()).append(": ").append(bookmark.uri());
        }
        return context.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChatBookmark && uri.equals(((ChatBookmark) other).uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    @Override
    public String toString() {
        return "ChatBookmark[" + uri + "]";
    }
}
