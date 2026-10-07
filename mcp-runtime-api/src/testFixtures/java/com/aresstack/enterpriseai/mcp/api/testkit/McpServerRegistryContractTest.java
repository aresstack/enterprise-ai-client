package com.aresstack.enterpriseai.mcp.api.testkit;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Vertragstests für jede Implementierung von {@link McpServerRegistry} zusammen mit ihrer
 * {@link McpToolClientFactory}. Die InProcess-Referenz und der Solon-Transport erben beide davon, damit sie
 * sich nachweislich gleich verhalten. Tool-Aufrufe laufen immer über einen Client, der sich mit
 * {@link McpServerRegistry#endpointUrl(McpEndpointHandle)} verbindet – also über den echten Transport.
 */
public abstract class McpServerRegistryContractTest {

    protected McpServerRegistry registry;
    private final List<McpToolClient> clients = new ArrayList<McpToolClient>();

    /** Eine frische Registry je Test. */
    protected abstract McpServerRegistry createRegistry();

    /** Die Client-Factory, die Endpoint-URLs dieser Registry erreicht. */
    protected abstract McpToolClientFactory clientFactory(McpServerRegistry registry);

    @Before
    public void createRegistryUnderTest() {
        registry = createRegistry();
    }

    @After
    public void shutDownRegistryUnderTest() {
        for (McpToolClient client : clients) {
            client.close();
        }
        if (registry != null) {
            registry.shutdown();
        }
    }

    protected McpToolClient connect(McpEndpointHandle handle) {
        String url = registry.endpointUrl(handle);
        assertNotNull("endpoint url", url);
        McpToolClient client = clientFactory(registry).connect(url, McpToolClientFactory.STREAMABLE_HTTP);
        clients.add(client);
        return client;
    }

    protected McpEndpointHandle register(String endpointId, McpToolContribution... tools) {
        McpEndpointHandle handle = registry.registerEndpoint(new McpEndpointDefinition(endpointId, endpointId));
        registry.updateTools(handle, Arrays.asList(tools));
        return handle;
    }

    private static Map<String, Object> args(String key, Object value) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(key, value);
        return arguments;
    }

    @Test
    public void registeredEndpointReportsItsLiveToolsInOrder() {
        McpEndpointHandle handle = register("contract.tools", McpTestTools.ping(), McpTestTools.echo());

        assertEquals(Arrays.asList("ping", "echo"), registry.toolNames(handle));
        Map<String, String> catalog = registry.toolCatalog(handle);
        assertEquals(Arrays.asList("ping", "echo"), new ArrayList<String>(catalog.keySet()));
        assertEquals(McpTestTools.echo().getDescription(), catalog.get("echo"));
    }

    @Test
    public void endpointUrlCarriesTheTokenInItsPath() {
        McpEndpointHandle handle = register("contract.url", McpTestTools.ping());

        String url = registry.endpointUrl(handle);
        assertNotNull(url);
        assertTrue("token must be part of the endpoint path", url.contains("/" + handle.getToken()));
    }

    @Test
    public void clientListsAndCallsToolsOverTheEndpointUrl() throws Exception {
        McpEndpointHandle handle = register("contract.call",
                McpTestTools.ping(), McpTestTools.echo(), McpTestTools.add());
        McpToolClient client = connect(handle);

        assertEquals(Arrays.asList("ping", "echo", "add"), new ArrayList<String>(client.listTools().keySet()));
        assertEquals("pong", client.callTool("ping", Collections.<String, Object>emptyMap()));
        assertEquals("Grüße – héllo ✓", client.callTool("echo", args("text", "Grüße – héllo ✓")));
        Map<String, Object> summands = args("a", 40);
        summands.put("b", 2);
        assertEquals("42", client.callTool("add", summands));
    }

    @Test
    public void toolsCanBeAddedAndRemovedWhileAClientIsConnected() throws Exception {
        McpEndpointHandle handle = register("contract.dynamic", McpTestTools.ping());
        McpToolClient client = connect(handle);
        assertEquals(Collections.singletonList("ping"), new ArrayList<String>(client.listTools().keySet()));

        registry.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        assertEquals(Arrays.asList("ping", "echo"), new ArrayList<String>(client.listTools().keySet()));
        assertEquals("added", client.callTool("echo", args("text", "added")));

        registry.updateTools(handle, Collections.singletonList(McpTestTools.echo()));
        assertEquals(Collections.singletonList("echo"), new ArrayList<String>(client.listTools().keySet()));
        assertEquals(Collections.singletonList("echo"), registry.toolNames(handle));
        assertToolFailure(client, "ping");

        registry.updateTools(handle, null);
        assertTrue(client.listTools().isEmpty());
        assertTrue(registry.toolNames(handle).isEmpty());
    }

    @Test
    public void unknownToolIsAToolFailureNotAnUnavailableEndpoint() {
        McpEndpointHandle handle = register("contract.unknown", McpTestTools.ping());
        assertToolFailure(connect(handle), "does_not_exist");
    }

    @Test
    public void errorResultOfAHandlerIsAToolFailure() {
        McpEndpointHandle handle = register("contract.error", McpTestTools.fail());
        McpToolCallException failure = assertToolFailure(connect(handle), "fail");
        assertTrue(failure.getMessage(), failure.getMessage().contains(McpTestTools.FAILURE_MESSAGE));
    }

    @Test
    public void throwingHandlerIsContainedAsAToolFailure() throws Exception {
        McpToolContribution exploding = McpToolContribution.of("explode", "throws", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                throw new IllegalStateException("boom");
            }
        });
        McpEndpointHandle handle = register("contract.throw", exploding, McpTestTools.ping());
        McpToolClient client = connect(handle);

        assertToolFailure(client, "explode");
        assertEquals("endpoint keeps serving after a handler failure",
                "pong", client.callTool("ping", Collections.<String, Object>emptyMap()));
    }

    @Test
    public void tokensAreLongRandomAndDistinctPerEndpoint() {
        McpEndpointHandle a = register("contract.a", McpTestTools.ping());
        McpEndpointHandle b = register("contract.b", McpTestTools.ping());

        assertTrue("token must carry at least 128 bit", a.getToken().length() >= 32);
        assertNotEquals(a.getToken(), b.getToken());
        assertNull("token of A must not open B",
                registry.endpointUrl(new McpEndpointHandle("contract.b", a.getToken())));
    }

    @Test
    public void forgedHandleSeesNothingAndChangesNothing() {
        McpEndpointHandle handle = register("contract.forged", McpTestTools.ping());
        McpEndpointHandle forged = new McpEndpointHandle("contract.forged", "deadbeefdeadbeefdeadbeefdeadbeef");

        assertNull(registry.endpointUrl(forged));
        assertTrue(registry.toolNames(forged).isEmpty());
        assertTrue(registry.toolCatalog(forged).isEmpty());
        registry.updateTools(forged, Collections.<McpToolContribution>emptyList());
        registry.unregisterEndpoint(forged);
        registry.unregisterEndpoint(null);
        assertEquals(Collections.singletonList("ping"), registry.toolNames(handle));
        assertNotNull(registry.endpointUrl(handle));
    }

    @Test
    public void clientWithAWrongTokenCannotReachTheEndpoint() {
        McpEndpointHandle handle = register("contract.wrongtoken", McpTestTools.ping());
        String url = registry.endpointUrl(handle);
        String forgedUrl = url.replace(handle.getToken(), "0123456789abcdef0123456789abcdef0123456789abcdef");
        McpToolClient client = clientFactory(registry).connect(forgedUrl, McpToolClientFactory.STREAMABLE_HTTP);
        clients.add(client);

        assertEndpointUnavailable(client, handle.getToken());
    }

    @Test
    public void unregisterInvalidatesTheToken() throws Exception {
        McpEndpointHandle handle = register("contract.unregister", McpTestTools.ping());
        McpToolClient client = connect(handle);
        assertEquals("pong", client.callTool("ping", Collections.<String, Object>emptyMap()));

        registry.unregisterEndpoint(handle);
        registry.unregisterEndpoint(handle); // idempotent

        assertNull(registry.endpointUrl(handle));
        assertTrue(registry.toolNames(handle).isEmpty());
        assertEndpointUnavailable(client, handle.getToken());
    }

    @Test
    public void reRegistrationIssuesAFreshTokenAndRevokesTheOldOne() throws Exception {
        McpEndpointHandle first = register("contract.reregister", McpTestTools.ping());
        McpToolClient oldClient = connect(first);
        McpEndpointHandle second = register("contract.reregister", McpTestTools.echo());

        assertNotEquals(first.getToken(), second.getToken());
        assertNull(registry.endpointUrl(first));
        assertEndpointUnavailable(oldClient, first.getToken());
        assertEquals("fresh", connect(second).callTool("echo", args("text", "fresh")));
    }

    @Test
    public void handleAndToolTypesNeverRevealTheToken() {
        McpEndpointHandle handle = register("contract.secret", McpTestTools.ping());
        assertFalse(handle.toString().contains(handle.getToken()));
    }

    @Test
    public void shutdownIsIdempotentInvalidatesEverythingAndRejectsRegistration() {
        McpEndpointHandle handle = register("contract.shutdown", McpTestTools.ping());
        McpToolClient client = connect(handle);

        registry.shutdown();
        registry.shutdown();

        assertNull(registry.endpointUrl(handle));
        assertTrue(registry.toolNames(handle).isEmpty());
        assertEndpointUnavailable(client, handle.getToken());
        try {
            registry.registerEndpoint(new McpEndpointDefinition("contract.after", "after"));
            fail("registration after shutdown must fail");
        } catch (IllegalStateException expected) {
            // erwartet
        }
    }

    protected static McpToolCallException assertToolFailure(McpToolClient client, String toolName) {
        try {
            String result = client.callTool(toolName, Collections.<String, Object>emptyMap());
            fail("tool '" + toolName + "' must fail, got: " + result);
            return null;
        } catch (McpToolCallException expected) {
            assertFalse("a tool failure must not be reported as unavailable endpoint: " + expected.getMessage(),
                    expected.isEndpointUnavailable());
            return expected;
        }
    }

    protected static void assertEndpointUnavailable(McpToolClient client, String token) {
        try {
            String result = client.callTool("ping", Collections.<String, Object>emptyMap());
            fail("endpoint must not be reachable, got: " + result);
        } catch (McpToolCallException expected) {
            assertTrue("must be reported as unavailable endpoint: " + expected.getMessage(),
                    expected.isEndpointUnavailable());
            assertFalse("exception must not reveal the token",
                    String.valueOf(expected.getMessage()).contains(token));
        }
    }
}
