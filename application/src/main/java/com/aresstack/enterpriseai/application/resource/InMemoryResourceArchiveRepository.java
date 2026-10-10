package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic in-memory reference implementation of {@link ResourceArchiveRepository}.
 *
 * <p>Suitable for tests and the walking skeleton. Records are immutable, so the stored
 * instances can be handed out directly. A persistent adapter (e.g. H2) is a later outer
 * adapter behind the same port.
 */
public final class InMemoryResourceArchiveRepository implements ResourceArchiveRepository {

    private final Map<VirtualResourceRef, ArchivedResource> resources =
            new LinkedHashMap<VirtualResourceRef, ArchivedResource>();

    @Override
    public synchronized void save(ArchivedResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        resources.put(resource.ref(), resource);
    }

    @Override
    public synchronized ArchivedResource findByRef(VirtualResourceRef ref) {
        if (ref == null) {
            return null;
        }
        return resources.get(ref);
    }

    @Override
    public synchronized List<ArchivedResource> findByUri(BookmarkUri uri) {
        List<ArchivedResource> result = new ArrayList<ArchivedResource>();
        if (uri == null) {
            return result;
        }
        for (ArchivedResource resource : resources.values()) {
            if (uri.equals(resource.ref().uri())) {
                result.add(resource);
            }
        }
        return result;
    }
}
