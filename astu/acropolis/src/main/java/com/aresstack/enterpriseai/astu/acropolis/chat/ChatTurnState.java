package com.aresstack.enterpriseai.astu.acropolis.chat;

/** Zustand eines Chat-Turns. */
public enum ChatTurnState {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this != RUNNING;
    }
}
