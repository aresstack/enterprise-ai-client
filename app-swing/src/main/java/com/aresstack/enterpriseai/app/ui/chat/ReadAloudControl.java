package com.aresstack.enterpriseai.app.ui.chat;

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

    /** Beendet die laufende Ausgabe; idempotent. */
    void stop();

    void addListener(Listener listener);
}
