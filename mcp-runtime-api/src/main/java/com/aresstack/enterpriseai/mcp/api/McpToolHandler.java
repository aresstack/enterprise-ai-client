package com.aresstack.enterpriseai.mcp.api;

/**
 * Führt einen Tool-Aufruf aus. Implementierungen liefern Fehler als {@link McpToolResult#error(String)} und
 * werfen nicht über die MCP-Grenze; eine Runtime fängt dennoch geworfene Exceptions ab.
 *
 * <p>Nach den Projektregeln rufen Handler ausschließlich Application-Use-Cases auf, nie Adapter.
 *
 * <p>Herkunft: askai-java8 {@code McpToolHandler}.
 */
public interface McpToolHandler {

    McpToolResult invoke(McpToolCall call);
}
