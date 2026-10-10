package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.VirtualResourceKind;

/**
 * Transport-near metadata observed while acquiring a raw resource.
 */
public final class RawResourceMetadata {

    private final String name;
    private final String contentType;
    private final long sizeBytes;
    private final long modifiedAtMillis;
    private final long observedAtMillis;
    private final VirtualResourceKind kind;

    public RawResourceMetadata(String name, String contentType, long sizeBytes,
                               long modifiedAtMillis, long observedAtMillis,
                               VirtualResourceKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.modifiedAtMillis = modifiedAtMillis;
        this.observedAtMillis = observedAtMillis;
        this.kind = kind;
    }

    public static RawResourceMetadata file(String name, String contentType, long sizeBytes,
                                           long modifiedAtMillis, long observedAtMillis) {
        return new RawResourceMetadata(name, contentType, sizeBytes, modifiedAtMillis,
                observedAtMillis, VirtualResourceKind.FILE);
    }

    public static RawResourceMetadata directory(String name, long modifiedAtMillis, long observedAtMillis) {
        return new RawResourceMetadata(name, null, 0L, modifiedAtMillis,
                observedAtMillis, VirtualResourceKind.DIRECTORY);
    }

    public String name() {
        return name;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long modifiedAtMillis() {
        return modifiedAtMillis;
    }

    public long observedAtMillis() {
        return observedAtMillis;
    }

    public VirtualResourceKind kind() {
        return kind;
    }
}
