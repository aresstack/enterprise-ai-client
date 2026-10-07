package com.aresstack.enterpriseai.app.ui.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eine Zeile des Chat-Verlaufs, wie die Oberfläche sie darstellt. Gehört dem {@link ChatShellModel}: nur das
 * Model ändert Text, Zustand und Quellen, Ansichten lesen.
 */
public final class TranscriptEntry {

    /** Wer spricht. */
    public enum Author {
        USER,
        ASSISTANT,
        /** Ein Hinweis der Anwendung (z. B. ausgefallene Wissenssuche), weder Nutzer noch Modell. */
        NOTICE
    }

    /** Lebenszyklus einer Nachricht. Nutzernachrichten und Hinweise sind sofort {@link #COMPLETE}. */
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
    private String activity = "";
    private List<SourceReference> sources = Collections.emptyList();

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

    /**
     * Was die Anwendung gerade für diese Antwort tut, solange noch kein Text da ist (z. B. "Wissen wird
     * gesucht …"); leer, sobald das erste Delta kommt oder die Antwort endet.
     */
    public String getActivity() {
        return activity;
    }

    /**
     * Die Quellen, auf die sich eine Assistentenantwort stützt (AP22), in Nummernreihenfolge; leer ohne RAG,
     * ohne Treffer oder für Nutzer- und Hinweiszeilen. Unveränderliche Sicht.
     */
    public List<SourceReference> getSources() {
        return sources;
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }

    void append(String delta) {
        text.append(delta);
        activity = "";
    }

    void finish(State finalState, String failure) {
        this.state = finalState;
        this.failureMessage = failure == null ? "" : failure;
        this.activity = "";
    }

    void setActivity(String newActivity) {
        this.activity = newActivity == null ? "" : newActivity.trim();
    }

    void setSources(List<SourceReference> newSources) {
        this.sources = Collections.unmodifiableList(new ArrayList<SourceReference>(newSources));
    }

    @Override
    public String toString() {
        // Bewusst ohne Nachrichtentext: Verlaufsinhalte gehören nicht in Logs.
        return "TranscriptEntry{id=" + id + ", author=" + author + ", state=" + state
                + ", length=" + text.length() + ", sources=" + sources.size() + '}';
    }
}
