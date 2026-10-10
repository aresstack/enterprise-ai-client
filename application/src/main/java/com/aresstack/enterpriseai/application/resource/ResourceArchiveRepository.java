package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.util.List;

/**
 * Persistence port for the authoritative {@link ArchivedResource} records.
 *
 * <p>This is the single Chalcotheca port for resource records and their version histories.
 * It stores immutable records as a whole and makes no decisions: version assignment and the
 * other fact transitions live in {@link ArchivedResource}. Implementations may use in-memory
 * maps, relational databases or any other backend; the chalcotheca core does not prescribe a
 * technology. Records are never hard-deleted through this port, so histories are retained.
 *
 * <p>{@link ResourceArchive} is a compatibility facade on top of this port
 * ({@link RecordBackedResourceArchive}).
 */
public interface ResourceArchiveRepository {

    /**
     * Saves a record, replacing any record stored for the same reference.
     *
     * @param resource the record to persist
     */
    void save(ArchivedResource resource);

    /**
     * Retrieves the record for the given reference.
     *
     * @param ref the resource reference
     * @return the record, or {@code null} if the resource is unknown
     */
    ArchivedResource findByRef(VirtualResourceRef ref);

    /**
     * Returns all records whose reference has the given URI, regardless of resource kind.
     *
     * @param uri the bookmark URI
     * @return the matching records in the order in which they were first saved
     *         (never {@code null})
     */
    List<ArchivedResource> findByUri(BookmarkUri uri);
}
