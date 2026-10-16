package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemoryStoreException;
import com.aresstack.enterpriseai.memory.api.MemoryStorePort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use cases behind the memory management view (#144): list and delete the memories of exactly one owner, a chat, a
 * project or the running user. Reads the H2 memory store only; there is no second source of memories.
 *
 * <p>The user acts directly in the view and confirms there, so deleting does not go through the
 * {@link com.aresstack.enterpriseai.astu.approval.ToolApprovalGate}, which guards the model's {@code memory_delete}
 * calls. The delete is bound the same way: it only happens while the memory still belongs to the managed owner and
 * still has the version the view showed.
 */
public final class MemoryManagement {

    public static final int MAX_LIMIT = 500;

    /** Whose memories are managed. */
    public enum Owner {
        /** One chat; key = chat id. */
        CHAT,
        /** One project; key = project id. */
        PROJECT,
        /** The running user; no key. */
        GLOBAL
    }

    /** Outcome of {@link #delete}. */
    public enum Deletion {
        DELETED,
        /** The memory is gone or was changed after the view read it; nothing deleted. */
        CHANGED,
        /** The memory belongs to another owner than the one managed; nothing deleted. */
        NOT_OWNED,
        UNAVAILABLE
    }

    private static final Logger LOG = Logger.getLogger(MemoryManagement.class.getName());

    private final MemoryStorePort store;
    private final String userId;

    /** @param userId the running user/profile; key of the global memories */
    public MemoryManagement(MemoryStorePort store, String userId) {
        if (store == null || userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("store and userId must be given");
        }
        this.store = store;
        this.userId = userId;
    }

    /**
     * Memories of the owner whose content contains every word of {@code query}, newest first; a blank query lists all
     * up to {@code limit}. Empty for a missing key.
     *
     * @throws MemoryStoreException when the store is not available
     */
    public List<Item> list(Owner kind, String key, String query, int limit) {
        MemoryOwner owner = owner(kind, key);
        if (owner == null) {
            return Collections.emptyList();
        }
        int max = limit < 1 ? MAX_LIMIT : Math.min(limit, MAX_LIMIT);
        List<Item> items = new ArrayList<Item>();
        for (Memory memory : store.search(Collections.singletonList(owner), query, max)) {
            items.add(new Item(memory.id().visible(), memory.version(), memory.content(), memory.createdAt()));
        }
        return items;
    }

    /**
     * Deletes the memory with the visible id {@code id} (e.g. {@code M-0042}) only while it belongs to the owner and
     * still has {@code expectedVersion}.
     */
    public Deletion delete(Owner kind, String key, String id, long expectedVersion) {
        MemoryOwner owner = owner(kind, key);
        MemoryId memoryId = MemoryId.parse(id);
        if (owner == null || memoryId == null) {
            return Deletion.NOT_OWNED;
        }
        try {
            Memory memory = store.find(memoryId);
            if (memory == null) {
                return Deletion.CHANGED;
            }
            if (!owner.equals(memory.owner())) {
                return Deletion.NOT_OWNED;
            }
            if (memory.version() != expectedVersion) {
                return Deletion.CHANGED;
            }
            return store.delete(memoryId, expectedVersion) ? Deletion.DELETED : Deletion.CHANGED;
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory deletion failed: " + e.getMessage(), e);
            return Deletion.UNAVAILABLE;
        }
    }

    private MemoryOwner owner(Owner kind, String key) {
        if (kind == Owner.GLOBAL) {
            return MemoryOwner.global(userId);
        }
        if (kind == null || key == null || key.trim().isEmpty()) {
            return null;
        }
        return kind == Owner.CHAT ? MemoryOwner.chat(key) : MemoryOwner.project(key);
    }

    /** One memory as the management view shows it; {@code version} binds a later delete. */
    public static final class Item {

        private final String id;
        private final long version;
        private final String content;
        private final Instant createdAt;

        Item(String id, long version, String content, Instant createdAt) {
            this.id = id;
            this.version = version;
            this.content = content;
            this.createdAt = createdAt;
        }

        /** Visible id such as {@code M-0042}. */
        public String id() {
            return id;
        }

        public long version() {
            return version;
        }

        public String content() {
            return content;
        }

        public Instant createdAt() {
            return createdAt;
        }
    }
}
