package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.ResourceScheme;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Connector-Registry über den Quellen-Adaptern: die benannten Connectoren für {@code wiki}, {@code confluence} und
 * {@code file}, für jedes weitere Schema ein {@link KnowledgeSourceResourceConnector} über denselben Quellen (ein
 * Quelltyp, den ein späterer Adapter mitbringt, braucht so keinen Eintrag hier).
 */
public final class KnowledgeSourceConnectorRegistry implements ResourceConnectorRegistry {

    private final Supplier<? extends Collection<KnowledgeSourcePort>> sources;
    private final LongSupplier clock;
    private final Map<ResourceScheme, ResourceConnector> connectors =
            new ConcurrentHashMap<ResourceScheme, ResourceConnector>();

    public KnowledgeSourceConnectorRegistry(Supplier<? extends Collection<KnowledgeSourcePort>> sources,
                                            LongSupplier clock) {
        if (sources == null || clock == null) {
            throw new IllegalArgumentException("sources and clock must not be null");
        }
        this.sources = sources;
        this.clock = clock;
        List<ResourceConnector> named = new ArrayList<ResourceConnector>();
        named.add(new WikiResourceConnector(sources, clock));
        named.add(new ConfluenceResourceConnector(sources, clock));
        named.add(new LocalFilesResourceConnector(sources, clock));
        for (ResourceConnector connector : named) {
            connectors.put(connector.supportedScheme(), connector);
        }
    }

    @Override
    public ResourceConnector find(ResourceScheme scheme) {
        if (scheme == null) {
            return null;
        }
        ResourceConnector connector = connectors.get(scheme);
        if (connector == null) {
            ResourceConnector created = new KnowledgeSourceResourceConnector(scheme, sources, clock);
            connector = connectors.putIfAbsent(scheme, created);
            if (connector == null) {
                connector = created;
            }
        }
        return connector;
    }

    @Override
    public ResourceConnector require(ResourceScheme scheme) throws ResourceConnectorException {
        ResourceConnector connector = find(scheme);
        if (connector == null) {
            throw new ResourceConnectorException("No resource connector registered for scheme: " + scheme);
        }
        return connector;
    }

    @Override
    public Set<ResourceScheme> supportedSchemes() {
        return Collections.unmodifiableSet(new LinkedHashSet<ResourceScheme>(connectors.keySet()));
    }
}
