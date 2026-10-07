package com.aresstack.enterpriseai.mcp.api;

/**
 * Beschreibt einen zu registrierenden logischen MCP-Endpoint: stabile ID und Anzeigename.
 *
 * <p>Herkunft: askai-java8 {@code McpEndpointDefinition}.
 */
public final class McpEndpointDefinition {

    private final String endpointId;
    private final String displayName;

    public McpEndpointDefinition(String endpointId, String displayName) {
        if (endpointId == null || endpointId.trim().isEmpty()) {
            throw new IllegalArgumentException("endpointId must not be empty");
        }
        this.endpointId = endpointId.trim();
        this.displayName = displayName == null || displayName.trim().isEmpty() ? this.endpointId : displayName;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "McpEndpointDefinition[" + endpointId + "]";
    }
}
