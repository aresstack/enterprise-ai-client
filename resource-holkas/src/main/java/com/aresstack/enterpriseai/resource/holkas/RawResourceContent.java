package com.aresstack.enterpriseai.resource.holkas;

import java.util.Arrays;

/**
 * Raw bytes fetched from a resource connector, with optional metadata.
 */
public final class RawResourceContent {

    private final byte[] bytes;
    private final long sizeBytes;

    public RawResourceContent(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.sizeBytes = bytes.length;
    }

    /** Returns a defensive copy of the raw content bytes. */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /** Returns the content size in bytes. */
    public long sizeBytes() {
        return sizeBytes;
    }
}
