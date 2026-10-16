package com.aresstack.enterpriseai.proasteion.exedra.ui.memory;

import java.util.List;

/** The memories of exactly one owner (a chat, a project or the user), as the management view sees them. */
public interface MemoryBrowser {

    /** Tab or heading, e.g. "Projekt" or "Global". */
    String title();

    /** One sentence on who sees these memories. */
    String description();

    /**
     * The owner's memories containing every word of {@code query}, newest first; blank lists all.
     *
     * @throws RuntimeException when the memory store is not available; the view shows the message
     */
    List<MemoryEntry> list(String query);

    /** Deletes the memory if it is still unchanged; {@code null} on success, otherwise a sentence for the user. */
    String delete(MemoryEntry entry);
}
