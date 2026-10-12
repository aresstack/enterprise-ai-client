package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Was der Nutzer mit einer Nachricht abschickt, wie der Composer es gesammelt hat: Text (getrimmt, nicht leer), der
 * RAG-Schalter, neue Dateien, Seiten aus dem Lesebereich und ausdrückliche Verweise auf Ressourcen, die schon zum Chat
 * gehören, sowie zitierte Passagen früherer Antworten. Unveränderlich; weitere Bestandteile kommen als Feld mit eigener {@code with…}-Methode dazu, nicht als
 * neue Parameter von {@link ChatShellActions#sendRequested}. Die Anbindung entscheidet, wie die Bestandteile
 * fachlich übertragen werden; die sichtbare Nutzernachricht ist immer nur {@link #text()}.
 */
public final class ChatDraft {

    private final String text;
    private final boolean ragEnabled;
    private final List<Path> attachments;
    private final List<ChatPage> pages;
    private final List<ChatResource> references;
    private final List<ChatQuote> quotes;

    private ChatDraft(String text, boolean ragEnabled, List<Path> attachments, List<ChatPage> pages,
                      List<ChatResource> references, List<ChatQuote> quotes) {
        this.text = text;
        this.ragEnabled = ragEnabled;
        this.attachments = attachments;
        this.pages = pages;
        this.references = references;
        this.quotes = quotes;
    }

    /** Ein Entwurf nur aus Text. */
    public static ChatDraft of(String text, boolean ragEnabled) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return new ChatDraft(text.trim(), ragEnabled, Collections.<Path>emptyList(),
                Collections.<ChatPage>emptyList(), Collections.<ChatResource>emptyList(),
                Collections.<ChatQuote>emptyList());
    }

    /** Neue Dateien, die mit dieser Nachricht in den Chat kommen. */
    public ChatDraft withAttachments(List<Path> files) {
        return new ChatDraft(text, ragEnabled, copy(files), pages, references, quotes);
    }

    /** Seiten (Titel und Bookmark), die mit dieser Nachricht in den Chat kommen. */
    public ChatDraft withPages(List<ChatPage> values) {
        return new ChatDraft(text, ragEnabled, attachments, copy(values), references, quotes);
    }

    /** Ressourcen des Chats, auf die sich diese Nachricht ausdrücklich bezieht (je Ressource einmal). */
    public ChatDraft withReferences(List<ChatResource> values) {
        List<ChatResource> distinct = new ArrayList<ChatResource>();
        if (values != null) {
            for (ChatResource value : values) {
                if (value != null && !distinct.contains(value)) {
                    distinct.add(value);
                }
            }
        }
        return new ChatDraft(text, ragEnabled, attachments, pages, Collections.unmodifiableList(distinct), quotes);
    }

    /**
     * Passagen früherer Antworten, auf die sich diese Nachricht bezieht ({@link ChatQuote}); sie gehen nur für diesen
     * Turn als Kontext an das Modell, der sichtbare Text bleibt {@link #text()}.
     */
    public ChatDraft withQuotes(List<ChatQuote> values) {
        List<ChatQuote> distinct = new ArrayList<ChatQuote>();
        if (values != null) {
            for (ChatQuote value : values) {
                if (value != null && !distinct.contains(value)) {
                    distinct.add(value);
                }
            }
        }
        return new ChatDraft(text, ragEnabled, attachments, pages, references, Collections.unmodifiableList(distinct));
    }

    public String text() {
        return text;
    }

    public boolean ragEnabled() {
        return ragEnabled;
    }

    public List<Path> attachments() {
        return attachments;
    }

    public List<ChatPage> pages() {
        return pages;
    }

    public List<ChatResource> references() {
        return references;
    }

    public List<ChatQuote> quotes() {
        return quotes;
    }

    private static <T> List<T> copy(List<T> values) {
        List<T> copy = new ArrayList<T>();
        if (values != null) {
            for (T value : values) {
                if (value != null) {
                    copy.add(value);
                }
            }
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public String toString() {
        // Der Text gehört nicht in Logs.
        return "ChatDraft{length=" + text.length() + ", attachments=" + attachments.size() + ", pages=" + pages.size()
                + ", references=" + references.size() + ", quotes=" + quotes.size() + '}';
    }
}
