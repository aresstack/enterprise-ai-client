package com.aresstack.enterpriseai.resource.api;

import com.aresstack.enterpriseai.domain.resource.ResourceFingerprint;

/**
 * Digest information for determining if a resource has changed.
 */
public final class ResourceDigest {

    private final ResourceFingerprint fingerprint;
    private final long sizeBytes;

    public ResourceDigest(ResourceFingerprint fingerprint, long sizeBytes) {
        if (fingerprint == null) {
            throw new IllegalArgumentException("fingerprint must not be null");
        }
        this.fingerprint = fingerprint;
        this.sizeBytes = sizeBytes;
    }

    /** Returns the content fingerprint. */
    public ResourceFingerprint fingerprint() {
        return fingerprint;
    }

    /** Returns the content size in bytes. */
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public String toString() {
        return "ResourceDigest{" + fingerprint + ", " + sizeBytes + " bytes}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceDigest)) return false;
        ResourceDigest that = (ResourceDigest) o;
        return sizeBytes == that.sizeBytes && fingerprint.equals(that.fingerprint);
    }

    @Override
    public int hashCode() {
        return 31 * fingerprint.hashCode() + Long.valueOf(sizeBytes).hashCode();
    }
}
