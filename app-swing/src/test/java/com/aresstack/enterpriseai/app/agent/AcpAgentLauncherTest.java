package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpEndpointDescriptor;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpToolClientFactory;
import com.aresstack.enterpriseai.mcp.api.testkit.McpTestTools;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Der Launcher legt je Agentenprozess einen MCP-Endpoint an und gibt ihn mit der Verbindung wieder frei. */
public class AcpAgentLauncherTest {

    private static final McpEndpointDefinition ENDPOINT = new McpEndpointDefinition("agent-tools", "Agent-Tools");
    private static final AgentLaunchSpec BASE = new AgentLaunchSpec("agent",
            Arrays.asList("--stdio"), Collections.singletonMap("AGENT_MODE", "demo"));

    private final InProcessMcpServerRegistry registry = new InProcessMcpServerRegistry();

    /** Merkt sich die Launch-Spezifikation und liefert eine Verbindung ohne Prozess. */
    private static final class RecordingConnector implements AcpAgentConnector {
        AgentLaunchSpec spec;
        AcpException failure;
        boolean closed;

        @Override
        public AcpConnection connect(AgentLaunchSpec launchSpec) throws AcpException {
            spec = launchSpec;
            if (failure != null) {
                throw failure;
            }
            return new AcpConnection() {
                @Override
                public AcpConnectionState getState() {
                    return closed ? AcpConnectionState.CLOSED : AcpConnectionState.READY;
                }

                @Override
                public AgentProcessHandle getProcess() {
                    return null;
                }

                @Override
                public AcpSession newSession() {
                    return null;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }

    @Test
    public void agentGetsAWorkingToolEndpointThroughItsEnvironment() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        AcpAgentLauncher launcher = new AcpAgentLauncher(connector, BASE, registry, ENDPOINT,
                Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));

        AcpConnection connection = launcher.launch();

        Map<String, String> env = connector.spec.getEnv();
        assertEquals("demo", env.get("AGENT_MODE"));
        assertEquals("agent", connector.spec.getCommand());
        assertEquals(Collections.singletonList("--stdio"), connector.spec.getArgs());
        AcpEndpointDescriptor endpoint = AgentMcpEnvironment.read(env);
        assertEquals("agent-tools", endpoint.getEndpointId());
        assertEquals(McpToolClientFactory.STREAMABLE_HTTP, endpoint.getTransport());
        assertFalse(endpoint.getToken().isEmpty());
        assertFalse("launch spec must not print the token", connector.spec.toString().contains(endpoint.getToken()));
        assertFalse(connector.spec.toString().contains(endpoint.getUrl()));

        McpToolClient client = new InProcessMcpToolClientFactory(registry).connect(endpoint.getUrl(),
                endpoint.getTransport());
        assertEquals(Arrays.asList("ping", "echo"), new java.util.ArrayList<String>(client.listTools().keySet()));
        assertEquals("hallo", client.callTool("echo", Collections.<String, Object>singletonMap("text", "hallo")));

        connection.close();
        assertTrue(connector.closed);
        assertTrue("endpoint is gone with the agent", registry.toolNames(handleOf(endpoint)).isEmpty());
        assertNull(registry.endpointUrl(handleOf(endpoint)));
        connection.close(); // idempotent
    }

    @Test
    public void everyLaunchGetsAFreshToken() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        AcpAgentLauncher launcher = new AcpAgentLauncher(connector, BASE, registry, ENDPOINT,
                Collections.singletonList(McpTestTools.ping()));
        launcher.launch().close();
        String first = AgentMcpEnvironment.read(connector.spec.getEnv()).getToken();
        launcher.launch();
        String second = AgentMcpEnvironment.read(connector.spec.getEnv()).getToken();
        assertNotEquals(first, second);
    }

    @Test
    public void failedStartReleasesTheEndpointAtOnce() {
        RecordingConnector connector = new RecordingConnector();
        connector.failure = new AcpException(AcpException.Phase.SPAWN, "no agent", null);
        AcpAgentLauncher launcher = new AcpAgentLauncher(connector, BASE, registry, ENDPOINT,
                Collections.singletonList(McpTestTools.ping()));
        try {
            launcher.launch();
            fail("connect failure must propagate");
        } catch (AcpException expected) {
            assertEquals(AcpException.Phase.SPAWN, expected.getPhase());
        }
        AcpEndpointDescriptor endpoint = AgentMcpEnvironment.read(connector.spec.getEnv());
        assertNull("token invalid after failed start", registry.endpointUrl(handleOf(endpoint)));
    }

    @Test
    public void failingToolUpdateReleasesTheEndpoint() {
        final java.util.List<McpEndpointHandle> registered = new java.util.ArrayList<McpEndpointHandle>();
        McpServerRegistry rejecting = new McpServerRegistry() {
            @Override
            public McpEndpointHandle registerEndpoint(McpEndpointDefinition definition) {
                McpEndpointHandle handle = registry.registerEndpoint(definition);
                registered.add(handle);
                return handle;
            }

            @Override
            public void updateTools(McpEndpointHandle handle, java.util.Collection<McpToolContribution> tools) {
                throw new IllegalStateException("tool update rejected");
            }

            @Override
            public void unregisterEndpoint(McpEndpointHandle handle) {
                registry.unregisterEndpoint(handle);
            }

            @Override
            public String endpointUrl(McpEndpointHandle handle) {
                return registry.endpointUrl(handle);
            }

            @Override
            public java.util.List<String> toolNames(McpEndpointHandle handle) {
                return registry.toolNames(handle);
            }

            @Override
            public Map<String, String> toolCatalog(McpEndpointHandle handle) {
                return registry.toolCatalog(handle);
            }

            @Override
            public void shutdown() {
                registry.shutdown();
            }
        };
        RecordingConnector connector = new RecordingConnector();
        AcpAgentLauncher launcher = new AcpAgentLauncher(connector, BASE, rejecting, ENDPOINT,
                Collections.singletonList(McpTestTools.ping()));
        try {
            launcher.launch();
            fail();
        } catch (AcpException expected) {
            assertEquals(AcpException.Phase.SPAWN, expected.getPhase());
        }
        assertNull("no agent without its tools", connector.spec);
        assertEquals(1, registered.size());
        assertNull("endpoint released after the failed tool update", registry.endpointUrl(registered.get(0)));
    }

    @Test
    public void shutDownRegistryIsAClassifiedStartFailure() {
        registry.shutdown();
        RecordingConnector connector = new RecordingConnector();
        AcpAgentLauncher launcher = new AcpAgentLauncher(connector, BASE, registry, ENDPOINT, null);
        try {
            launcher.launch();
            fail();
        } catch (AcpException expected) {
            assertEquals(AcpException.Phase.SPAWN, expected.getPhase());
        }
        assertNull("no agent without its endpoint", connector.spec);
    }

    @Test
    public void withoutMcpTheAgentGetsOnlyTheBaseEnvironment() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        new AcpAgentLauncher(connector, BASE).launch();
        assertEquals(BASE.getEnv(), connector.spec.getEnv());
        assertNull(AgentMcpEnvironment.read(connector.spec.getEnv()));
    }

    private static McpEndpointHandle handleOf(AcpEndpointDescriptor endpoint) {
        return new McpEndpointHandle(endpoint.getEndpointId(), endpoint.getToken());
    }
}
