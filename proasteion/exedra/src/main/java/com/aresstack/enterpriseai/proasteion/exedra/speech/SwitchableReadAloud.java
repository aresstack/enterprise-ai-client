package com.aresstack.enterpriseai.proasteion.exedra.speech;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechRate;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Die Sprachausgabe des Verlaufs mit austauschbarer {@link ReadAloudBinding}: speichert der Benutzer ein anderes
 * TTS-Modell, gilt es wie die Chat-Auswahl sofort, ohne Neustart. {@link #replace} beendet die alte Bindung und
 * meldet den Wechsel, damit der Play/Pause-Orb Zustand und Tooltip neu zeichnet. Alles auf dem EDT.
 */
public final class SwitchableReadAloud implements ReadAloudControl {

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private ReadAloudBinding current;
    /** The chosen rate survives a model change: the new binding speaks at the same speed. */
    private double rate = SpeechRate.NORMAL;

    public SwitchableReadAloud(ReadAloudBinding initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial must not be null");
        }
        this.current = initial;
    }

    /** Ersetzt die Bindung (auf dem EDT); eine laufende Wiedergabe der alten endet. */
    public void replace(ReadAloudBinding next) {
        if (next == null) {
            throw new IllegalArgumentException("next must not be null");
        }
        ReadAloudBinding previous = current;
        current = next;
        previous.close();
        next.setRate(rate);
        fireChanged();
    }

    public String currentDescription() {
        return current.description();
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
    public void speak(String markdown) {
        current.speak(markdown);
    }

    @Override
    public void speak(String markdown, Runnable finished) {
        current.speak(markdown, finished);
    }

    @Override
    public void stop() {
        current.stop();
    }

    @Override
    public boolean canPause() {
        return current.canPause();
    }

    @Override
    public void pause() {
        current.pause();
    }

    @Override
    public void resume() {
        current.resume();
    }

    @Override
    public boolean isPaused() {
        return current.isPaused();
    }

    @Override
    public void setRate(double rate) {
        this.rate = SpeechRate.clamp(rate);
        current.setRate(this.rate);
    }

    @Override
    public double rate() {
        return rate;
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
