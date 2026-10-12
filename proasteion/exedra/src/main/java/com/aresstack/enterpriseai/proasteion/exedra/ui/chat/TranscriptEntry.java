package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

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
        NOTICE,
        /**
         * Ein Arbeitsschritt des Agenten (askai arch): Werkzeugaufruf (gelbe Gedankenwolke) oder Denkschritt des
         * Modells (grüne Wolke). Der Text ist die Eingabe bzw. Erläuterung; läuft, solange {@link State#STREAMING}.
         */
        ACTIVITY
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
    private List<String> attachments = Collections.emptyList();
    private List<ChatPage> pages = Collections.emptyList();
    private List<ChatResource> references = Collections.emptyList();
    private List<ChatQuote> quotes = Collections.emptyList();
    private String activityKey = "";
    private String activityTitle = "";
    private String activityKind = "";
    private String activitySummary = "";
    /** Die laufende Antwort steht hinter einer Wolke ihres Turns (nur {@link Author#ASSISTANT}). */
    private boolean behindActivity;

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
     * Eine noch leere, laufende Antwort, vor der in ihrem Turn schon eine Wolke steht: Die Oberfläche zeigt sie erst
     * mit dem ersten Antworttext (die Wolken zeigen, woran gearbeitet wird; keine Platzhalterblase darunter).
     */
    public boolean isPendingBehindActivity() {
        return behindActivity && author == Author.ASSISTANT && state == State.STREAMING && text.length() == 0;
    }

    void setBehindActivity(boolean value) {
        this.behindActivity = value;
    }

    /**
     * Die Quellen, auf die sich eine Assistentenantwort stützt (AP22), in Nummernreihenfolge; leer ohne RAG,
     * ohne Treffer oder für Nutzer- und Hinweiszeilen. Unveränderliche Sicht.
     */
    public List<SourceReference> getSources() {
        return sources;
    }

    /** Dateinamen der Anhänge einer Nutzernachricht; leer, wenn sie keine hat. */
    public List<String> getAttachments() {
        return attachments;
    }

    void setAttachments(List<String> names) {
        this.attachments = Collections.unmodifiableList(new java.util.ArrayList<String>(names));
    }

    /** Seiten (Bookmarks), die mit einer Nutzernachricht in den Chat kamen; leer, wenn sie keine hat. */
    public List<ChatPage> getPages() {
        return pages;
    }

    void setPages(List<ChatPage> values) {
        this.pages = Collections.unmodifiableList(new java.util.ArrayList<ChatPage>(values));
    }

    /**
     * Ressourcen des Chats, auf die sich eine Nutzernachricht ausdrücklich bezog (Verweis-Chips); leer, wenn keine.
     * Die Ressourcen selbst gehören dem Chat, nicht dieser Nachricht.
     */
    public List<ChatResource> getReferences() {
        return references;
    }

    void setReferences(List<ChatResource> values) {
        this.references = Collections.unmodifiableList(new java.util.ArrayList<ChatResource>(values));
    }

    /** Passagen früherer Antworten, auf die sich eine Nutzernachricht bezieht; leer, wenn sie keine zitiert. */
    public List<ChatQuote> getQuotes() {
        return quotes;
    }

    void setQuotes(List<ChatQuote> values) {
        this.quotes = Collections.unmodifiableList(new java.util.ArrayList<ChatQuote>(values));
    }

    /** Kennung des Werkzeugaufrufs (nur {@link Author#ACTIVITY}). */
    public String getActivityKey() {
        return activityKey;
    }

    /** Überschrift der Wolke, z. B. „Suche in den Wissensquellen“ (nur {@link Author#ACTIVITY}). */
    public String getActivityTitle() {
        return activityTitle;
    }

    /** ACP-Art des Aufrufs ({@code search}, {@code read}, {@code think} …; nur {@link Author#ACTIVITY}). */
    public String getActivityKind() {
        return activityKind;
    }

    /** Ein Denkschritt (grüne Wolke) statt eines Werkzeugs (gelbe Wolke). */
    public boolean isThinking() {
        return "think".equals(activityKind);
    }

    /** Die Ergebniszeile eines beendeten Aufrufs, sonst leer (nur {@link Author#ACTIVITY}). */
    public String getActivitySummary() {
        return activitySummary;
    }

    void setActivityHead(String key, String title, String kind) {
        this.activityKey = key == null ? "" : key;
        this.activityTitle = title == null ? "" : title;
        this.activityKind = kind == null ? "" : kind;
    }

    void finishActivity(State finalState, String summary) {
        this.state = finalState;
        this.activitySummary = summary == null ? "" : summary.trim();
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }

    /** Neuer Inhalt eines laufenden Arbeitsschritts (die Recherche-Karte wird in place aktualisiert). */
    void replaceText(String newText) {
        text.setLength(0);
        text.append(newText == null ? "" : newText);
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
