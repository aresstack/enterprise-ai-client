package com.aresstack.enterpriseai.mcp.api;

import java.util.Map;

/**
 * Neutraler Client-Port zum Aufruf von Tools auf einem (entfernten) MCP-Endpoint, ohne Solon-/MCP-SDK-Typen.
 * Die Solon-Implementierung liegt in {@code mcp-solon-runtime}.
 *
 * <p>Herkunft: askai-java8 {@code McpToolClient}; ergänzt um {@link #listTools()}.
 */
public interface McpToolClient {

    /**
     * Die aktuell angebotenen Tools als Name → Beschreibung (frisch vom Endpoint, kein Cache).
     *
     * @throws McpToolCallException wenn der Endpoint nicht erreichbar ist
     */
    Map<String, String> listTools() throws McpToolCallException;

    /**
     * @return das Text-Ergebnis des Tools
     * @throws McpToolCallException bei einem Tool-Fehler oder nicht erreichbarem Endpoint
     */
    String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException;

    /** Gibt Verbindung und Threads frei. Idempotent. */
    void close();
}
