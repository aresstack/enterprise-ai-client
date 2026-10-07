package com.aresstack.enterpriseai.application.agent;

/**
 * Warum ein Agent-Auftrag gescheitert ist. Bewusst ohne technische Details des Adapters: Meldungen aus dem
 * Agentenprozess oder dem SDK können Pfade, Umgebungswerte oder Endpoint-URLs enthalten.
 */
public enum AgentFailure {
    /** Agent konnte nicht gestartet oder initialisiert werden. */
    START_FAILED,
    /** Der Agent hat keine Session angelegt. */
    SESSION_FAILED,
    /** Der Agent hat den Auftrag mit einem Fehler beendet oder nicht rechtzeitig geantwortet. */
    PROMPT_FAILED,
    /** Der Agentenprozess ist während des Auftrags weggefallen. */
    AGENT_TERMINATED,
    /** Der Agent-Modus wurde geschlossen, bevor der Auftrag beim Agenten ankam. */
    CLOSED
}
