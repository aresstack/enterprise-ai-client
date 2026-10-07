package com.aresstack.enterpriseai.mcp.api.testkit;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transportfreie Referenzimplementierung von {@link McpServerRegistry}: Endpoints, Tokens und Tool-Mengen
 * leben in der JVM, Aufrufe werden direkt dispatcht. Kein MCP-Protokoll (kein Wire-Format); dient dazu,
 * Tool-Logik (z. B. AP20) ohne Netzwerk zu testen. Clients bekommt man über
 * {@link InProcessMcpToolClientFactory}; Endpoint-URLs haben die Form {@code inprocess://<url-kodierte id>/<token>}.
 *
 * <p>Herkunft: askai-java8 {@code InProcessMcpServerRegistry}; Zufallsquelle per Konstruktor statt statisch.
 */
public final class InProcessMcpServerRegistry implements McpServerRegistry {

    /** Generische Meldung, wenn ein Handler eine Exception wirft. */
    public static final String TOOL_FAILED = "Tool failed.";

    /** URL-Schema der InProcess-Endpoints. */
    public static final String SCHEME = "inprocess://";

    /** Wird bei jeder Änderung einer Tool-Menge benachrichtigt (ein Transport meldet das als tools/list_changed). */
    public interface ToolsChangedListener {
        void onToolsChanged(String endpointId);
    }

    private final SecureRandom random;
    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<String, Endpoint>();
    private final CopyOnWriteArrayList<ToolsChangedListener> listeners =
            new CopyOnWriteArrayList<ToolsChangedListener>();
    private volatile boolean shutdown;

    public InProcessMcpServerRegistry() {
        this(new SecureRandom());
    }

    public InProcessMcpServerRegistry(SecureRandom random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.random = random;
    }

    @Override
    public synchronized McpEndpointHandle registerEndpoint(McpEndpointDefinition definition) {
        if (shutdown) {
            throw new IllegalStateException("registry is shut down");
        }
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        String token = newToken();
        endpoints.put(definition.getEndpointId(), new Endpoint(definition, token));
        return new McpEndpointHandle(definition.getEndpointId(), token);
    }

    @Override
    public void updateTools(McpEndpointHandle handle, Collection<McpToolContribution> tools) {
        Endpoint endpoint = authorized(handle);
        if (endpoint == null) {
            return;
        }
        Map<String, McpToolContribution> next = new LinkedHashMap<String, McpToolContribution>();
        if (tools != null) {
            for (McpToolContribution tool : tools) {
                next.put(tool.getName(), tool);
            }
        }
        endpoint.tools = Collections.unmodifiableMap(next);
        for (ToolsChangedListener listener : listeners) {
            listener.onToolsChanged(endpoint.definition.getEndpointId());
        }
    }

    @Override
    public synchronized void unregisterEndpoint(McpEndpointHandle handle) {
        if (authorized(handle) != null) {
            endpoints.remove(handle.getEndpointId());
        }
    }

    @Override
    public String endpointUrl(McpEndpointHandle handle) {
        Endpoint endpoint = authorized(handle);
        return endpoint == null ? null : SCHEME + encode(endpoint.definition.getEndpointId()) + "/" + endpoint.token;
    }

    @Override
    public List<String> toolNames(McpEndpointHandle handle) {
        Endpoint endpoint = authorized(handle);
        return endpoint == null ? new ArrayList<String>() : new ArrayList<String>(endpoint.tools.keySet());
    }

    @Override
    public Map<String, String> toolCatalog(McpEndpointHandle handle) {
        Map<String, String> catalog = new LinkedHashMap<String, String>();
        Endpoint endpoint = authorized(handle);
        if (endpoint != null) {
            for (McpToolContribution tool : endpoint.tools.values()) {
                catalog.put(tool.getName(), tool.getDescription());
            }
        }
        return catalog;
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
        endpoints.clear();
        listeners.clear();
    }

    public void addToolsChangedListener(ToolsChangedListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public boolean isEndpointRegistered(String endpointId) {
        return endpointId != null && endpoints.containsKey(endpointId);
    }

    /** @return ob ID und Token zu einem registrierten Endpoint gehören. */
    public boolean isAuthorized(String endpointId, String token) {
        return lookup(endpointId, token) != null;
    }

    /** Aktuelle Tools als Name → Beschreibung; leer bei falschem Token oder unbekanntem Endpoint. */
    public Map<String, String> listTools(String endpointId, String token) {
        Map<String, String> tools = catalogOrNull(endpointId, token);
        return tools == null ? new LinkedHashMap<String, String>() : tools;
    }

    /** Katalog in einem Schritt mit der Autorisierung; {@code null}, wenn der Endpoint nicht erreichbar ist. */
    Map<String, String> catalogOrNull(String endpointId, String token) {
        Endpoint endpoint = lookup(endpointId, token);
        if (endpoint == null) {
            return null;
        }
        Map<String, String> catalog = new LinkedHashMap<String, String>();
        for (McpToolContribution tool : endpoint.tools.values()) {
            catalog.put(tool.getName(), tool.getDescription());
        }
        return catalog;
    }

    static String encode(String endpointId) {
        try {
            return java.net.URLEncoder.encode(endpointId, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String decode(String encodedEndpointId) {
        try {
            return java.net.URLDecoder.decode(encodedEndpointId, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Dispatcht einen Tool-Aufruf. Falscher Token, unbekannter Endpoint oder unbekanntes Tool ergeben ein
     * Fehler-Ergebnis (kein Throw); eine Exception des Handlers wird ebenfalls zum Fehler-Ergebnis.
     */
    public McpToolResult invoke(String endpointId, String token, McpToolCall call) {
        McpToolResult result = dispatch(endpointId, token, call);
        return result == null ? McpToolResult.error("Unknown endpoint or invalid token.") : result;
    }

    /**
     * Wie {@link #invoke(String, String, McpToolCall)}, aber Autorisierung und Aufruf in einem Schritt:
     * {@code null}, wenn der Endpoint nicht (mehr) erreichbar ist.
     */
    McpToolResult dispatch(String endpointId, String token, McpToolCall call) {
        Endpoint endpoint = lookup(endpointId, token);
        if (endpoint == null) {
            return null;
        }
        McpToolContribution tool = endpoint.tools.get(call.getToolName());
        if (tool == null) {
            return McpToolResult.error("Unknown tool: " + call.getToolName());
        }
        try {
            McpToolResult result = tool.getHandler().invoke(call);
            return result == null ? McpToolResult.error("Tool returned no result.") : result;
        } catch (RuntimeException ex) {
            // Generisch: die Meldung kann Anfragedaten oder Secrets enthalten.
            return McpToolResult.error(TOOL_FAILED);
        }
    }

    private Endpoint authorized(McpEndpointHandle handle) {
        return handle == null ? null : lookup(handle.getEndpointId(), handle.getToken());
    }

    private Endpoint lookup(String endpointId, String token) {
        if (shutdown || endpointId == null || token == null) {
            return null;
        }
        Endpoint endpoint = endpoints.get(endpointId);
        return endpoint != null && constantTimeEquals(endpoint.token, token) ? endpoint : null;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static final class Endpoint {
        private final McpEndpointDefinition definition;
        private final String token;
        private volatile Map<String, McpToolContribution> tools = Collections.emptyMap();

        private Endpoint(McpEndpointDefinition definition, String token) {
            this.definition = definition;
            this.token = token;
        }
    }
}
