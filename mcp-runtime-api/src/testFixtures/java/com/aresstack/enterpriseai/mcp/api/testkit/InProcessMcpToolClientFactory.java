package com.aresstack.enterpriseai.mcp.api.testkit;

import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import java.util.Map;

/**
 * {@link McpToolClientFactory} für {@code inprocess://<id>/<token>}-URLs einer
 * {@link InProcessMcpServerRegistry}. Ein ungültiger Token oder ein abgemeldeter Endpoint gilt – wie beim
 * echten Transport – als nicht erreichbarer Endpoint.
 */
public final class InProcessMcpToolClientFactory implements McpToolClientFactory {

    private final InProcessMcpServerRegistry registry;

    public InProcessMcpToolClientFactory(InProcessMcpServerRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    @Override
    public McpToolClient connect(String url, String transport) {
        if (url == null || !url.startsWith(InProcessMcpServerRegistry.SCHEME)) {
            throw new IllegalArgumentException("not an in-process MCP endpoint URL");
        }
        String rest = url.substring(InProcessMcpServerRegistry.SCHEME.length());
        int slash = rest.lastIndexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw new IllegalArgumentException("malformed in-process MCP endpoint URL");
        }
        return new Client(registry, rest.substring(0, slash), rest.substring(slash + 1));
    }

    private static final class Client implements McpToolClient {
        private final InProcessMcpServerRegistry registry;
        private final String endpointId;
        private final String token;
        private volatile boolean closed;

        private Client(InProcessMcpServerRegistry registry, String endpointId, String token) {
            this.registry = registry;
            this.endpointId = endpointId;
            this.token = token;
        }

        @Override
        public Map<String, String> listTools() throws McpToolCallException {
            ensureReachable();
            return registry.listTools(endpointId, token);
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
            ensureReachable();
            McpToolResult result = registry.invoke(endpointId, token, new McpToolCall(toolName, arguments));
            if (result.isError()) {
                throw new McpToolCallException(result.getText(), false);
            }
            return result.getText();
        }

        @Override
        public void close() {
            closed = true;
        }

        private void ensureReachable() throws McpToolCallException {
            if (closed) {
                throw new McpToolCallException("MCP client is closed", true);
            }
            if (!registry.isAuthorized(endpointId, token)) {
                throw new McpToolCallException("MCP endpoint not available", true);
            }
        }
    }
}
