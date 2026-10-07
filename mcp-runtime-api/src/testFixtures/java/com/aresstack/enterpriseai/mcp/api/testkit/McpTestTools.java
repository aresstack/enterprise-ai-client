package com.aresstack.enterpriseai.mcp.api.testkit;

import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

/**
 * Neutrale Test-Tools: {@code ping()}, {@code echo(text)}, {@code add(a, b)} und das immer fehlschlagende
 * {@code fail()}.
 *
 * <p>Herkunft: askai-java8 {@code McpTestTools} (ping, echo); add und fail neu.
 */
public final class McpTestTools {

    /** Fehlermeldung von {@link #fail()}. */
    public static final String FAILURE_MESSAGE = "deliberate failure";

    private McpTestTools() {
    }

    public static McpToolContribution ping() {
        return McpToolContribution.of("ping", "Liveness check; returns \"pong\".", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                return McpToolResult.ok("pong");
            }
        });
    }

    public static McpToolContribution echo() {
        return McpToolContribution.of("echo", "Echoes the given text back.", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                String text = call.getString("text");
                return McpToolResult.ok(text == null ? "" : text);
            }
        }, McpToolParameter.string("text", true, "The text to echo"));
    }

    public static McpToolContribution add() {
        return McpToolContribution.of("add", "Adds two integers.", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                return McpToolResult.ok(String.valueOf(call.getInteger("a", 0) + call.getInteger("b", 0)));
            }
        }, McpToolParameter.integer("a", true, "First summand"), McpToolParameter.integer("b", true, "Second summand"));
    }

    public static McpToolContribution fail() {
        return McpToolContribution.of("fail", "Always reports an error.", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                return McpToolResult.error(FAILURE_MESSAGE);
            }
        });
    }
}
