package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

/**
 * Eine zitierte Passage aus einer früheren Assistentenantwort dieses Chats, die der Nutzer für seine nächste Nachricht
 * vormerkt: welche Zeile des Verlaufs ({@link TranscriptEntry#getId()} und ihr Zeitpunkt, der auch nach dem Wiederöffnen
 * gilt), der markierte Text und, wenn bekannt, seine Position im Markdown der Antwort. Kein Dateianhang und keine
 * externe Wissensquelle; Anzeigewert ohne Port- oder Domänentypen.
 */
public final class ChatQuote {

    /** Keine Quellposition bekannt. */
    public static final int UNKNOWN_OFFSET = -1;

    private final long messageId;
    private final long messageCreatedAtMillis;
    private final String selectedText;
    private final int sourceStart;
    private final int sourceEnd;

    public ChatQuote(long messageId, long messageCreatedAtMillis, String selectedText) {
        this(messageId, messageCreatedAtMillis, selectedText, UNKNOWN_OFFSET, UNKNOWN_OFFSET);
    }

    /**
     * @param sourceStart Beginn im Markdown der Antwort (inklusive) oder {@link #UNKNOWN_OFFSET}
     * @param sourceEnd   Ende im Markdown der Antwort (exklusive) oder {@link #UNKNOWN_OFFSET}
     */
    public ChatQuote(long messageId, long messageCreatedAtMillis, String selectedText, int sourceStart,
                     int sourceEnd) {
        if (selectedText == null || selectedText.trim().isEmpty()) {
            throw new IllegalArgumentException("selectedText must not be blank");
        }
        boolean known = sourceStart >= 0 && sourceEnd > sourceStart;
        this.messageId = messageId;
        this.messageCreatedAtMillis = messageCreatedAtMillis;
        this.selectedText = selectedText;
        this.sourceStart = known ? sourceStart : UNKNOWN_OFFSET;
        this.sourceEnd = known ? sourceEnd : UNKNOWN_OFFSET;
    }

    /** Die zitierte Zeile des Verlaufs ({@link TranscriptEntry#getId()}). */
    public long messageId() {
        return messageId;
    }

    /** Zeitpunkt der zitierten Antwort (bleibt beim Speichern und Wiederöffnen gleich). */
    public long messageCreatedAtMillis() {
        return messageCreatedAtMillis;
    }

    /** Der markierte Text, genau wie angezeigt. */
    public String selectedText() {
        return selectedText;
    }

    public boolean hasSourceOffsets() {
        return sourceStart != UNKNOWN_OFFSET;
    }

    public int sourceStart() {
        return sourceStart;
    }

    public int sourceEnd() {
        return sourceEnd;
    }

    /** Dieselbe Passage, einer anderen Zeile zugeordnet (beim Wiederöffnen eines gespeicherten Chats). */
    public ChatQuote withMessageId(long id) {
        return new ChatQuote(id, messageCreatedAtMillis, selectedText, sourceStart, sourceEnd);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatQuote)) {
            return false;
        }
        ChatQuote quote = (ChatQuote) other;
        return messageId == quote.messageId && sourceStart == quote.sourceStart
                && selectedText.equals(quote.selectedText);
    }

    @Override
    public int hashCode() {
        return (Long.valueOf(messageId).hashCode() * 31 + sourceStart) * 31 + selectedText.hashCode();
    }

    @Override
    public String toString() {
        // Bewusst ohne Text: Verlaufsinhalte gehören nicht in Logs.
        return "ChatQuote[message=" + messageId + ", length=" + selectedText.length() + ", source=" + sourceStart
                + ".." + sourceEnd + "]";
    }
}
