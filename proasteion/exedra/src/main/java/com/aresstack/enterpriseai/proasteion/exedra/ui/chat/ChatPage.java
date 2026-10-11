package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

/**
 * Eine Seite aus dem Lesebereich, die der Nutzer dem Chat hinzufügt: Titel und Bookmark als Text (z. B.
 * {@code confluence://cf.intern/confluence/page/123}). Kein Seitentext; Anzeigewert ohne Port- oder Domänentypen.
 */
public final class ChatPage {

    private final String title;
    private final String bookmark;

    public ChatPage(String title, String bookmark) {
        if (bookmark == null || bookmark.trim().isEmpty()) {
            throw new IllegalArgumentException("bookmark must not be blank");
        }
        this.bookmark = bookmark.trim();
        this.title = title == null || title.trim().isEmpty() ? this.bookmark : title.trim();
    }

    public String title() {
        return title;
    }

    public String bookmark() {
        return bookmark;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChatPage && bookmark.equals(((ChatPage) other).bookmark);
    }

    @Override
    public int hashCode() {
        return bookmark.hashCode();
    }

    @Override
    public String toString() {
        return "ChatPage[" + bookmark + "]";
    }
}
