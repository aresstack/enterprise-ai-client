package com.aresstack.enterpriseai.app.ui.chat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Presentation-Model der Statuszeile zur Wissensbasis im Chat-Reiter (AP22), ohne Swing: ein Text, ob gerade ein
 * Lauf (Indexierung) aktiv ist, und ob der Nutzer dessen Abbruch verlangt hat.
 *
 * <p>Die Anbindung in {@code app.chat} schreibt Fortschritt und Ergebnis der Indexierung hierher (auf dem
 * UI-Thread) und fragt {@link #isCancelRequested()} vom Indexierungs-Thread aus ab; deshalb ist genau dieses Flag
 * {@code volatile}. Alles andere wird nur auf dem UI-Thread gelesen und geschrieben.
 *
 * <p>Ein leerer Text bedeutet: nichts anzuzeigen, die Zeile ist ausgeblendet.
 */
public final class KnowledgeStatusModel {

    /** Beobachtet das Model; wird auf dem Thread des Models (in der Anwendung: EDT) gerufen. */
    public interface Listener {
        void statusChanged();
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private String text = "";
    private boolean running;
    private volatile boolean cancelRequested;

    public void addListener(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Der anzuzeigende Text; leer, wenn die Zeile ausgeblendet ist. */
    public String getText() {
        return text;
    }

    public boolean isVisible() {
        return !text.isEmpty();
    }

    /** Ein abbrechbarer Lauf ist aktiv. */
    public boolean isRunning() {
        return running;
    }

    /** Der Nutzer hat den Abbruch des laufenden Laufs verlangt; vom Arbeits-Thread lesbar. */
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /** Ein Lauf beginnt; ein früherer Abbruchwunsch gilt nicht mehr. */
    public void started(String newText) {
        running = true;
        cancelRequested = false;
        text = normalize(newText);
        fire();
    }

    /** Fortschritt des laufenden Laufs. */
    public void progressed(String newText) {
        if (!running) {
            throw new IllegalStateException("no run is active");
        }
        text = normalize(newText);
        fire();
    }

    /** Der Lauf ist zu Ende (auch abgebrochen oder gescheitert); {@code newText} bleibt stehen, leer blendet aus. */
    public void finished(String newText) {
        running = false;
        cancelRequested = false;
        text = normalize(newText);
        fire();
    }

    /** Zeigt einen Text ohne laufenden Lauf (z. B. den Stand der Wissensbasis); leer blendet aus. */
    public void show(String newText) {
        if (running) {
            throw new IllegalStateException("a run is active; use progressed or finished");
        }
        text = normalize(newText);
        fire();
    }

    /** Verlangt den Abbruch des laufenden Laufs; ohne Lauf wirkungslos. Der Lauf endet nach dem aktuellen Schritt. */
    public void requestCancel() {
        if (!running || cancelRequested) {
            return;
        }
        cancelRequested = true;
        fire();
    }

    private void fire() {
        for (Listener listener : listeners) {
            listener.statusChanged();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
