package com.aresstack.enterpriseai.app.ui.chat;

/**
 * Eine Zeile des Chat-Verlaufs, wie die Oberfläche sie darstellt. Gehört dem {@link ChatShellModel}: nur das
 * Model ändert Text und Zustand, Ansichten lesen.
 */
public final class TranscriptEntry {

    /** Wer spricht. */
    public enum Author {
        USER,
        ASSISTANT
    }

    /** Lebenszyklus einer Nachricht. Nutzernachrichten sind sofort {@link #COMPLETE}. */
    public enum State {
        STREAMING,
        COMPLETE,
        CANCELLED,
        FAILED
    }

    private final long id;
    private final Author author;
    private final long createdAtMillis;
    private final StringBuilder text = new StringBuilder();
    private State state;
    private String failureMessage = "";

    TranscriptEntry(long id, Author author, long createdAtMillis, String text, State state) {
        this.id = id;
        this.author = author;
        this.createdAtMillis = createdAtMillis;
        this.text.append(text);
        this.state = state;
    }

    public long getId() {
        return id;
    }

    public Author getAuthor() {
        return author;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public String getText() {
        return text.toString();
    }

    public State getState() {
        return state;
    }

    /** Die für Menschen bestimmte Fehlermeldung bei {@link State#FAILED}, sonst leer. */
    public String getFailureMessage() {
        return failureMessage;
    }

    void append(String delta) {
        text.append(delta);
    }

    void finish(State finalState, String failure) {
        this.state = finalState;
        this.failureMessage = failure == null ? "" : failure;
    }

    @Override
    public String toString() {
        // Bewusst ohne Nachrichtentext: Verlaufsinhalte gehören nicht in Logs.
        return "TranscriptEntry{id=" + id + ", author=" + author + ", state=" + state
                + ", length=" + text.length() + '}';
    }
}
