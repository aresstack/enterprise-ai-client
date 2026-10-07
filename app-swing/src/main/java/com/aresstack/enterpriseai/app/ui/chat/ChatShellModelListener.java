package com.aresstack.enterpriseai.app.ui.chat;

/** Beobachtet das {@link ChatShellModel}; wird auf dem Thread des Models (in der Anwendung: EDT) gerufen. */
public interface ChatShellModelListener {

    void entryAdded(TranscriptEntry entry);

    /** Text oder Zustand eines vorhandenen Eintrags hat sich geändert (Streaming-Delta, Abschluss, Fehler). */
    void entryUpdated(TranscriptEntry entry);

    /** Streaming-Zustand oder RAG-Schalter haben sich geändert; Send/Stop neu bewerten. */
    void stateChanged();
}
