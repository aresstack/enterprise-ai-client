package com.aresstack.enterpriseai.application.agent;

/** Zustand eines Agent-Auftrags. {@link #CANCELLING} ist nicht terminal: der Agent bestätigt den Abbruch noch. */
public enum AgentTurnState {
    RUNNING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
