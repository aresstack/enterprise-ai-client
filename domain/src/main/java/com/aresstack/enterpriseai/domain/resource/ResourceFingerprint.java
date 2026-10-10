package com.aresstack.enterpriseai.domain.resource;

/**
 * An immutable content-identity fingerprint for a virtual resource.
 *
 * <p>A fingerprint uniquely identifies the content state of a resource at a point
 * in time. It is used for cache validation, change detection and deduplication.
 */
public final class ResourceFingerprint {

    private final String algorithm;
    private final String hash;

    /**
     * @param algorithm the hash algorithm name (e.g. "SHA-256", "MD5")
     * @param hash      the hex-encoded hash value
     */
    public ResourceFingerprint(String algorithm, String hash) {
        if (algorithm == null || algorithm.isEmpty()) {
            throw new IllegalArgumentException("Algorithm must not be null or empty");
        }
        if (hash == null || hash.isEmpty()) {
            throw new IllegalArgumentException("Hash must not be null or empty");
        }
        this.algorithm = algorithm;
        this.hash = hash;
    }

    /** Returns the hash algorithm name. */
    public String algorithm() {
        return algorithm;
    }

    /** Returns the hex-encoded hash value. */
    public String hash() {
        return hash;
    }

    @Override
    public String toString() {
        return algorithm + ":" + hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceFingerprint)) return false;
        ResourceFingerprint that = (ResourceFingerprint) o;
        return algorithm.equals(that.algorithm) && hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return 31 * algorithm.hashCode() + hash.hashCode();
    }
}
