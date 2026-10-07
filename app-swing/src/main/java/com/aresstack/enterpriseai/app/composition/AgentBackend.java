package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;

/**
 * Die Ports des Agent-Modus: ACP-Verbindung zum Agentenprozess, seine Startbeschreibung und der MCP-Server,
 * auf dem der Agent seinen eigenen Endpoint (mit frischem Token je Prozess, AP21) bekommt. {@code registry}
 * darf {@code null} sein (Agent ohne Werkzeuge).
 */
public final class AgentBackend {

    private final AcpAgentConnector connector;
    private final AgentLaunchSpec launchSpec;
    private final McpServerRegistry registry;
    private final McpEndpointDefinition endpoint;

    public AgentBackend(AcpAgentConnector connector, AgentLaunchSpec launchSpec, McpServerRegistry registry,
                        McpEndpointDefinition endpoint) {
        if (connector == null || launchSpec == null) {
            throw new IllegalArgumentException("connector and launchSpec must not be null");
        }
        if (registry != null && endpoint == null) {
            throw new IllegalArgumentException("endpoint must be given together with the registry");
        }
        this.connector = connector;
        this.launchSpec = launchSpec;
        this.registry = registry;
        this.endpoint = endpoint;
    }

    public AcpAgentConnector connector() {
        return connector;
    }

    public AgentLaunchSpec launchSpec() {
        return launchSpec;
    }

    /** {@code null}: Agent ohne MCP-Endpoint. */
    public McpServerRegistry registry() {
        return registry;
    }

    public McpEndpointDefinition endpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return "AgentBackend[" + launchSpec.getCommand() + (registry == null ? "" : ", mcp=" + endpoint.getEndpointId()) + "]";
    }
}
