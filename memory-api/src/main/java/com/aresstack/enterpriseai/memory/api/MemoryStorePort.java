package com.aresstack.enterpriseai.memory.api;

import java.util.Collection;
import java.util.List;

/**
 * Persistent memories (#144). Every write is one transaction: it either commits completely or changes nothing. All
 * methods throw {@link MemoryStoreException} when the storage fails. Authorization is the caller's job; the store only
 * filters by the owners it is given.
 */
public interface MemoryStorePort {

    /**
     * Stores {@code content} for {@code owner} under a new id, or returns the owner's existing memory with exactly the
     * same content without changing it. Returns only after the commit.
     */
    MemorySaved save(MemoryOwner owner, String content);

    /**
     * Memories of the given owners whose content contains every word of {@code query} (case-insensitive), newest
     * first; a blank query lists the newest. At most {@code limit} results. Owners not in the collection are never
     * returned.
     */
    List<Memory> search(Collection<MemoryOwner> owners, String query, int limit);

    /** The memory with this id, or {@code null} if there is none (any more). */
    Memory find(MemoryId id);

    /**
     * Deletes the memory only if it still has {@code expectedVersion}; {@code false} if it is gone or has changed in
     * the meantime, in which case nothing is deleted.
     */
    boolean delete(MemoryId id, long expectedVersion);

    /** Deletes all memories of {@code owner} (e.g. when its chat is deleted); returns how many. */
    int deleteAll(MemoryOwner owner);
}
