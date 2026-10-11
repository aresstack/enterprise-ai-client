package com.aresstack.enterpriseai.astu.acropolis.history;

import java.util.ArrayList;
import java.util.List;

/** Eine gespeicherte Nachricht (aus askai-java8 arch): Rolle, Text, Zeitpunkt, Anhänge und Seiten. */
public final class ChatMessageRecord {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    /** Ein Hinweis der Anwendung (z. B. ausgefallene Wissenssuche): gespeichert, aber kein Modell-Turn. */
    public static final String ROLE_INFO = "info";

    private String role;
    private String text;
    private long createdAt;
    /** Abgebrochene Antwort: wird angezeigt, geht aber nicht in den Verlauf für das Modell (wie im ChatService). */
    private boolean cancelled;
    private List<AttachmentRecord> attachments = new ArrayList<AttachmentRecord>();
    /** Seiten, die mit dieser Nachricht in den Chat kamen (nur Bookmark und Titel). */
    private List<BookmarkRecord> bookmarks = new ArrayList<BookmarkRecord>();

    /** Für die Deserialisierung. */
    public ChatMessageRecord() {
    }

    public ChatMessageRecord(String role, String text, long createdAt, List<AttachmentRecord> attachments) {
        this.role = role;
        this.text = text;
        this.createdAt = createdAt;
        this.attachments = attachments != null ? new ArrayList<AttachmentRecord>(attachments)
                : new ArrayList<AttachmentRecord>();
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text != null ? text : "";
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Markiert eine abgebrochene Antwort (Teiltext). */
    public ChatMessageRecord markCancelled() {
        this.cancelled = true;
        return this;
    }

    public List<AttachmentRecord> getAttachments() {
        return attachments != null ? attachments : new ArrayList<AttachmentRecord>();
    }

    public List<BookmarkRecord> getBookmarks() {
        return bookmarks != null ? bookmarks : new ArrayList<BookmarkRecord>();
    }

    /** Hängt die Seiten dieser Nachricht an. */
    public ChatMessageRecord withBookmarks(List<BookmarkRecord> pages) {
        this.bookmarks = pages != null ? new ArrayList<BookmarkRecord>(pages) : new ArrayList<BookmarkRecord>();
        return this;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equals(role);
    }

    public boolean isInfo() {
        return ROLE_INFO.equals(role);
    }
}
