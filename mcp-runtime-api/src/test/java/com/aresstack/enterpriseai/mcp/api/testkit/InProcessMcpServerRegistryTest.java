package com.aresstack.enterpriseai.mcp.api.testkit;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Die InProcess-spezifischen Teile: Tools-Changed-Benachrichtigung und direkter Dispatch.
 * Herkunft: askai-java8 {@code InProcessMcpServerRegistryTest}.
 */
public class InProcessMcpServerRegistryTest {

    private static McpToolCall call(String tool, String key, Object value) {
        Map<String, Object> args = new HashMap<String, Object>();
        if (key != null) {
            args.put(key, value);
        }
        return new McpToolCall(tool, args);
    }

    @Test
    public void everyToolSetChangeIsAnnounced() {
        InProcessMcpServerRegistry registry = new InProcessMcpServerRegistry();
        final List<String> changes = new ArrayList<String>();
        registry.addToolsChangedListener(new InProcessMcpServerRegistry.ToolsChangedListener() {
            @Override
            public void onToolsChanged(String endpointId) {
                changes.add(endpointId);
            }
        });
        McpEndpointHandle handle = registry.registerEndpoint(new McpEndpointDefinition("test.endpoint", "Test"));
        registry.updateTools(handle, Collections.singletonList(McpTestTools.ping()));
        registry.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        registry.updateTools(handle, Collections.<McpToolContribution>emptyList());
        registry.updateTools(new McpEndpointHandle("test.endpoint", "forged"), null);

        assertEquals(Arrays.asList("test.endpoint", "test.endpoint", "test.endpoint"), changes);
    }

    @Test
    public void invokeDispatchesDirectlyAndRejectsBadTokensAndUnknownTools() {
        InProcessMcpServerRegistry registry = new InProcessMcpServerRegistry();
        McpEndpointHandle handle = registry.registerEndpoint(new McpEndpointDefinition("test.endpoint", "Test"));
        registry.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));

        McpToolResult echoed = registry.invoke("test.endpoint", handle.getToken(), call("echo", "text", "hi"));
        assertFalse(echoed.isError());
        assertEquals("hi", echoed.getText());
        assertTrue(registry.invoke("test.endpoint", "wrong-token", call("ping", null, null)).isError());
        assertTrue(registry.invoke("test.endpoint", handle.getToken(), call("nope", null, null)).isError());
        assertTrue(registry.listTools("test.endpoint", "wrong-token").isEmpty());
    }

    @Test
    public void handlerReturningNullBecomesAnError() {
        InProcessMcpServerRegistry registry = new InProcessMcpServerRegistry();
        McpEndpointHandle handle = registry.registerEndpoint(new McpEndpointDefinition("test.endpoint", "Test"));
        registry.updateTools(handle, Collections.singletonList(McpToolContribution.of("null", "returns null",
                new com.aresstack.enterpriseai.mcp.api.McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return null;
                    }
                })));
        assertTrue(registry.invoke("test.endpoint", handle.getToken(), call("null", null, null)).isError());
    }

    @Test
    public void registrationStateIsVisible() {
        InProcessMcpServerRegistry registry = new InProcessMcpServerRegistry();
        McpEndpointHandle handle = registry.registerEndpoint(new McpEndpointDefinition("test.endpoint", "Test"));
        assertTrue(registry.isEndpointRegistered("test.endpoint"));
        registry.unregisterEndpoint(handle);
        assertFalse(registry.isEndpointRegistered("test.endpoint"));
    }
}
