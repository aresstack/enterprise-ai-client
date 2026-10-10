package com.aresstack.enterpriseai.app.ui.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Presentation-Model der Chat-Shell: Verlauf, Streaming-Zustand und RAG-Schalter, ohne Swing.
 *
 * <p>Es bildet genau den Ablauf ab, den die Oberfläche zeigt: Eine Nutzernachricht wird angehängt, danach
 * höchstens eine Assistentenantwort gestreamt, die regulär endet, abgebrochen wird oder fehlschlägt. Während
 * des Streamings kann nicht gesendet, aber gestoppt werden. Ungültige Übergänge (Delta ohne laufende Antwort,
 * zweite Antwort parallel) sind Programmierfehler und werfen {@link IllegalStateException}.
 *
 * <p>Mit RAG (AP22) bekommt die laufende Antwort ihre {@link SourceReference Quellen} angehängt, sobald das
 * Retrieval abgeschlossen ist, also vor oder während des Streamings; Hinweise der Anwendung (ausgefallene
 * Wissenssuche, ausgefallener Suchpfad) sind eigene {@link TranscriptEntry.Author#NOTICE Hinweiszeilen}.
 *
 * <p>Nicht threadsicher: In der Anwendung nur auf dem Event Dispatch Thread verwenden; die Anbindung an den
 * Chat-Use-Case reicht dessen Callbacks dorthin weiter. Tests nutzen es direkt (headless).
 */
public final class ChatShellModel {

    private final LongSupplier clock;
    private final List<TranscriptEntry> entries = new ArrayList<TranscriptEntry>();
    private final List<ChatShellModelListener> listeners = new CopyOnWriteArrayList<ChatShellModelListener>();
    private long nextId = 1;
    private TranscriptEntry streamingEntry;
    private boolean ragEnabled;

    /** @param clock liefert Epoch-Millisekunden für die Zeitstempel der Bubbles (im Test fest). */
    public ChatShellModel(LongSupplier clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    public void addListener(ChatShellModelListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public void removeListener(ChatShellModelListener listener) {
        listeners.remove(listener);
    }

    /** Der Verlauf in Anzeigereihenfolge (unveränderliche Sicht). */
    public List<TranscriptEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isStreaming() {
        return streamingEntry != null;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean enabled) {
        if (ragEnabled == enabled) {
            return;
        }
        ragEnabled = enabled;
        fireStateChanged();
    }

    /** Ob ein Entwurf gesendet werden darf: nicht leer und keine Antwort läuft. */
    public boolean canSend(String draft) {
        return !isStreaming() && draft != null && draft.trim().length() > 0;
    }

    public boolean canStop() {
        return isStreaming();
    }

    public TranscriptEntry addUserMessage(String text) {
        return addUserMessage(text, Collections.<String>emptyList());
    }

    /** Eine Nutzernachricht mit den Dateinamen ihrer Anhänge (für die Anzeige in der Nutzerblase). */
    public TranscriptEntry addUserMessage(String text, List<String> attachmentNames) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        TranscriptEntry entry = newEntry(TranscriptEntry.Author.USER, text, TranscriptEntry.State.COMPLETE);
        if (attachmentNames != null && !attachmentNames.isEmpty()) {
            entry.setAttachments(attachmentNames);
        }
        fireEntryAdded(entry);
        return entry;
    }

    /** Startet eine (zunächst leere) Assistentenantwort, an die Deltas angehängt werden. */
    public TranscriptEntry beginAssistantMessage() {
        if (isStreaming()) {
            throw new IllegalStateException("an assistant message is already streaming");
        }
        streamingEntry = newEntry(TranscriptEntry.Author.ASSISTANT, "", TranscriptEntry.State.STREAMING);
        fireEntryAdded(streamingEntry);
        fireStateChanged();
        return streamingEntry;
    }

    public void appendAssistantDelta(String delta) {
        TranscriptEntry entry = requireStreaming();
        if (delta == null || delta.isEmpty()) {
            return; // Null-/Leer-Chunks sind im Streaming zulässig und ändern nichts.
        }
        entry.append(delta);
        fireEntryUpdated(entry);
    }

    public void completeAssistantMessage() {
        finishStreaming(TranscriptEntry.State.COMPLETE, null);
    }

    /** Die Antwort wurde abgebrochen; bereits empfangener Text bleibt sichtbar. */
    public void cancelAssistantMessage() {
        finishStreaming(TranscriptEntry.State.CANCELLED, null);
    }

    /**
     * Zeigt in der laufenden, noch leeren Antwort, was die Anwendung gerade tut (z. B. "Wissen wird gesucht …").
     * Verfällt mit dem ersten Delta und beim Abschluss; leer entfernt den Text.
     */
    public void setAssistantActivity(String activity) {
        TranscriptEntry entry = requireStreaming();
        if (!entry.getText().isEmpty()) {
            return; // Es wird schon geantwortet; der Text hat Vorrang.
        }
        entry.setActivity(activity);
        fireEntryUpdated(entry);
    }

    /**
     * Hängt einer Assistentenantwort ihre Quellen an (AP22), höchstens einmal je Antwort. Erlaubt in jedem
     * Zustand der Antwort, weil das Retrieval seine Quellen vor dem Streaming kennt, die Antwort eines schnellen
     * Backends aber schon abgeschlossen sein kann, wenn die Quellen auf dem UI-Thread ankommen. Eine leere Liste
     * ist zulässig und ändert nichts.
     *
     * @throws IllegalArgumentException wenn {@code entry} nicht zu diesem Model gehört oder keine Antwort ist
     * @throws IllegalStateException    wenn die Antwort schon Quellen hat
     */
    public void attachSources(TranscriptEntry entry, List<SourceReference> sources) {
        if (entry == null || sources == null) {
            throw new IllegalArgumentException("entry and sources must not be null");
        }
        if (!entries.contains(entry) || entry.getAuthor() != TranscriptEntry.Author.ASSISTANT) {
            throw new IllegalArgumentException("entry is not an assistant message of this model");
        }
        if (entry.hasSources()) {
            throw new IllegalStateException("the answer already has sources");
        }
        if (sources.isEmpty()) {
            return;
        }
        entry.setSources(sources);
        fireEntryUpdated(entry);
    }

    /**
     * Fügt einen Hinweis der Anwendung an (eigene Zeile, sofort vollständig), z. B. dass die Wissenssuche
     * ausgefallen ist. Darf auch während einer laufenden Antwort gerufen werden. {@code text} ist für Menschen
     * bestimmt und enthält keine Secrets.
     */
    public TranscriptEntry addNotice(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        TranscriptEntry entry = newEntry(TranscriptEntry.Author.NOTICE, text, TranscriptEntry.State.COMPLETE);
        fireEntryAdded(entry);
        return entry;
    }

    /**
     * Leert den Verlauf für einen neuen Chat. Nur möglich, wenn keine Antwort läuft (vorher Stop); die
     * Anbindung eröffnet daneben ihre neue Unterhaltung. Der RAG-Schalter bleibt, wie er ist.
     */
    public void clear() {
        if (streamingEntry != null) {
            throw new IllegalStateException("cannot clear while an assistant message is streaming");
        }
        if (entries.isEmpty()) {
            return;
        }
        entries.clear();
        for (ChatShellModelListener listener : listeners) {
            listener.entriesCleared();
        }
        fireStateChanged();
    }

    /**
     * Stellt eine gespeicherte Zeile wieder her (Chat-Historie): sofort vollständig, mit ihrem ursprünglichen
     * Zeitpunkt und, bei Nutzernachrichten, den Namen ihrer Anhänge. Nur, solange keine Antwort läuft.
     */
    public TranscriptEntry restoreEntry(TranscriptEntry.Author author, String text, long createdAtMillis,
                                        List<String> attachmentNames) {
        if (author == null || text == null) {
            throw new IllegalArgumentException("author and text must not be null");
        }
        if (streamingEntry != null) {
            throw new IllegalStateException("cannot restore while an assistant message is streaming");
        }
        TranscriptEntry entry = new TranscriptEntry(nextId++, author, createdAtMillis, text,
                TranscriptEntry.State.COMPLETE);
        entries.add(entry);
        if (attachmentNames != null && !attachmentNames.isEmpty()) {
            entry.setAttachments(attachmentNames);
        }
        fireEntryAdded(entry);
        return entry;
    }

    /** Die Antwort ist fehlgeschlagen. {@code message} ist für Menschen bestimmt und enthält keine Secrets. */
    public void failAssistantMessage(String message) {
        finishStreaming(TranscriptEntry.State.FAILED, message == null || message.trim().isEmpty()
                ? "Die Antwort konnte nicht erzeugt werden." : message);
    }

    private void finishStreaming(TranscriptEntry.State state, String failure) {
        TranscriptEntry entry = requireStreaming();
        streamingEntry = null;
        entry.finish(state, failure);
        fireEntryUpdated(entry);
        fireStateChanged();
    }

    private TranscriptEntry requireStreaming() {
        if (streamingEntry == null) {
            throw new IllegalStateException("no assistant message is streaming");
        }
        return streamingEntry;
    }

    private TranscriptEntry newEntry(TranscriptEntry.Author author, String text, TranscriptEntry.State state) {
        TranscriptEntry entry = new TranscriptEntry(nextId++, author, clock.getAsLong(), text, state);
        entries.add(entry);
        return entry;
    }

    private void fireEntryAdded(TranscriptEntry entry) {
        for (ChatShellModelListener listener : listeners) {
            listener.entryAdded(entry);
        }
    }

    private void fireEntryUpdated(TranscriptEntry entry) {
        for (ChatShellModelListener listener : listeners) {
            listener.entryUpdated(entry);
        }
    }

    private void fireStateChanged() {
        for (ChatShellModelListener listener : listeners) {
            listener.stateChanged();
        }
    }
}
