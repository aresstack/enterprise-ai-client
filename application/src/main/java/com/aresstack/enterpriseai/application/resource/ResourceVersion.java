package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.ResourceDigest;

/**
 * An immutable, observed bronze version of a resource.
 *
 * <p>A version is a persistable fact about a payload, not the payload itself: it records the
 * {@link ResourceDigest} produced by the bronze acquirer, the time at which this content was
 * first observed, and its position in the resource's version history. The bytes stay with
 * {@link BronzeContent}; a version history never replaces a content cache.
 *
 * <p>The {@code sequence} is assigned by the owning {@link ArchivedResource}: it starts at 1
 * and grows by one for every observed digest change, so it gives a monotone, deterministic
 * order that is independent of wall-clock timestamps.
 */
public final class ResourceVersion {

    private final long sequence;
    private final ResourceDigest digest;
    private final long observedAtMillis;

    public ResourceVersion(long sequence, ResourceDigest digest, long observedAtMillis) {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        if (digest == null) {
            throw new IllegalArgumentException("digest must not be null");
        }
        this.sequence = sequence;
        this.digest = digest;
        this.observedAtMillis = observedAtMillis;
    }

    /** Returns the position of this version in the resource's history (1-based, monotone). */
    public long sequence() {
        return sequence;
    }

    /** Returns the content digest for this version. */
    public ResourceDigest digest() {
        return digest;
    }

    /** Returns the epoch millis when this version was first observed. */
    public long observedAtMillis() {
        return observedAtMillis;
    }

    @Override
    public String toString() {
        return "ResourceVersion{#" + sequence + ", " + digest + ", observed=" + observedAtMillis + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceVersion)) return false;
        ResourceVersion that = (ResourceVersion) o;
        return sequence == that.sequence
                && observedAtMillis == that.observedAtMillis
                && digest.equals(that.digest);
    }

    @Override
    public int hashCode() {
        int result = Long.valueOf(sequence).hashCode();
        result = 31 * result + digest.hashCode();
        return 31 * result + Long.valueOf(observedAtMillis).hashCode();
    }
}
