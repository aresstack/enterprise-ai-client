package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.domain.resource.ResourceScheme;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceKind;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;
import com.aresstack.enterpriseai.resource.api.AcquisitionFailure;
import com.aresstack.enterpriseai.resource.api.KnowledgeBronze;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Holkas-Connector über den Quellen-Adaptern dieses Clients: ein Schema ({@code wiki}, {@code confluence},
 * {@code file}), dahinter die angebundenen {@link KnowledgeSourcePort}s mit diesem Schema. {@link #fetch} lädt das
 * Dokument aus der ersten Quelle, die die ID kennt (eine fremde ID lehnt jede Quelle als {@code UNSUPPORTED} ab),
 * und liefert es in Bronze-Form ({@link KnowledgeBronze}); {@link #list} liefert die Links eines Dokuments als
 * Kinder.
 *
 * <p>Wie in corenth liegt der Connector im äußeren Adapterring: Aufrufer kommen nur über den vermittelten Zugriff
 * (Chalcotheca, Tamias) und den {@code AcquisitionPort} hierher, nie direkt.
 */
public class KnowledgeSourceResourceConnector implements ResourceConnector {

    private final ResourceScheme scheme;
    private final Supplier<? extends Collection<KnowledgeSourcePort>> sources;
    private final LongSupplier clock;

    /**
     * @param sources die angebundenen Quellen (wird bei jedem Zugriff gefragt; Quellen kommen und gehen)
     */
    public KnowledgeSourceResourceConnector(ResourceScheme scheme,
                                            Supplier<? extends Collection<KnowledgeSourcePort>> sources,
                                            LongSupplier clock) {
        if (scheme == null || sources == null || clock == null) {
            throw new IllegalArgumentException("scheme, sources and clock must not be null");
        }
        this.scheme = scheme;
        this.sources = sources;
        this.clock = clock;
    }

    @Override
    public ResourceScheme supportedScheme() {
        return scheme;
    }

    @Override
    public RawResource fetch(VirtualResourceRef ref) throws IOException {
        KnowledgeResourceId id = idOf(ref);
        KnowledgeDocument document = null;
        for (KnowledgeSourcePort source : sources.get()) {
            try {
                document = source.load(id);
                break;
            } catch (KnowledgeSourceException e) {
                if (e.kind() != KnowledgeSourceException.Kind.UNSUPPORTED) {
                    throw new ResourceConnectorException(AcquisitionFailure.describe(e.kind().name(), id.value()), e);
                }
            }
        }
        if (document == null) {
            throw new ResourceConnectorException(AcquisitionFailure.describe(
                    KnowledgeSourceException.Kind.UNSUPPORTED.name(), id.value()));
        }
        byte[] bytes = KnowledgeBronze.encode(document);
        long modified = document.resource().revision().modifiedAt().isPresent()
                ? document.resource().revision().modifiedAt().get().toEpochMilli() : 0L;
        return new RawResource(ref, new RawResourceContent(bytes), RawResourceMetadata.file(
                document.resource().title(), document.resource().contentType(), bytes.length, modified,
                clock.getAsLong()));
    }

    @Override
    public ResourceListing list(VirtualResourceRef ref) throws IOException {
        KnowledgeResourceId id = idOf(ref);
        for (KnowledgeSourcePort source : sources.get()) {
            List<SourceLink> links;
            try {
                links = source.discoverLinks(id);
            } catch (KnowledgeSourceException e) {
                if (e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED) {
                    continue;
                }
                throw new ResourceConnectorException(AcquisitionFailure.describe(e.kind().name(), id.value()), e);
            }
            List<ResourceListingEntry> entries = new ArrayList<ResourceListingEntry>();
            long now = clock.getAsLong();
            for (SourceLink link : links) {
                VirtualResourceRef child = new VirtualResourceRef(KnowledgeBookmarks.of(link.target()),
                        VirtualResourceKind.FILE);
                entries.add(new ResourceListingEntry(child, link.label(), VirtualResourceKind.FILE,
                        RawResourceMetadata.file(link.label(), null, 0L, 0L, now)));
            }
            return new ResourceListing(ref, entries, now);
        }
        throw new ResourceConnectorException(AcquisitionFailure.describe(
                KnowledgeSourceException.Kind.UNSUPPORTED.name(), id.value()));
    }

    private KnowledgeResourceId idOf(VirtualResourceRef ref) throws ResourceConnectorException {
        if (ref == null || !supports(ref.uri().scheme())) {
            throw new IllegalArgumentException("Schema nicht unterstützt: " + (ref == null ? null : ref.uri()));
        }
        try {
            return KnowledgeBookmarks.toResourceId(ref.uri());
        } catch (IllegalArgumentException e) {
            throw new ResourceConnectorException(AcquisitionFailure.describe(
                    KnowledgeSourceException.Kind.UNSUPPORTED.name(), "keine Ressourcen-ID"));
        }
    }
}
