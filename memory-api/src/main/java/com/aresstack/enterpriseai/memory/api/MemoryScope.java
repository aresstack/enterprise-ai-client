package com.aresstack.enterpriseai.memory.api;

import java.util.Locale;

/** Validity of a memory: one chat, the chats currently assigned to one project, or all chats of the user. */
public enum MemoryScope {
    CHAT, PROJECT, GLOBAL;

    /** Lower-case name as used in tool arguments and output ({@code chat}, {@code project}, {@code global}). */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The scope named {@code value} (case-insensitive), or {@code null} if there is none. */
    public static MemoryScope parse(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (MemoryScope scope : values()) {
            if (scope.name().equalsIgnoreCase(trimmed)) {
                return scope;
            }
        }
        return null;
    }
}
