package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.ResourceDigest;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.time.Clock;
import java.util.List;

/**
 * {@link ResourceArchive} compatibility facade over the authoritative resource records.
 *
 * <p>Snapshots are not stored separately: every operation maps onto the
 * {@link ArchivedResource} held by the {@link ResourceArchiveRepository}, and every
 * {@link ResourceSnapshot} returned is a view of the record's indexed-version fact. The facade
 * answers fact queries only; it makes no Tamias decisions and knows nothing about payload
 * caches.
 *
 * <p>Read-modify-write sequences are serialized on this instance. A persistent repository
 * shared by several facades or processes needs its own transactional guarantee.
 */
public final class RecordBackedResourceArchive implements ResourceArchive {

    private final ResourceArchiveRepository repository;
    private final Clock clock;

    /**
     * @param repository the record repository that holds the truth
     * @param clock      the clock used for observations that carry no time of their own
     *                   (removal at source)
     */
    public RecordBackedResourceArchive(ResourceArchiveRepository repository, Clock clock) {
        if (repository == null) throw new IllegalArgumentException("repository must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Observes the snapshot's digest as a version and records that version as indexed.
     *
     * <p>An unchanged digest creates no new version; a changed digest creates the next version.
     * The snapshot's {@code indexedAtMillis} is used both as the indexing time and, for a new
     * version, as its observation time (the snapshot carries no separate acquisition time).
     */
    @Override
    public synchronized void store(ResourceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        ArchivedResource existing = repository.findByRef(snapshot.ref());
        ArchivedResource observed = existing == null
                ? ArchivedResource.firstObservation(snapshot.ref(), snapshot.digest(), snapshot.indexedAtMillis())
                : existing.observe(snapshot.digest(), snapshot.indexedAtMillis());
        repository.save(observed.markIndexed(observed.latestObservedVersion(), snapshot.indexedAtMillis()));
    }

    /**
     * Returns the indexed version as a snapshot, or {@code null} if no version is recorded as
     * indexed. Never falls back to the latest observed version.
     */
    @Override
    public synchronized ResourceSnapshot find(VirtualResourceRef ref) {
        if (ref == null) {
            return null;
        }
        return indexedSnapshot(repository.findByRef(ref));
    }

    /**
     * Pure fact query: {@code true} if no version is recorded as indexed, otherwise whether the
     * digest differs from the indexed version's digest.
     */
    @Override
    public synchronized boolean hasChanged(VirtualResourceRef ref, ResourceDigest digest) {
        ResourceSnapshot indexed = find(ref);
        if (indexed == null) {
            return true;
        }
        return !indexed.digest().equals(digest);
    }

    /**
     * Withdraws the indexed-version fact. The record, its history and its removal observation
     * are kept.
     *
     * @return {@code true} if a version was recorded as indexed
     */
    @Override
    public synchronized boolean remove(VirtualResourceRef ref) {
        ArchivedResource existing = repository.findByRef(ref);
        if (existing == null || !existing.isIndexed()) {
            return false;
        }
        repository.save(existing.withdrawIndexedVersion());
        return true;
    }

    /**
     * Records a removal at the source for every record with this URI, regardless of kind. The
     * histories and any indexed-version facts are kept.
     *
     * @return {@code true} if at least one record exists for the URI
     */
    @Override
    public synchronized boolean removeByUri(BookmarkUri uri) {
        if (uri == null) {
            return false;
        }
        List<ArchivedResource> affected = repository.findByUri(uri);
        long now = clock.millis();
        for (ArchivedResource resource : affected) {
            repository.save(resource.markRemovedAtSource(now));
        }
        return !affected.isEmpty();
    }

    /**
     * Returns the indexed version of the first record with this URI that has one, regardless
     * of kind, or {@code null}.
     */
    @Override
    public synchronized ResourceSnapshot findByUri(BookmarkUri uri) {
        if (uri == null) {
            return null;
        }
        for (ArchivedResource resource : repository.findByUri(uri)) {
            ResourceSnapshot snapshot = indexedSnapshot(resource);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    private static ResourceSnapshot indexedSnapshot(ArchivedResource resource) {
        if (resource == null || !resource.isIndexed()) {
            return null;
        }
        IndexedVersion indexed = resource.indexedVersion();
        return new ResourceSnapshot(resource.ref(), indexed.version().digest(), indexed.indexedAtMillis());
    }
}
