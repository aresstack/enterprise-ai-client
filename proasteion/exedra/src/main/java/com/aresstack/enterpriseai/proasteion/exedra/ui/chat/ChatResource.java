package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

/**
 * Eine Ressource, die schon zum Chat gehört, wie die Ressourcenübersicht im Composer sie zeigt und ein Verweis-Chip
 * sie trägt: Art, stabile Kennung (Attachment-ID, Bookmark oder Resource-ID als Text), Titel und ein Hinweis für den
 * Tooltip. Anzeigewert ohne Port- oder Domänentypen; befüllt wird er von der Anbindung. Gleich sind zwei Ressourcen
 * mit gleicher Art und Kennung, nie über den Titel.
 */
public final class ChatResource {

    /** Gruppe in der Übersicht. */
    public enum Kind {
        /** Eine Datei des Chats (liegt schon in seiner Ablage). */
        FILE,
        /** Eine hinzugefügte Seite (Bookmark). */
        PAGE,
        /** Eine von der Wissenssuche verwendete Quelle (Resource-ID). */
        SOURCE
    }

    private final Kind kind;
    private final String id;
    private final String title;
    private final String detail;

    /**
     * @param kind   Art
     * @param id     stabile Kennung (nicht leer)
     * @param title  Titel; leer: die Kennung
     * @param detail Zusatz für den Tooltip (Ort, Größe …) oder leer
     */
    public ChatResource(Kind kind, String id, String title, String detail) {
        if (kind == null || id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("kind and id must not be blank");
        }
        this.kind = kind;
        this.id = id.trim();
        this.title = title == null || title.trim().isEmpty() ? this.id : title.trim();
        this.detail = detail == null ? "" : detail.trim();
    }

    public Kind kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatResource)) {
            return false;
        }
        ChatResource that = (ChatResource) other;
        return kind == that.kind && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + id.hashCode();
    }

    @Override
    public String toString() {
        // Titel gehören nicht in Logs.
        return "ChatResource[" + kind + "]";
    }
}
