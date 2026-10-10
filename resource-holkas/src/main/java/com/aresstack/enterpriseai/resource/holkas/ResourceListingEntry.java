package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.VirtualResourceKind;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

/**
 * A transport-near child entry observed in a resource listing.
 */
public final class ResourceListingEntry {

    private final VirtualResourceRef ref;
    private final String name;
    private final VirtualResourceKind kind;
    private final RawResourceMetadata metadata;

    public ResourceListingEntry(VirtualResourceRef ref, String name, VirtualResourceKind kind,
                                RawResourceMetadata metadata) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.ref = ref;
        this.name = name;
        this.kind = kind;
        this.metadata = metadata;
    }

    public VirtualResourceRef ref() {
        return ref;
    }

    public String name() {
        return name;
    }

    public VirtualResourceKind kind() {
        return kind;
    }

    public RawResourceMetadata metadata() {
        return metadata;
    }
}
