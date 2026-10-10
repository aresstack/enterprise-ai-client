package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.ResourceDigest;

import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

/**
 * Compatibility view of a resource's indexed version: reference, the digest of the version
 * recorded as indexed, and when it was indexed.
 *
 * <p>A snapshot is a value passed to and returned by the {@link ResourceArchive} facade. It is
 * not stored on its own; the authoritative facts live in {@link ArchivedResource}.
 */
public final class ResourceSnapshot {

    private final VirtualResourceRef ref;
    private final ResourceDigest digest;
    private final long indexedAtMillis;

    public ResourceSnapshot(VirtualResourceRef ref, ResourceDigest digest, long indexedAtMillis) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (digest == null) {
            throw new IllegalArgumentException("digest must not be null");
        }
        this.ref = ref;
        this.digest = digest;
        this.indexedAtMillis = indexedAtMillis;
    }

    /** Returns the resource reference. */
    public VirtualResourceRef ref() {
        return ref;
    }

    /** Returns the digest at the time of indexing. */
    public ResourceDigest digest() {
        return digest;
    }

    /** Returns the timestamp when the resource was last indexed. */
    public long indexedAtMillis() {
        return indexedAtMillis;
    }

    @Override
    public String toString() {
        return "ResourceSnapshot{" + ref + ", " + digest + "}";
    }
}
