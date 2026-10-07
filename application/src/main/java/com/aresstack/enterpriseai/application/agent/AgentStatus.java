package com.aresstack.enterpriseai.application.agent;

/** Zustand des Agent-Modus aus Sicht des Use Cases. */
public enum AgentStatus {
    /** Noch kein Agentenprozess gestartet; der erste Auftrag startet ihn. */
    NOT_STARTED,
    /** Prozessstart, ACP-Initialisierung oder Session-Anlage laufen. */
    STARTING,
    /** Verbindung und Session sind bereit. */
    READY,
    /** Start fehlgeschlagen oder Agentenprozess beendet; der nächste Auftrag startet ihn neu. */
    FAILED,
    /** {@link AgentService#close()} wurde gerufen; endgültig. */
    CLOSED
}
