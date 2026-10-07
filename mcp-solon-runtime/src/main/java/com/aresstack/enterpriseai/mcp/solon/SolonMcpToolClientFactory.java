package com.aresstack.enterpriseai.mcp.solon;

import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link McpToolClientFactory} über den echten Solon-MCP-Client (Streamable HTTP).
 *
 * <p>Fehlerabbildung: ein Ergebnis mit {@code isError} oder die JSON-RPC-Fehlerantwort "Invalid params" des
 * Servers (unbekanntes Tool, ungültige Argumente) ist ein Tool-Fehler; alles andere (Verbindung, Zeitüberschreitung, HTTP-Fehler wie 404
 * für einen falschen oder ungültig gewordenen Token) gilt als nicht erreichbarer Endpoint. Die URL trägt den
 * Token und wird weder geloggt noch in Exception-Meldungen übernommen.
 *
 * <p>Herkunft: askai-java8 {@code SolonMcpToolClientFactory}; geändert: strukturierte Fehlerabbildung über
 * {@code CallToolResult.isError()} statt Textpräfixen, {@code listTools()}, Bereinigung von Meldungen.
 */
public final class SolonMcpToolClientFactory implements McpToolClientFactory {

    private final Duration initializationTimeout;
    private final Duration requestTimeout;

    /** Standard: 15 s Initialisierung, 60 s je Anfrage. */
    public SolonMcpToolClientFactory() {
        this(Duration.ofSeconds(15), Duration.ofSeconds(60));
    }

    public SolonMcpToolClientFactory(Duration initializationTimeout, Duration requestTimeout) {
        if (initializationTimeout == null || requestTimeout == null
                || initializationTimeout.isNegative() || initializationTimeout.isZero()
                || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        this.initializationTimeout = initializationTimeout;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public McpToolClient connect(String url, String transport) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        McpClientProvider client = McpClientProvider.builder()
                .url(url)
                .channel(transport == null || transport.trim().isEmpty() ? STREAMABLE_HTTP : transport)
                .cacheSeconds(0)
                .initializationTimeout(initializationTimeout)
                .requestTimeout(requestTimeout)
                .build();
        return new SolonMcpToolClient(client, url);
    }

    /** JSON-RPC "Invalid params": so meldet der MCP-Server ein unbekanntes Tool oder ungültige Argumente. */
    static final int JSON_RPC_INVALID_PARAMS = -32602;

    /**
     * Ein Tool-Fehler liegt nur vor, wenn der Server mit einer JSON-RPC-Fehlerantwort "Invalid params"
     * geantwortet hat. Das MCP-SDK verwendet {@link McpError} auch für Transport-, Parse- und
     * Protokollfehler; die gelten – wie jeder andere Fehler – als nicht erreichbarer Endpoint.
     */
    static boolean isToolFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof McpError) {
                McpSchema.JSONRPCResponse.JSONRPCError error = ((McpError) cause).getJsonRpcError();
                return error != null && error.code() != null && error.code() == JSON_RPC_INVALID_PARAMS;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static final class SolonMcpToolClient implements McpToolClient {

        private final McpClientProvider client;
        private final String url;
        private volatile boolean closed;

        private SolonMcpToolClient(McpClientProvider client, String url) {
            this.client = client;
            this.url = url;
        }

        @Override
        public Map<String, String> listTools() throws McpToolCallException {
            ensureOpen();
            try {
                Map<String, String> tools = new LinkedHashMap<String, String>();
                for (FunctionTool tool : client.getTools()) {
                    tools.put(tool.name(), tool.description());
                }
                return tools;
            } catch (RuntimeException ex) {
                throw translate(ex);
            }
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
            ensureOpen();
            McpSchema.CallToolResult result;
            try {
                result = client.callToolRequest(toolName,
                        arguments == null ? new LinkedHashMap<String, Object>() : arguments);
            } catch (RuntimeException ex) {
                throw translate(ex);
            }
            String text = text(result);
            if (Boolean.TRUE.equals(result.isError())) {
                throw new McpToolCallException(sanitize(text), false);
            }
            return text;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }

        private void ensureOpen() throws McpToolCallException {
            if (closed) {
                throw new McpToolCallException("MCP client is closed", true);
            }
        }

        private McpToolCallException translate(RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            // Bewusst ohne Cause: Stacktraces und Meldungen der Bibliothek können die URL samt Token enthalten.
            return new McpToolCallException(sanitize(message), !isToolFailure(ex));
        }

        private String sanitize(String message) {
            if (message == null) {
                return "";
            }
            String clean = message.replace(url, "<mcp-endpoint>");
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                clean = clean.replace(url.substring(lastSlash + 1), "***");
            }
            return clean;
        }

        private static String text(McpSchema.CallToolResult result) {
            StringBuilder text = new StringBuilder();
            if (result.content() != null) {
                for (McpSchema.Content content : result.content()) {
                    if (content instanceof McpSchema.TextContent) {
                        text.append(((McpSchema.TextContent) content).text());
                    }
                }
            }
            return text.toString();
        }
    }
}
