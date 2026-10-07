package com.aresstack.enterpriseai.application.chat;

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
