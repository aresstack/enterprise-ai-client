package com.aresstack.enterpriseai.application.resource;

/**
 * The fact that a specific observed version of a resource is recorded as indexed.
 *
 * <p>This is a fact, not a decision: it says which version derived indexes were last built
 * from and when. Whether a resource should be (re)indexed or withdrawn is decided by Tamias
 * (#5) and executed by Acropolis (#10).
 */
public final class IndexedVersion {

    private final ResourceVersion version;
    private final long indexedAtMillis;

    public IndexedVersion(ResourceVersion version, long indexedAtMillis) {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        this.version = version;
        this.indexedAtMillis = indexedAtMillis;
    }

    /** Returns the version that is recorded as indexed. */
    public ResourceVersion version() {
        return version;
    }

    /** Returns the epoch millis when the version was recorded as indexed. */
    public long indexedAtMillis() {
        return indexedAtMillis;
    }

    @Override
    public String toString() {
        return "IndexedVersion{#" + version.sequence() + ", indexed=" + indexedAtMillis + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexedVersion)) return false;
        IndexedVersion that = (IndexedVersion) o;
        return indexedAtMillis == that.indexedAtMillis && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return 31 * version.hashCode() + Long.valueOf(indexedAtMillis).hashCode();
    }
}
