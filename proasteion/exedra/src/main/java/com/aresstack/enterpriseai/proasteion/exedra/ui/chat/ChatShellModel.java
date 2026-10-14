package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    /** Die Arbeitsschritte des laufenden Agent-Auftrags nach Kennung (leer außerhalb eines Auftrags). */
    private final java.util.Map<String, TranscriptEntry> activities =
            new java.util.LinkedHashMap<String, TranscriptEntry>();
    private boolean ragEnabled;
    /** Die laufende Antwort ist ein weiterer Abschnitt desselben Auftrags (nach {@link #beginAnswerSection}). */
    private boolean continuedSection;
    /**
     * Antwortabschnitte, die nur entstanden, weil eine Wolke mitten in die Antwort kam; mit dem Ende des Auftrags
     * verschwinden die Wolken, und diese Abschnitte gehören wieder zur Antwort davor (eine Antwortblase je Turn).
     */
    private final List<TranscriptEntry> splitSections = new ArrayList<TranscriptEntry>();

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
        return addUserMessage(text, attachmentNames, Collections.<ChatPage>emptyList());
    }

    /** Wie oben, dazu die Seiten (Bookmarks), die mit der Nachricht in den Chat kommen. */
    public TranscriptEntry addUserMessage(String text, List<String> attachmentNames, List<ChatPage> pages) {
        return addUserMessage(text, attachmentNames, pages, Collections.<ChatResource>emptyList());
    }

    /** Wie oben, dazu die Ressourcen des Chats, auf die sich die Nachricht ausdrücklich bezieht (Verweis-Chips). */
    public TranscriptEntry addUserMessage(String text, List<String> attachmentNames, List<ChatPage> pages,
                                          List<ChatResource> references) {
        return addUserMessage(text, attachmentNames, pages, references, Collections.<ChatQuote>emptyList());
    }

    /** Wie oben, dazu die zitierten Passagen früherer Antworten, auf die sich die Nachricht bezieht. */
    public TranscriptEntry addUserMessage(String text, List<String> attachmentNames, List<ChatPage> pages,
                                          List<ChatResource> references, List<ChatQuote> quotes) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        TranscriptEntry entry = newEntry(TranscriptEntry.Author.USER, text, TranscriptEntry.State.COMPLETE);
        if (attachmentNames != null && !attachmentNames.isEmpty()) {
            entry.setAttachments(attachmentNames);
        }
        if (pages != null && !pages.isEmpty()) {
            entry.setPages(pages);
        }
        if (references != null && !references.isEmpty()) {
            entry.setReferences(references);
        }
        if (quotes != null && !quotes.isEmpty()) {
            entry.setQuotes(quotes);
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
        continuedSection = false;
        splitSections.clear();
        fireEntryAdded(streamingEntry);
        fireStateChanged();
        return streamingEntry;
    }

    public void appendAssistantDelta(String delta) {
        TranscriptEntry entry = requireStreaming();
        if (delta == null || delta.isEmpty()) {
            return; // Null-/Leer-Chunks sind im Streaming zulässig und ändern nichts.
        }
        if (entry.getText().isEmpty() && (!activities.isEmpty() || continuedSection)) {
            // Ein neuer Abschnitt hinter einer Tool-Wolke beginnt nicht mit den Absatz-Leerzeilen des Agenten.
            delta = delta.replaceFirst("^[\\r\\n]+", "");
            if (delta.isEmpty()) {
                return;
            }
        }
        entry.append(delta);
        fireEntryUpdated(entry);
    }

    /**
     * Eine neue Äußerung des Agenten beginnt (askai arch: eine Blase je Äußerung): eine angefangene Antwort endet als
     * eigener Abschnitt, der weitere Text kommt in eine neue Antwortblase. Eine noch leere Antwort bleibt. Leerzeilen
     * am Anfang des neuen Abschnitts entfallen.
     */
    public void beginAnswerSection() {
        TranscriptEntry entry = requireStreaming();
        if (entry.getText().isEmpty()) {
            return;
        }
        entry.finish(TranscriptEntry.State.COMPLETE, null);
        fireEntryUpdated(entry);
        streamingEntry = newEntry(TranscriptEntry.Author.ASSISTANT, "", TranscriptEntry.State.STREAMING);
        continuedSection = true;
        fireEntryAdded(streamingEntry);
    }

    public void completeAssistantMessage() {
        finishStreaming(TranscriptEntry.State.COMPLETE, null);
    }

    /** Die Antwort wurde abgebrochen; bereits empfangener Text bleibt sichtbar. */
    public void cancelAssistantMessage() {
        finishStreaming(TranscriptEntry.State.CANCELLED, null);
    }

    /**
     * Ein Arbeitsschritt des Agenten beginnt (askai arch: Tool-Wolke bzw. Denkwolke). Die Wolke steht im Verlauf an
     * der Stelle, an der der Agent gerade ist: eine noch leere Antwort rückt hinter sie, eine angefangene endet als
     * eigener Abschnitt und der weitere Text kommt in eine neue Antwortblase hinter der Wolke. Ein zweiter Start mit
     * derselben Kennung ersetzt Titel und Inhalt (die Recherche-Karte zählt so in place mit).
     */
    public TranscriptEntry startActivity(String key, String title, String kind, String detail) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        TranscriptEntry known = activities.get(key);
        if (known != null) {
            known.setActivityHead(key, title == null || title.isEmpty() ? known.getActivityTitle() : title,
                    kind == null || kind.isEmpty() ? known.getActivityKind() : kind);
            if (detail != null && !detail.isEmpty()) {
                known.replaceText(detail);
            }
            fireEntryUpdated(known);
            return known;
        }
        TranscriptEntry answer = streamingEntry;
        boolean reuseAnswer = false;
        if (answer != null) {
            if (answer.getText().isEmpty()) {
                entries.remove(answer);
                fireEntryRemoved(answer);
                reuseAnswer = true;
            } else {
                answer.finish(TranscriptEntry.State.COMPLETE, null);
                fireEntryUpdated(answer);
            }
        }
        TranscriptEntry activity = newEntry(TranscriptEntry.Author.ACTIVITY, detail == null ? "" : detail,
                TranscriptEntry.State.STREAMING);
        activity.setActivityHead(key, title, kind);
        activities.put(key, activity);
        fireEntryAdded(activity);
        if (answer != null) {
            if (reuseAnswer) {
                answer.setBehindActivity(true);
                entries.add(answer);
                fireEntryAdded(answer);
            } else {
                streamingEntry = newEntry(TranscriptEntry.Author.ASSISTANT, "", TranscriptEntry.State.STREAMING);
                streamingEntry.setBehindActivity(true);
                splitSections.add(streamingEntry);
                fireEntryAdded(streamingEntry);
            }
        }
        return activity;
    }

    /** Hängt Text an die Erläuterung eines laufenden Schritts (gestreamte Überlegungen in der Denkwolke). */
    public void appendActivityText(String key, String delta) {
        TranscriptEntry activity = activities.get(key);
        if (activity == null || delta == null || delta.isEmpty()
                || activity.getState() != TranscriptEntry.State.STREAMING) {
            return;
        }
        activity.append(delta);
        fireEntryUpdated(activity);
    }

    /**
     * Ein Arbeitsschritt ist beendet; die Wolke platzt und zeigt ihre Ergebniszeile, bis der Auftrag endet. Dann
     * verschwinden die Wolken aus dem Verlauf (askai arch: transiente Gedanken- und Werkzeugwolken). Unbekannte
     * Kennung: nichts.
     */
    public void finishActivity(String key, boolean succeeded, String summary) {
        TranscriptEntry activity = activities.get(key);
        if (activity == null || activity.getState() != TranscriptEntry.State.STREAMING) {
            return;
        }
        activity.finishActivity(succeeded ? TranscriptEntry.State.COMPLETE : TranscriptEntry.State.FAILED, summary);
        fireEntryUpdated(activity);
    }

    /** Ob der laufende Auftrag einen Arbeitsschritt mit dieser Kennung kennt (laufend oder beendet). */
    public boolean hasActivity(String key) {
        return activities.containsKey(key);
    }

    /** Ob ein Arbeitsschritt mit dieser Kennung noch läuft. */
    public boolean isActivityRunning(String key) {
        TranscriptEntry activity = activities.get(key);
        return activity != null && activity.getState() == TranscriptEntry.State.STREAMING;
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
     * Add sources to an answer that may already have some (issue #118): resources the model read through a tool
     * after the RAG sources were attached. A passage (with chunk id) is new unless the same passage is present; a
     * whole resource (without chunk id) is new unless any passage of it or the resource itself is present (same
     * knowledge source and resource id, or same location without ids). A new source keeps its number unless that
     * number is taken, then it gets the next free one. Unlike {@link #attachSources} this may be called repeatedly, also after the answer completed.
     *
     * @return whether the answer changed
     */
    public boolean mergeSources(TranscriptEntry entry, List<SourceReference> sources) {
        if (entry == null || sources == null) {
            throw new IllegalArgumentException("entry and sources must not be null");
        }
        if (!entries.contains(entry) || entry.getAuthor() != TranscriptEntry.Author.ASSISTANT) {
            throw new IllegalArgumentException("entry is not an assistant message of this model");
        }
        List<SourceReference> merged = new ArrayList<SourceReference>(entry.getSources());
        Set<String> keys = new HashSet<String>();
        Set<Integer> numbers = new HashSet<Integer>();
        int highest = 0;
        for (SourceReference source : merged) {
            keys.add(identity(source));
            keys.add(passage(source));
            numbers.add(source.getNumber());
            highest = Math.max(highest, source.getNumber());
        }
        boolean changed = false;
        for (SourceReference source : sources) {
            if (source == null) {
                continue;
            }
            String key = source.getChunkId().isEmpty() ? identity(source) : passage(source);
            if (keys.contains(key)) {
                continue;
            }
            keys.add(identity(source));
            keys.add(passage(source));
            SourceReference added = source;
            if (!numbers.add(source.getNumber())) {
                added = renumbered(source, highest + 1);
                numbers.add(added.getNumber());
            }
            highest = Math.max(highest, added.getNumber());
            merged.add(added);
            changed = true;
        }
        if (changed) {
            entry.setSources(merged);
            fireEntryUpdated(entry);
        }
        return changed;
    }

    private static String identity(SourceReference source) {
        return source.getResourceId().isEmpty()
                ? "location\u0000" + source.getLocation()
                : "resource\u0000" + source.getSourceId() + '\u0000' + source.getResourceId();
    }

    private static String passage(SourceReference source) {
        return identity(source) + "\u0000chunk\u0000" + source.getChunkId();
    }

    private static SourceReference renumbered(SourceReference source, int number) {
        return new SourceReference(number, source.getTitle(), source.getHeading(), source.getLocation(),
                source.getRevision(), source.getScore(), source.getKeywordRank(), source.getSemanticRank(),
                source.getSourceId(), source.getResourceId(), source.getChunkId());
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
        return restoreEntry(author, text, createdAtMillis, attachmentNames, Collections.<ChatPage>emptyList());
    }

    /** Wie oben, dazu die Seiten einer Nutzernachricht. */
    public TranscriptEntry restoreEntry(TranscriptEntry.Author author, String text, long createdAtMillis,
                                        List<String> attachmentNames, List<ChatPage> pages) {
        return restoreEntry(author, text, createdAtMillis, attachmentNames, pages,
                Collections.<ChatResource>emptyList());
    }

    /** Wie oben, dazu die ausdrücklichen Ressourcenverweise einer Nutzernachricht. */
    public TranscriptEntry restoreEntry(TranscriptEntry.Author author, String text, long createdAtMillis,
                                        List<String> attachmentNames, List<ChatPage> pages,
                                        List<ChatResource> references) {
        return restoreEntry(author, text, createdAtMillis, attachmentNames, pages, references,
                Collections.<ChatQuote>emptyList());
    }

    /** Wie oben, dazu die zitierten Passagen einer Nutzernachricht. */
    public TranscriptEntry restoreEntry(TranscriptEntry.Author author, String text, long createdAtMillis,
                                        List<String> attachmentNames, List<ChatPage> pages,
                                        List<ChatResource> references, List<ChatQuote> quotes) {
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
        if (pages != null && !pages.isEmpty()) {
            entry.setPages(pages);
        }
        if (references != null && !references.isEmpty()) {
            entry.setReferences(references);
        }
        if (quotes != null && !quotes.isEmpty()) {
            entry.setQuotes(quotes);
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
        boolean hadActivities = !activities.isEmpty();
        // Wolken sind vergänglich (askai arch): nach einer Antwort verlassen sie das Modell. Endet der Auftrag mit einem
        // Fehler oder Abbruch, bleiben alle Schritte des Turns mit ihrem tatsächlichen Endzustand stehen: ein später
        // gescheiterter Modellaufruf löscht die vorher erfolgreich gelaufenen Suchen nicht aus dem Verlauf. Nur ein
        // Schritt, der beim Ende noch lief (etwa die Recherche-Karte vor dem Stop), hat kein Ergebnis und entfällt.
        boolean answered = state == TranscriptEntry.State.COMPLETE;
        for (TranscriptEntry activity : activities.values()) {
            boolean running = activity.getState() == TranscriptEntry.State.STREAMING;
            if (running) {
                activity.finishActivity(TranscriptEntry.State.CANCELLED, "");
            }
            if (!answered && !running) {
                fireEntryUpdated(activity);
            } else {
                entries.remove(activity);
                fireEntryRemoved(activity);
            }
        }
        activities.clear();
        boolean earlierSection = continuedSection || splitSections.contains(entry);
        if (hadActivities && entry.getText().isEmpty()
                && (state == TranscriptEntry.State.COMPLETE
                || state == TranscriptEntry.State.CANCELLED && earlierSection)) {
            // Der leere Platzhalter hinter der letzten Wolke: die Antwort steht schon in früheren Abschnitten. Ein
            // Abbruch vor dem ersten Antworttext bleibt als „abgebrochen“ stehen.
            entries.remove(entry);
            fireEntryRemoved(entry);
        } else {
            entry.finish(state, failure);
            fireEntryUpdated(entry);
        }
        mergeSplitSections();
        fireStateChanged();
    }

    /**
     * Abschnitte, die nur eine (jetzt verschwundene) Wolke von der Antwort davor getrennt hat, kommen zurück in diese
     * Antwort: ein Turn endet mit einer Antwortblase. Absichtlich getrennte Äußerungen
     * ({@link #beginAnswerSection}) bleiben eigene Blasen.
     */
    private void mergeSplitSections() {
        for (TranscriptEntry section : splitSections) {
            int index = entries.indexOf(section);
            if (index <= 0) {
                continue;
            }
            TranscriptEntry previous = entries.get(index - 1);
            if (previous.getAuthor() != TranscriptEntry.Author.ASSISTANT
                    || previous.getState() != TranscriptEntry.State.COMPLETE) {
                continue;
            }
            String text = section.getText().replaceFirst("^[\\r\\n]+", "");
            if (!text.isEmpty()) {
                previous.append("\n\n" + text);
            }
            if (section.getState() != TranscriptEntry.State.COMPLETE) {
                previous.finish(section.getState(), section.getFailureMessage());
            }
            if (section.hasSources() && !previous.hasSources()) {
                previous.setSources(section.getSources());
            }
            entries.remove(index);
            fireEntryRemoved(section);
            fireEntryUpdated(previous);
        }
        splitSections.clear();
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

    private void fireEntryRemoved(TranscriptEntry entry) {
        for (ChatShellModelListener listener : listeners) {
            listener.entryRemoved(entry);
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
