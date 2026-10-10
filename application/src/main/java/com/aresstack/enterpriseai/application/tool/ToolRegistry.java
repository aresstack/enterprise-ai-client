package com.aresstack.enterpriseai.application.tool;

import com.aresstack.enterpriseai.chat.api.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Die Werkzeuge einer Anfrage, nach Namen. Unveränderlich nach dem Bau. */
public final class ToolRegistry {

    private final Map<String, AiTool> tools;

    public ToolRegistry(List<? extends AiTool> tools) {
        Map<String, AiTool> byName = new LinkedHashMap<String, AiTool>();
        if (tools != null) {
            for (AiTool tool : tools) {
                if (tool == null) {
                    throw new IllegalArgumentException("tools must not contain null");
                }
                if (byName.put(tool.name(), tool) != null) {
                    throw new IllegalArgumentException("duplicate tool " + tool.name());
                }
            }
        }
        this.tools = Collections.unmodifiableMap(byName);
    }

    /** Die Definitionen aller Werkzeuge in Registrierungsreihenfolge. */
    public List<ToolDefinition> definitions() {
        List<ToolDefinition> definitions = new ArrayList<ToolDefinition>(tools.size());
        for (AiTool tool : tools.values()) {
            definitions.add(tool.definition());
        }
        return definitions;
    }

    /** Das Werkzeug mit diesem Namen oder {@code null}. */
    public AiTool find(String name) {
        return name == null ? null : tools.get(name);
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }
}
