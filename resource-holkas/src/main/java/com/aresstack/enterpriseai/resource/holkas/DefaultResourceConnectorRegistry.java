package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.ResourceScheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable scheme-based connector registry.
 */
public final class DefaultResourceConnectorRegistry implements ResourceConnectorRegistry {

    private final Map<ResourceScheme, ResourceConnector> connectorsByScheme;

    public DefaultResourceConnectorRegistry(List<ResourceConnector> connectors) {
        if (connectors == null) {
            throw new IllegalArgumentException("connectors must not be null");
        }
        Map<ResourceScheme, ResourceConnector> map = new LinkedHashMap<ResourceScheme, ResourceConnector>();
        for (ResourceConnector connector : connectors) {
            if (connector == null) {
                throw new IllegalArgumentException("connector must not be null");
            }
            ResourceScheme scheme = connector.supportedScheme();
            if (scheme == null) {
                throw new IllegalArgumentException("connector scheme must not be null");
            }
            if (map.containsKey(scheme)) {
                throw new IllegalArgumentException("duplicate connector for scheme: " + scheme);
            }
            map.put(scheme, connector);
        }
        this.connectorsByScheme = Collections.unmodifiableMap(map);
    }

    public static DefaultResourceConnectorRegistry of(ResourceConnector connector) {
        List<ResourceConnector> connectors = new ArrayList<ResourceConnector>();
        connectors.add(connector);
        return new DefaultResourceConnectorRegistry(connectors);
    }

    @Override
    public ResourceConnector find(ResourceScheme scheme) {
        if (scheme == null) {
            return null;
        }
        return connectorsByScheme.get(scheme);
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
        return connectorsByScheme.keySet();
    }
}
