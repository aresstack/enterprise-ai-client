package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.VirtualResourceKind;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

/**
 * A raw resource fetched by a connector.
 *
 * <p>Combines the resource reference with raw content and transport-near metadata.
 */
public final class RawResource {

    private final VirtualResourceRef ref;
    private final RawResourceContent content;
    private final RawResourceMetadata metadata;

    public RawResource(VirtualResourceRef ref, RawResourceContent content,
                       String filename, long lastModifiedMillis) {
        this(ref, content, new RawResourceMetadata(filename, null,
                content != null ? content.sizeBytes() : 0L,
                lastModifiedMillis, System.currentTimeMillis(), VirtualResourceKind.FILE));
    }

    public RawResource(VirtualResourceRef ref, RawResourceContent content,
                       RawResourceMetadata metadata) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        this.ref = ref;
        this.content = content;
        this.metadata = metadata;
    }

    /** Returns the virtual resource reference. */
    public VirtualResourceRef ref() {
        return ref;
    }

    /** Returns the raw content. */
    public RawResourceContent content() {
        return content;
    }

    /** Returns transport-near metadata observed during acquisition. */
    public RawResourceMetadata metadata() {
        return metadata;
    }

    /** Returns the filename extracted from the URI path, or {@code null}. */
    public String filename() {
        return metadata.name();
    }

    /** Returns last modified time in millis since epoch, or 0 if unknown. */
    public long lastModifiedMillis() {
        return metadata.modifiedAtMillis();
    }
}
