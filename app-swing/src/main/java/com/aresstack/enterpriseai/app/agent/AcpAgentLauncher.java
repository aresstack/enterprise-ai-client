package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpEndpointDescriptor;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.application.agent.AgentLauncher;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Startet den Agenten über den ACP-Port und gibt ihm optional einen eigenen MCP-Endpoint mit.
 *
 * <p>Je Agentenprozess wird der Endpoint frisch registriert (neuer, zufälliger Token), mit den Tools bestückt
 * und als {@link AcpEndpointDescriptor} über {@link AgentMcpEnvironment} in die Launch-Umgebung gelegt. Die
 * gelieferte Verbindung meldet den Endpoint beim Schließen wieder ab; damit ist der Token ungültig, sobald
 * der Agent-Modus oder der Prozess endet. Scheitert der Start, wird der Endpoint sofort abgemeldet.
 *
 * <p>Die Umgebung des Agenten besteht nur aus den Variablen der Basis-Spezifikation plus dem MCP-Endpoint.
 * Welche Tools der Agent bekommt, entscheidet die Composition Root; Tool-Handler rufen nur Use Cases (AP20).
 */
public final class AcpAgentLauncher implements AgentLauncher {

    private final AcpAgentConnector connector;
    private final AgentLaunchSpec baseSpec;
    private final McpServerRegistry registry;
    private final McpEndpointDefinition endpoint;
    private final List<McpToolContribution> tools;

    /** Agent ohne MCP-Endpoint. */
    public AcpAgentLauncher(AcpAgentConnector connector, AgentLaunchSpec baseSpec) {
        this(connector, baseSpec, null, null, null);
    }

    /**
     * @param registry MCP-Server-Port, über den der Endpoint bereitgestellt wird ({@code null}: ohne MCP)
     * @param endpoint stabile ID und Anzeigename des Endpoints
     * @param tools    die Tools, die der Agent dort sieht
     */
    public AcpAgentLauncher(AcpAgentConnector connector, AgentLaunchSpec baseSpec, McpServerRegistry registry,
                            McpEndpointDefinition endpoint, Collection<McpToolContribution> tools) {
        if (connector == null || baseSpec == null) {
            throw new IllegalArgumentException("connector and baseSpec must not be null");
        }
        if (registry != null && endpoint == null) {
            throw new IllegalArgumentException("endpoint must be given together with the registry");
        }
        this.connector = connector;
        this.baseSpec = baseSpec;
        this.registry = registry;
        this.endpoint = endpoint;
        this.tools = Collections.unmodifiableList(new ArrayList<McpToolContribution>(
                tools == null ? Collections.<McpToolContribution>emptyList() : tools));
    }

    @Override
    public AcpConnection launch() throws AcpException {
        Map<String, String> env = new LinkedHashMap<String, String>(baseSpec.getEnv());
        McpEndpointHandle handle = null;
        if (registry != null) {
            handle = provideEndpoint(env);
        }
        AgentLaunchSpec spec = new AgentLaunchSpec(baseSpec.getCommand(), baseSpec.getArgs(), env);
        AcpConnection connection;
        try {
            connection = connector.connect(spec);
        } catch (AcpException e) {
            release(handle);
            throw e;
        } catch (RuntimeException e) {
            release(handle);
            throw e;
        }
        return handle == null ? connection : new EndpointBoundConnection(connection, registry, handle);
    }

    private McpEndpointHandle provideEndpoint(Map<String, String> env) throws AcpException {
        McpEndpointHandle handle;
        try {
            handle = registry.registerEndpoint(endpoint);
        } catch (RuntimeException e) {
            // z. B. Registry schon heruntergefahren; die Ursache trägt keinen Token
            throw new AcpException(AcpException.Phase.SPAWN, "MCP endpoint for the agent is not available", e);
        }
        String url;
        try {
            registry.updateTools(handle, tools);
            url = registry.endpointUrl(handle);
        } catch (RuntimeException e) {
            release(handle);
            throw new AcpException(AcpException.Phase.SPAWN, "MCP tools for the agent could not be provided", e);
        }
        if (url == null) {
            release(handle);
            throw new AcpException(AcpException.Phase.SPAWN, "MCP endpoint for the agent is not available", null);
        }
        AgentMcpEnvironment.put(env, new AcpEndpointDescriptor(handle.getEndpointId(), url,
                McpToolClientFactory.STREAMABLE_HTTP, handle.getToken()));
        return handle;
    }

    private void release(McpEndpointHandle handle) {
        if (handle != null) {
            registry.unregisterEndpoint(handle);
        }
    }

    /** Die Verbindung des Agenten; Schließen meldet zusätzlich seinen MCP-Endpoint ab (Token ungültig). */
    private static final class EndpointBoundConnection implements AcpConnection {

        private final AcpConnection delegate;
        private final McpServerRegistry registry;
        private final McpEndpointHandle handle;

        EndpointBoundConnection(AcpConnection delegate, McpServerRegistry registry, McpEndpointHandle handle) {
            this.delegate = delegate;
            this.registry = registry;
            this.handle = handle;
        }

        @Override
        public AcpConnectionState getState() {
            return delegate.getState();
        }

        @Override
        public AgentProcessHandle getProcess() {
            return delegate.getProcess();
        }

        @Override
        public AcpSession newSession() throws AcpException {
            return delegate.newSession();
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } finally {
                registry.unregisterEndpoint(handle); // idempotent
            }
        }
    }
}
