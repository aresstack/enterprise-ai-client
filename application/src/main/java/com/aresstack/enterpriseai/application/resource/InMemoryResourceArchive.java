package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.ResourceDigest;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.time.Clock;

/**
 * In-memory {@link ResourceArchive}: the {@link RecordBackedResourceArchive} facade over an
 * {@link InMemoryResourceArchiveRepository}.
 *
 * <p>Suitable for tests and the walking skeleton. The snapshots it returns are views of the
 * resource records exposed by {@link #records()}; nothing is stored twice.
 */
public final class InMemoryResourceArchive implements ResourceArchive {

    private final InMemoryResourceArchiveRepository records = new InMemoryResourceArchiveRepository();
    private final RecordBackedResourceArchive archive;

    public InMemoryResourceArchive() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock the clock used for removal observations
     */
    public InMemoryResourceArchive(Clock clock) {
        this.archive = new RecordBackedResourceArchive(records, clock);
    }

    /** Returns the authoritative records behind this archive. */
    public ResourceArchiveRepository records() {
        return records;
    }

    @Override
    public void store(ResourceSnapshot snapshot) {
        archive.store(snapshot);
    }

    @Override
    public ResourceSnapshot find(VirtualResourceRef ref) {
        return archive.find(ref);
    }

    @Override
    public boolean hasChanged(VirtualResourceRef ref, ResourceDigest digest) {
        return archive.hasChanged(ref, digest);
    }

    @Override
    public boolean remove(VirtualResourceRef ref) {
        return archive.remove(ref);
    }

    @Override
    public boolean removeByUri(BookmarkUri uri) {
        return archive.removeByUri(uri);
    }

    @Override
    public ResourceSnapshot findByUri(BookmarkUri uri) {
        return archive.findByUri(uri);
    }
}
