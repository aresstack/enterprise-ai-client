package com.aresstack.enterpriseai.resource.api;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;

import java.io.IOException;

/**
 * Internal acquisition port for the bronze archive.
 *
 * <p>This port is used by Chalcotheca internally when Tamias allows acquisition
 * of a missing or stale resource. Holkas adapters implement this port.
 *
 * <p><strong>This is NOT a client-facing API.</strong> External callers (UI, bot,
 * plugin, service) must request resources through the mediated access service,
 * never through this port directly.
 */
public interface AcquisitionPort {

    /**
     * Fetches content for the given bookmark URI from the external source.
     *
     * @param uri the resource to acquire
     * @return the acquired bronze content
     * @throws IOException if acquisition fails
     */
    BronzeContent fetchContent(BookmarkUri uri) throws IOException;

    /**
     * Lists children of a container/directory resource at the given URI.
     *
     * @param uri the container resource to list
     * @return the bronze listing
     * @throws IOException if listing fails
     */
    BronzeListing listChildren(BookmarkUri uri) throws IOException;
}
