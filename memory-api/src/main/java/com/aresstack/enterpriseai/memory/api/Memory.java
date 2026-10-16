package com.aresstack.enterpriseai.memory.api;

import java.time.Instant;

/** One stored memory. {@code version} changes whenever the stored row changes; deletion is bound to it. */
public final class Memory {

    private final MemoryId id;
    private final MemoryOwner owner;
    private final String content;
    private final Instant createdAt;
    private final long version;

    public Memory(MemoryId id, MemoryOwner owner, String content, Instant createdAt, long version) {
        if (id == null || owner == null || content == null || createdAt == null) {
            throw new IllegalArgumentException("id, owner, content and createdAt must not be null");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive: " + version);
        }
        this.id = id;
        this.owner = owner;
        this.content = content;
        this.createdAt = createdAt;
        this.version = version;
    }

    public MemoryId id() {
        return id;
    }

    public MemoryOwner owner() {
        return owner;
    }

    public MemoryScope scope() {
        return owner.scope();
    }

    public String content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "Memory[" + id + ", " + owner + ", v" + version + "]";
    }
}
