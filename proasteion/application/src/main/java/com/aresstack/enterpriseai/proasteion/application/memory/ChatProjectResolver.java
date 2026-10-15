package com.aresstack.enterpriseai.proasteion.application.memory;

/**
 * Which project a chat belongs to right now (#143). Asked on every memory call, so moving a chat to another project
 * withdraws access to the old project's memories at once. No inheritance from parent folders.
 */
public interface ChatProjectResolver {

    /** The id of the project {@code chatId} is assigned to now, or {@code null} if it has none. */
    String projectOf(String chatId);
}
