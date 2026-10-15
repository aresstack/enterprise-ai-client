package com.aresstack.enterpriseai.memory.api;

/** Result of {@link MemoryStorePort#save}: the committed memory and whether it already existed for that owner. */
public final class MemorySaved {

    private final Memory memory;
    private final boolean existing;

    public MemorySaved(Memory memory, boolean existing) {
        if (memory == null) {
            throw new IllegalArgumentException("memory must not be null");
        }
        this.memory = memory;
        this.existing = existing;
    }

    public Memory memory() {
        return memory;
    }

    /** {@code true}: the owner already had a memory with the same content; nothing was written or overwritten. */
    public boolean existing() {
        return existing;
    }
}
