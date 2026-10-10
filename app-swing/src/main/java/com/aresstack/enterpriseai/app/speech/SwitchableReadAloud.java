package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.ui.chat.ReadAloudControl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Die Sprachausgabe des Verlaufs mit austauschbarer {@link ReadAloudBinding}: speichert der Benutzer ein anderes
 * TTS-Modell, gilt es wie die Chat-Auswahl sofort, ohne Neustart. {@link #replace} beendet die alte Bindung und
 * meldet den Wechsel, damit die Lautsprecher-Knöpfe Zustand und Tooltip neu zeichnen. Alles auf dem EDT.
 */
public final class SwitchableReadAloud implements ReadAloudControl {

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private ReadAloudBinding current;

    public SwitchableReadAloud(ReadAloudBinding initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial must not be null");
        }
        this.current = initial;
        attach(initial);
    }

    /** Ersetzt die Bindung (auf dem EDT); eine laufende Wiedergabe der alten endet. */
    public void replace(ReadAloudBinding next) {
        if (next == null) {
            throw new IllegalArgumentException("next must not be null");
        }
        ReadAloudBinding previous = current;
        current = next;
        attach(next);
        previous.close();
        fireChanged();
    }

    public String currentDescription() {
        return current.description();
    }

    private void attach(final ReadAloudBinding binding) {
        binding.addListener(new Listener() {
            @Override
            public void readAloudChanged() {
                if (binding == current) {
                    fireChanged();
                }
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return current.isAvailable();
    }

    @Override
    public String description() {
        return current.description();
    }

    @Override
    public boolean isReading(long entryId) {
        return current.isReading(entryId);
    }

    @Override
    public void toggle(long entryId, String markdown) {
        current.toggle(entryId, markdown);
    }

    @Override
    public boolean autoStart() {
        return current.autoStart();
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** Beendet das Vorlesen beim Herunterfahren; idempotent. */
    public void close() {
        current.close();
    }

    private void fireChanged() {
        for (Listener listener : listeners) {
            listener.readAloudChanged();
        }
    }
}
