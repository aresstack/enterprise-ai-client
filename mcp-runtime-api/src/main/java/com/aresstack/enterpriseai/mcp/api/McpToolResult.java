package com.aresstack.enterpriseai.mcp.api;

/**
 * Ein Tool-Ergebnis: kurzer, strukturierter Text für das Modell (nie rohes HTML/Binärdaten) oder ein Fehler.
 * Handler liefern Fehler als Ergebnis und werfen nicht über die MCP-Grenze.
 *
 * <p>Herkunft: askai-java8 {@code McpToolResult}.
 */
public final class McpToolResult {

    private final boolean error;
    private final String text;

    private McpToolResult(boolean error, String text) {
        this.error = error;
        this.text = text == null ? "" : text;
    }

    public static McpToolResult ok(String text) {
        return new McpToolResult(false, text);
    }

    public static McpToolResult error(String message) {
        return new McpToolResult(true, message);
    }

    public boolean isError() {
        return error;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "McpToolResult[" + (error ? "error" : "ok") + ", " + text.length() + " chars]";
    }
}
