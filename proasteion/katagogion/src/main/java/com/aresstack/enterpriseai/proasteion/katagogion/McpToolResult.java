package com.aresstack.enterpriseai.proasteion.katagogion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Tool-Ergebnis: kurzer, strukturierter Text für das Modell (nie rohes HTML/Binärdaten) oder ein Fehler.
 * Handler liefern Fehler als Ergebnis und werfen nicht über die MCP-Grenze.
 *
 * <p>A successful result may name the knowledge resources whose content it handed to the model
 * ({@link #contextResources()}); in-process callers record them as provenance of the answer, the MCP wire carries
 * only the text.
 *
 * <p>Herkunft: askai-java8 {@code McpToolResult}.
 */
public final class McpToolResult {

    private final boolean error;
    private final String text;
    private final List<McpContextResource> contextResources;

    private McpToolResult(boolean error, String text, List<McpContextResource> contextResources) {
        this.error = error;
        this.text = text == null ? "" : text;
        this.contextResources = contextResources;
    }

    public static McpToolResult ok(String text) {
        return new McpToolResult(false, text, Collections.<McpContextResource>emptyList());
    }

    /** A successful result that handed the content of {@code contextResources} to the model. */
    public static McpToolResult ok(String text, List<McpContextResource> contextResources) {
        List<McpContextResource> copy = new ArrayList<McpContextResource>();
        if (contextResources != null) {
            for (McpContextResource resource : contextResources) {
                if (resource == null) {
                    throw new IllegalArgumentException("contextResources must not contain null");
                }
                copy.add(resource);
            }
        }
        return new McpToolResult(false, text, Collections.unmodifiableList(copy));
    }

    public static McpToolResult error(String message) {
        return new McpToolResult(true, message, Collections.<McpContextResource>emptyList());
    }

    /** The knowledge resources whose content this result delivered as context; empty for errors. */
    public List<McpContextResource> contextResources() {
        return contextResources;
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
