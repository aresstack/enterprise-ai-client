package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transport-near listing returned by a raw resource connector.
 */
public final class ResourceListing {

    private final VirtualResourceRef containerRef;
    private final List<ResourceListingEntry> entries;
    private final long observedAtMillis;

    public ResourceListing(VirtualResourceRef containerRef, List<ResourceListingEntry> entries,
                           long observedAtMillis) {
        if (containerRef == null) {
            throw new IllegalArgumentException("containerRef must not be null");
        }
        this.containerRef = containerRef;
        this.entries = entries != null
                ? Collections.unmodifiableList(new ArrayList<ResourceListingEntry>(entries))
                : Collections.<ResourceListingEntry>emptyList();
        this.observedAtMillis = observedAtMillis;
    }

    public VirtualResourceRef containerRef() {
        return containerRef;
    }

    public List<ResourceListingEntry> entries() {
        return entries;
    }

    public long observedAtMillis() {
        return observedAtMillis;
    }
}
