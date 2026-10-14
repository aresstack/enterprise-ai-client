package com.aresstack.enterpriseai.astu.acropolis.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Ressourcen einer Unterhaltung als fachliche Projektion eines {@link ChatRecord} (nicht zusätzlich gespeichert):
 * alle Dateien, alle hinzugefügten Seiten und alle von RAG tatsächlich verwendeten Quellen, je Gruppe in der
 * Reihenfolge ihres ersten Auftretens und je stabiler Kennung nur einmal (Datei: Attachment-ID, Seite: Bookmark,
 * Quelle: Wissensquelle und Resource-ID). Was keine stabile Kennung hat (eine noch nicht abgelegte Datei, eine Quelle aus einem
 * älteren Chat), fehlt: eine Kennung aus Titel oder Ort wird nicht erfunden.
 */
public final class ChatResourceCatalog {

    private final List<AttachmentRecord> attachments;
    private final List<BookmarkRecord> pages;
    private final List<SourceRecord> sources;

    private ChatResourceCatalog(List<AttachmentRecord> attachments, List<BookmarkRecord> pages,
                                List<SourceRecord> sources) {
        this.attachments = Collections.unmodifiableList(attachments);
        this.pages = Collections.unmodifiableList(pages);
        this.sources = Collections.unmodifiableList(sources);
    }

    /** Die Ressourcen des Chats; ein {@code null}-Chat hat keine. */
    public static ChatResourceCatalog of(ChatRecord record) {
        Map<String, AttachmentRecord> files = new LinkedHashMap<String, AttachmentRecord>();
        Map<String, BookmarkRecord> bookmarks = new LinkedHashMap<String, BookmarkRecord>();
        Map<String, SourceRecord> used = new LinkedHashMap<String, SourceRecord>();
        if (record != null) {
            for (ChatMessageRecord message : record.getMessages()) {
                for (AttachmentRecord attachment : message.getAttachments()) {
                    String id = attachment.getId();
                    if (id != null && !id.trim().isEmpty() && !files.containsKey(id)) {
                        files.put(id, attachment);
                    }
                }
                for (BookmarkRecord bookmark : message.getBookmarks()) {
                    String uri = bookmark.getUri().trim();
                    if (!uri.isEmpty() && !bookmarks.containsKey(uri)) {
                        bookmarks.put(uri, bookmark);
                    }
                }
                for (SourceRecord source : message.getSources()) {
                    String id = source.getResourceId();
                    // Key by source and resource: the same resource id in two sources names two resources.
                    String key = source.getSourceId() + '\u0000' + id;
                    if (!id.isEmpty() && !used.containsKey(key)) {
                        used.put(key, source);
                    }
                }
            }
        }
        return new ChatResourceCatalog(new ArrayList<AttachmentRecord>(files.values()),
                new ArrayList<BookmarkRecord>(bookmarks.values()), new ArrayList<SourceRecord>(used.values()));
    }

    /** Die abgelegten Dateien (je Attachment-ID einmal). */
    public List<AttachmentRecord> attachments() {
        return attachments;
    }

    /** Die hinzugefügten Seiten (je Bookmark einmal). */
    public List<BookmarkRecord> pages() {
        return pages;
    }

    /** Die verwendeten Quellen (je Wissensquelle und Resource-ID einmal, mit den Angaben ihres ersten Auftretens). */
    public List<SourceRecord> sources() {
        return sources;
    }

    /** Anzahl unterschiedlicher Ressourcen. */
    public int size() {
        return attachments.size() + pages.size() + sources.size();
    }
}
