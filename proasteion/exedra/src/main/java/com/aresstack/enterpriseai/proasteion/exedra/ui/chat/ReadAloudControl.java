package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechRate;

/**
 * Bedienabsicht „Vorlesen“ für den Verlauf (wie {@link ChatShellActions} nur Absichten, keine Ports): der zentrale
 * Play/Pause-Orb aus askai-java8 {@code arch}. Ist keine Sprachausgabe verfügbar, bleibt der Orb deaktiviert und
 * sein Tooltip nennt den Grund. Alle Methoden laufen auf dem EDT.
 */
public interface ReadAloudControl {

    /** Die Sprachausgabe wurde ausgetauscht (anderes TTS-Modell): Verfügbarkeit und Tooltip neu lesen; auf dem EDT. */
    interface Listener {
        void readAloudChanged();
    }

    /** Ob vorgelesen werden kann. */
    boolean isAvailable();

    /** Tooltip-Zusatz des Orbs: die Stimme oder, wenn nicht verfügbar, der Grund. */
    String description();

    /** Ob das Vorlesen beim Start aktiv ist (neue Antworten werden dann ohne Klick vorgelesen). */
    boolean autoStart();

    /** Liest diese Antwort vor; eine laufende Ausgabe endet vorher. Kehrt sofort zurück. */
    void speak(String markdown);

    /**
     * Wie {@link #speak(String)}, meldet danach auf dem EDT, dass die Ausgabe vorbei ist (fertig gesprochen,
     * fehlgeschlagen, gestoppt oder abgelöst); ohne Sprachausgabe sofort. Für Abläufe, die auf das Ende warten.
     */
    default void speak(String markdown, Runnable finished) {
        speak(markdown);
        if (finished != null) {
            finished.run();
        }
    }

    /** Beendet die laufende Ausgabe; idempotent. */
    void stop();

    /** Whether the running output can be held and continued ({@link #pause()}, {@link #resume()}). */
    default boolean canPause() {
        return false;
    }

    /** Holds the running output at the current position; {@link #stop()} or a new speak discards it. */
    default void pause() {
    }

    /** Continues a paused output where it stopped. */
    default void resume() {
    }

    default boolean isPaused() {
        return false;
    }

    /** Speaking rate of the output ({@link SpeechRate}); applies to a running output from the next block. */
    default void setRate(double rate) {
    }

    default double rate() {
        return SpeechRate.NORMAL;
    }

    void addListener(Listener listener);
}
