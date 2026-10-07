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
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        TranscriptEntry entry = newEntry(TranscriptEntry.Author.USER, text, TranscriptEntry.State.COMPLETE);
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
