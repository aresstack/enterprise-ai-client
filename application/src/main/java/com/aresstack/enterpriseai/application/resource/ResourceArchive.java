package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.ResourceDigest;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

/**
 * Snapshot-level compatibility facade over the authoritative resource records (#33).
 *
 * <p>The truth lives in {@link ArchivedResource} records behind the
 * {@link ResourceArchiveRepository}; a {@link ResourceSnapshot} is only a view of a record's
 * indexed-version fact and is never stored separately ({@link RecordBackedResourceArchive}).
 * The facade stays until the lifecycle moves onto the record port (#10 Slice 5). It answers
 * fact queries only: whether to reacquire, reindex or withdraw is decided by Tamias (#5) and
 * executed by Acropolis (#10).
 */
public interface ResourceArchive {

    /**
     * Records the snapshot's digest as an observed version (a new version only if the digest
     * differs from the latest observed one) and marks that version as indexed at the snapshot's
     * {@code indexedAtMillis}.
     *
     * @param snapshot the snapshot to store
     */
    void store(ResourceSnapshot snapshot);

    /**
     * Returns the version recorded as indexed for the given resource, or {@code null} if no
     * version is recorded as indexed. Never falls back to the latest observed version.
     *
     * @param ref the resource reference
     * @return the indexed snapshot, or {@code null}
     */
    ResourceSnapshot find(VirtualResourceRef ref);

    /**
     * Fact query: returns {@code true} if no version is recorded as indexed, or if the digest
     * differs from the indexed version's digest. This is not a policy decision.
     *
     * @param ref    the resource reference
     * @param digest the current digest
     * @return {@code true} if the digest differs from the indexed version (or none is indexed)
     */
    boolean hasChanged(VirtualResourceRef ref, ResourceDigest digest);

    /**
     * Withdraws the indexed-version fact for the given resource. The record and its version
     * history are kept; afterwards {@link #find} returns {@code null} and {@link #hasChanged}
     * returns {@code true}.
     *
     * @param ref the resource reference
     * @return {@code true} if a version was recorded as indexed, {@code false} otherwise
     */
    boolean remove(VirtualResourceRef ref);

    /**
     * Records that every resource with the given bookmark URI was removed at its source
     * (tombstone), regardless of resource kind.
     *
     * <p>Histories are kept and an indexed-version fact is <em>not</em> withdrawn: a resource
     * can be removed at the source and still be indexed until a withdrawal is decided (#5) and
     * executed (#10). Used by the mediated resource service when the kind is not known.
     *
     * @param uri the bookmark URI
     * @return {@code true} if at least one record exists for the URI
     */
    boolean removeByUri(BookmarkUri uri);

    /**
     * Finds an indexed snapshot by bookmark URI, regardless of resource kind.
     *
     * @param uri the bookmark URI
     * @return the indexed snapshot, or {@code null} if no record with this URI is indexed
     */
    ResourceSnapshot findByUri(BookmarkUri uri);
}
