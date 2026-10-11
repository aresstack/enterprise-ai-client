package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolArguments;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein MCP-Werkzeug als Werkzeug des normalen Chats ({@link AiTool}): dieselbe Beschreibung, dasselbe Schema, derselbe
 * Handler. So gibt es {@code knowledge_read} nur einmal, ob der Agent es über MCP ruft oder das Chat-Modell über
 * {@code tool_calls}. Ein Fehlerergebnis geht als {@code Fehler: …} an das Modell zurück.
 */
final class McpChatTool implements AiTool {

    private final McpToolContribution tool;
    private final ToolDefinition definition;

    McpChatTool(McpToolContribution tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
        this.tool = tool;
        this.definition = ToolDefinition.function(tool.getName(), tool.getDescription(), schema(tool));
    }

    @Override
    public String name() {
        return tool.getName();
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public String execute(String arguments) {
        ToolArguments args = ToolArguments.parse(arguments);
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (McpToolParameter parameter : tool.getParameters()) {
            String value = args.text(parameter.getName());
            if (value != null) {
                values.put(parameter.getName(), value);
            }
        }
        McpToolResult result = tool.getHandler().invoke(new McpToolCall(tool.getName(), values));
        return result.isError() ? "Fehler: " + result.getText() : result.getText();
    }

    private static String schema(McpToolContribution tool) {
        StringBuilder properties = new StringBuilder();
        StringBuilder required = new StringBuilder();
        for (McpToolParameter parameter : tool.getParameters()) {
            if (properties.length() > 0) {
                properties.append(',');
            }
            properties.append(quote(parameter.getName())).append(":{\"type\":")
                    .append(quote(jsonType(parameter.getType())));
            if (parameter.getType() == McpToolType.ENUM && !parameter.getEnumValues().isEmpty()) {
                properties.append(",\"enum\":[");
                for (int i = 0; i < parameter.getEnumValues().size(); i++) {
                    properties.append(i == 0 ? "" : ",").append(quote(parameter.getEnumValues().get(i)));
                }
                properties.append(']');
            }
            if (parameter.getDescription() != null && !parameter.getDescription().isEmpty()) {
                properties.append(",\"description\":").append(quote(parameter.getDescription()));
            }
            properties.append('}');
            if (parameter.isRequired()) {
                required.append(required.length() == 0 ? "" : ",").append(quote(parameter.getName()));
            }
        }
        return "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[" + required + "]}";
    }

    private static String jsonType(McpToolType type) {
        switch (type) {
            case INTEGER:
                return "integer";
            case BOOLEAN:
                return "boolean";
            default:
                return "string";
        }
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
