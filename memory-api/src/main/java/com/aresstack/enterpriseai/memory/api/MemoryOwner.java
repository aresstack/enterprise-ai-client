package com.aresstack.enterpriseai.memory.api;

/**
 * The context a memory belongs to: a scope and the key of the chat, project or user within that scope. Built by the
 * application from its trusted runtime context only.
 */
public final class MemoryOwner {

    private static final int MAX_KEY_LENGTH = 256;

    private final MemoryScope scope;
    private final String key;

    private MemoryOwner(MemoryScope scope, String key) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("owner key must not be empty");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("owner key too long: " + key.length());
        }
        this.scope = scope;
        this.key = key;
    }

    public static MemoryOwner of(MemoryScope scope, String key) {
        return new MemoryOwner(scope, key);
    }

    public static MemoryOwner chat(String chatId) {
        return new MemoryOwner(MemoryScope.CHAT, chatId);
    }

    public static MemoryOwner project(String projectId) {
        return new MemoryOwner(MemoryScope.PROJECT, projectId);
    }

    public static MemoryOwner global(String userId) {
        return new MemoryOwner(MemoryScope.GLOBAL, userId);
    }

    public MemoryScope scope() {
        return scope;
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemoryOwner)) {
            return false;
        }
        MemoryOwner that = (MemoryOwner) other;
        return scope == that.scope && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return 31 * scope.hashCode() + key.hashCode();
    }

    @Override
    public String toString() {
        return scope.wireName() + ":" + key;
    }
}
