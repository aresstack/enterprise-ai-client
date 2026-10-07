package com.aresstack.enterpriseai.domain.chat;

/** Warum der Provider die Generierung beendet hat. */
public enum ChatFinishReason {
    /** Natürliches Ende oder Stop-Sequenz. */
    STOP,
    /** Token-Limit erreicht; die Antwort ist abgeschnitten. */
    LENGTH,
    /** Inhalt vom Provider gefiltert. */
    CONTENT_FILTER,
    /** Kein oder ein unbekannter Grund gemeldet. */
    UNKNOWN
}
