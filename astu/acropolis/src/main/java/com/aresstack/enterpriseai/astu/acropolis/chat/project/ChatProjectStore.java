package com.aresstack.enterpriseai.astu.acropolis.chat.project;

/**
 * Port: persists the chat project tree and the chat assignments, separately from the chat messages
 * ({@link com.aresstack.enterpriseai.astu.acropolis.history.ChatHistoryStore}).
 */
public interface ChatProjectStore {

    /** The stored state; an empty record when nothing was stored yet or the store is unreadable. */
    ChatProjectsRecord load();

    /** Replaces the stored state. Failures are logged by the adapter, never thrown. */
    void save(ChatProjectsRecord record);
}
