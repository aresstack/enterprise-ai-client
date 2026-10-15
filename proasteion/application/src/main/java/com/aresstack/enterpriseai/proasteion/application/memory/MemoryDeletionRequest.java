package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryScope;

/**
 * The deletion an approval is asked for, resolved from the store (not from model arguments). An approval holds for
 * exactly this id and version; the store refuses the deletion if the memory changed in between.
 */
public final class MemoryDeletionRequest {

    private final MemoryId id;
    private final MemoryScope scope;
    private final long version;
    private final String preview;

    public MemoryDeletionRequest(MemoryId id, MemoryScope scope, long version, String preview) {
        if (id == null || scope == null) {
            throw new IllegalArgumentException("id and scope must not be null");
        }
        this.id = id;
        this.scope = scope;
        this.version = version;
        this.preview = preview == null ? "" : preview;
    }

    public MemoryId id() {
        return id;
    }

    public MemoryScope scope() {
        return scope;
    }

    public long version() {
        return version;
    }

    /** Short text of the memory to show in the confirmation. */
    public String preview() {
        return preview;
    }
}
