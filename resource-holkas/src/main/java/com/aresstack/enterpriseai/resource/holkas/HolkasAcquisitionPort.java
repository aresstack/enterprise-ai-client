package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceKind;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;
import com.aresstack.enterpriseai.resource.api.AcquisitionPort;
import com.aresstack.enterpriseai.resource.api.BronzeContent;
import com.aresstack.enterpriseai.resource.api.BronzeListing;
import com.aresstack.enterpriseai.resource.api.ContentHasher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chalcotheca acquisition adapter backed by Holkas connectors.
 *
 * <p>This class keeps Holkas behind the mediated bronze-access boundary. Clients
 * still use Chalcotheca; Chalcotheca calls this internal acquisition port.
 */
public final class HolkasAcquisitionPort implements AcquisitionPort {

    private final ResourceConnectorRegistry connectorRegistry;

    public HolkasAcquisitionPort(ResourceConnectorRegistry connectorRegistry) {
        if (connectorRegistry == null) {
            throw new IllegalArgumentException("connectorRegistry must not be null");
        }
        this.connectorRegistry = connectorRegistry;
    }

    @Override
    public BronzeContent fetchContent(BookmarkUri uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.FILE);
        ResourceConnector connector = connectorRegistry.require(uri.scheme());
        RawResource raw = connector.fetch(ref);
        byte[] bytes = raw.content().bytes();
        return new BronzeContent(uri, bytes, ContentHasher.digest(bytes), System.currentTimeMillis());
    }

    @Override
    public BronzeListing listChildren(BookmarkUri uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        VirtualResourceRef ref = new VirtualResourceRef(uri, VirtualResourceKind.DIRECTORY);
        ResourceConnector connector = connectorRegistry.require(uri.scheme());
        ResourceListing listing = connector.list(ref);
        List<BronzeListing.Entry> entries = new ArrayList<BronzeListing.Entry>();
        for (ResourceListingEntry entry : listing.entries()) {
            entries.add(new BronzeListing.Entry(entry.ref().uri(), entry.name(), entry.kind()));
        }
        return new BronzeListing(uri, entries, listing.observedAtMillis());
    }
}
