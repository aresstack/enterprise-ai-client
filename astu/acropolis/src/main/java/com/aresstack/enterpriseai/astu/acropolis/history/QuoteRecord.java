package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Gespeichertes Zitat einer Nutzernachricht: die markierte Passage einer früheren Antwort desselben Chats, der
 * Zeitpunkt dieser Antwort (über ihn findet ein wieder geöffneter Chat sie) und, wenn bekannt, die Position der
 * Passage in ihrem Markdown ({@code -1} = unbekannt). Fachlich weder Anhang noch Seite.
 */
public final class QuoteRecord {

    private String text;
    private long sourceCreatedAt;
    private int sourceStart = -1;
    private int sourceEnd = -1;

    /** Für die Deserialisierung. */
    public QuoteRecord() {
    }

    public QuoteRecord(String text, long sourceCreatedAt, int sourceStart, int sourceEnd) {
        this.text = text;
        this.sourceCreatedAt = sourceCreatedAt;
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
    }

    public String getText() {
        return text != null ? text : "";
    }

    /** Zeitpunkt der zitierten Antwort ({@link ChatMessageRecord#getCreatedAt()}). */
    public long getSourceCreatedAt() {
        return sourceCreatedAt;
    }

    public int getSourceStart() {
        return sourceStart;
    }

    public int getSourceEnd() {
        return sourceEnd;
    }
}
