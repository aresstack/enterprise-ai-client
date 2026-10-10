package com.aresstack.enterpriseai.app.ui.chat;

/**
 * Bedienabsicht „Antwort vorlesen“ für den Verlauf (wie {@link ChatShellActions} nur Absichten, keine Ports): unter
 * jeder fertigen Antwort steht ein Lautsprecher-Knopf. Ist keine Sprachausgabe verfügbar, bleibt er deaktiviert und
 * sein Tooltip nennt den Grund. Alle Methoden laufen auf dem EDT; Rückmeldungen ebenfalls.
 */
public interface ReadAloudControl {

    /** Der Vorlesezustand hat sich geändert (Start, Ende, Stopp); auf dem EDT. */
    interface Listener {
        void readAloudChanged();
    }

    /** Ob vorgelesen werden kann. */
    boolean isAvailable();

    /** Tooltip des Knopfs: das Modell oder, wenn nicht verfügbar, der Grund. */
    String description();

    /** Ob gerade diese Antwort vorgelesen wird. */
    boolean isReading(long entryId);

    /** Startet das Vorlesen dieser Antwort oder stoppt es, wenn sie gerade gelesen wird. */
    void toggle(long entryId, String markdown);

    /** Ob neue Antworten nach dem Streaming automatisch vorgelesen werden. */
    boolean autoStart();

    void addListener(Listener listener);
}
