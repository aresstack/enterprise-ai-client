package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.ResourceScheme;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Connector-Registry über den Quellen-Adaptern: ein {@link KnowledgeSourceResourceConnector} je Ressourcenschema der
 * angebundenen Quelltypen. MediaWiki, Confluence, lokale Dateien, FTP, NDV, JES, SharePoint, Outlook und BetaView
 * laufen damit über denselben Weg; ein Schema, das kein Quelltyp angemeldet hat, bekommt beim ersten Zugriff
 * denselben generischen Connector.
 */
public final class KnowledgeSourceConnectorRegistry implements ResourceConnectorRegistry {

    private final Supplier<? extends Collection<KnowledgeSourcePort>> sources;
    private final LongSupplier clock;
    private final Map<ResourceScheme, ResourceConnector> connectors =
            new ConcurrentHashMap<ResourceScheme, ResourceConnector>();

    /**
     * @param schemes Ressourcenschemata der Quelltypen ({@code KnowledgeSourceType.scheme()})
     * @param sources die aktuell angebundenen Quellen
     */
    public KnowledgeSourceConnectorRegistry(Collection<ResourceScheme> schemes,
                                            Supplier<? extends Collection<KnowledgeSourcePort>> sources,
                                            LongSupplier clock) {
        if (schemes == null || sources == null || clock == null) {
            throw new IllegalArgumentException("schemes, sources and clock must not be null");
        }
        this.sources = sources;
        this.clock = clock;
        for (ResourceScheme scheme : schemes) {
            connectors.put(scheme, new KnowledgeSourceResourceConnector(scheme, sources, clock));
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
