package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.ResourceDigest;

import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The authoritative, immutable resource record kept by the chalcotheca archive.
 *
 * <p>A record holds orthogonal facts about one {@link VirtualResourceRef}; it is deliberately
 * not a linear lifecycle state machine:
 * <ul>
 *   <li>the ordered bronze version history ({@link #versions()}, never empty, sequences
 *       {@code 1..n} without gaps), whose last entry is the latest observed version;</li>
 *   <li>optionally, which of those versions is recorded as indexed and when
 *       ({@link #indexedVersion()});</li>
 *   <li>optionally, the observation that the resource was removed at its source
 *       ({@link #sourceRemoval()}, the tombstone).</li>
 * </ul>
 * These facts are independent. A resource can be removed at the source while version 7 is
 * both its latest observed and its indexed version; that is a legitimate state, not an
 * inconsistency.
 *
 * <p>The record carries no decisions (cache validity, staleness, reacquisition, reindexing,
 * withdrawal, access denials) and no cache presence: those belong to Tamias (#5) and are
 * executed by Acropolis (#10). Payload bytes stay with {@link BronzeContent}.
 *
 * <p>Transitions return new instances; this is the aggregate stored by
 * {@link ResourceArchiveRepository}.
 */
public final class ArchivedResource {

    private final VirtualResourceRef ref;
    private final List<ResourceVersion> versions;
    private final IndexedVersion indexedVersion;
    private final SourceRemoval sourceRemoval;

    /**
     * Reconstitutes a record, e.g. from a persistent store.
     *
     * @param ref            the resource reference
     * @param versions       the version history in ascending order; sequences must be {@code 1..n}
     * @param indexedVersion the indexed-version fact, or {@code null}; its version must be part
     *                       of the history
     * @param sourceRemoval  the removal observation, or {@code null}
     */
    public ArchivedResource(VirtualResourceRef ref,
                            List<ResourceVersion> versions,
                            IndexedVersion indexedVersion,
                            SourceRemoval sourceRemoval) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("versions must not be empty");
        }
        List<ResourceVersion> copy = new ArrayList<ResourceVersion>(versions);
        for (int i = 0; i < copy.size(); i++) {
            ResourceVersion version = copy.get(i);
            if (version == null) {
                throw new IllegalArgumentException("versions must not contain null");
            }
            if (version.sequence() != i + 1) {
                throw new IllegalArgumentException("version sequences must be 1..n without gaps, found "
                        + version.sequence() + " at position " + (i + 1));
            }
        }
        if (indexedVersion != null) {
            long sequence = indexedVersion.version().sequence();
            if (sequence > copy.size() || !copy.get((int) sequence - 1).equals(indexedVersion.version())) {
                throw new IllegalArgumentException("indexed version must be part of the version history");
            }
        }
        this.ref = ref;
        this.versions = Collections.unmodifiableList(copy);
        this.indexedVersion = indexedVersion;
        this.sourceRemoval = sourceRemoval;
    }

    /**
     * Creates the record for the first observation of a resource.
     *
     * @param ref              the resource reference
     * @param digest           the digest produced by the bronze acquirer
     * @param observedAtMillis when the content was observed
     * @return a record with version 1 as its only version
     */
    public static ArchivedResource firstObservation(VirtualResourceRef ref, ResourceDigest digest,
                                                    long observedAtMillis) {
        return new ArchivedResource(ref,
                Collections.singletonList(new ResourceVersion(1, digest, observedAtMillis)), null, null);
    }

    /** Returns the resource reference. */
    public VirtualResourceRef ref() {
        return ref;
    }

    /** Returns the immutable version history in ascending sequence order. */
    public List<ResourceVersion> versions() {
        return versions;
    }

    /** Returns the latest observed version (never {@code null}). */
    public ResourceVersion latestObservedVersion() {
        return versions.get(versions.size() - 1);
    }

    /** Returns the indexed-version fact, or {@code null} if no version is recorded as indexed. */
    public IndexedVersion indexedVersion() {
        return indexedVersion;
    }

    /** Returns whether a version is recorded as indexed. */
    public boolean isIndexed() {
        return indexedVersion != null;
    }

    /** Returns the removal observation (tombstone), or {@code null} if none is recorded. */
    public SourceRemoval sourceRemoval() {
        return sourceRemoval;
    }

    /** Returns whether the resource was observed as removed at its source. */
    public boolean isRemovedAtSource() {
        return sourceRemoval != null;
    }

    /**
     * Records an observation of the resource content.
     *
     * <p>A digest equal to the latest observed version creates no new version; a different
     * digest appends version {@code n + 1}. Either way the resource was seen at its source, so
     * a previous removal observation no longer holds. The history and the indexed-version fact
     * are kept.
     *
     * @param digest           the digest produced by the bronze acquirer
     * @param observedAtMillis when the content was observed
     * @return the updated record (this instance if nothing changed)
     */
    public ArchivedResource observe(ResourceDigest digest, long observedAtMillis) {
        if (digest == null) {
            throw new IllegalArgumentException("digest must not be null");
        }
        if (latestObservedVersion().digest().equals(digest)) {
            return sourceRemoval == null ? this : new ArchivedResource(ref, versions, indexedVersion, null);
        }
        List<ResourceVersion> next = new ArrayList<ResourceVersion>(versions);
        next.add(new ResourceVersion(versions.size() + 1, digest, observedAtMillis));
        return new ArchivedResource(ref, next, indexedVersion, null);
    }

    /**
     * Records that the given version is indexed.
     *
     * @param version         a version of this record's history
     * @param indexedAtMillis when it was indexed
     * @return the updated record
     */
    public ArchivedResource markIndexed(ResourceVersion version, long indexedAtMillis) {
        return new ArchivedResource(ref, versions, new IndexedVersion(version, indexedAtMillis), sourceRemoval);
    }

    /**
     * Withdraws the indexed-version fact. The history and the removal observation are kept.
     *
     * @return the updated record (this instance if nothing was indexed)
     */
    public ArchivedResource withdrawIndexedVersion() {
        return indexedVersion == null ? this : new ArchivedResource(ref, versions, null, sourceRemoval);
    }

    /**
     * Records that the resource was removed at its source. The history and the
     * indexed-version fact are kept; an existing removal observation keeps its original time.
     *
     * @param observedAtMillis when the removal was observed
     * @return the updated record (this instance if it was already removed)
     */
    public ArchivedResource markRemovedAtSource(long observedAtMillis) {
        return sourceRemoval != null
                ? this
                : new ArchivedResource(ref, versions, indexedVersion, new SourceRemoval(observedAtMillis));
    }

    @Override
    public String toString() {
        return "ArchivedResource{" + ref
                + ", latest=#" + latestObservedVersion().sequence()
                + ", indexed=" + (indexedVersion == null ? "none" : "#" + indexedVersion.version().sequence())
                + (sourceRemoval == null ? "" : ", removed at source")
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArchivedResource)) return false;
        ArchivedResource that = (ArchivedResource) o;
        return ref.equals(that.ref)
                && versions.equals(that.versions)
                && (indexedVersion == null ? that.indexedVersion == null : indexedVersion.equals(that.indexedVersion))
                && (sourceRemoval == null ? that.sourceRemoval == null : sourceRemoval.equals(that.sourceRemoval));
    }

    @Override
    public int hashCode() {
        int result = ref.hashCode();
        result = 31 * result + versions.hashCode();
        result = 31 * result + (indexedVersion == null ? 0 : indexedVersion.hashCode());
        return 31 * result + (sourceRemoval == null ? 0 : sourceRemoval.hashCode());
    }
}
