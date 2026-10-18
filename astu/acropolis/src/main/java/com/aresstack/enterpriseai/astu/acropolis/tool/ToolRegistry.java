package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.astu.chat.ChatImage;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Die Werkzeuge einer Anfrage, nach Namen. Unveränderlich nach dem Bau; welche davon gerade angeboten und
 * ausgeführt werden, entscheidet {@code active} bei jedem Aufruf neu (Reiter „Tools“ während einer laufenden Runde).
 */
public final class ToolRegistry {

    /** Anfang eines Fehlerergebnisses, so wie es an das Modell zurückgeht (auch von {@code McpChatTool}). */
    public static final String ERROR_PREFIX = "Fehler:";

    private final Map<String, AiTool> tools;
    private final Predicate<String> active;

    public ToolRegistry(List<? extends AiTool> tools) {
        this(tools, null);
    }

    /**
     * @param active sagt je Aufruf, ob ein Werkzeug (nach Namen) gerade aktiv ist; {@code null}: alle aktiv. Ein
     *               abgeschaltetes Werkzeug fehlt in {@link #definitions()} und wird nicht mehr ausgeführt.
     */
    public ToolRegistry(List<? extends AiTool> tools, Predicate<String> active) {
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
        this.active = active == null ? name -> true : active;
    }

    /** Die Definitionen aller jetzt aktiven Werkzeuge in Registrierungsreihenfolge. */
    public List<ToolDefinition> definitions() {
        List<ToolDefinition> definitions = new ArrayList<ToolDefinition>(tools.size());
        for (AiTool tool : tools.values()) {
            if (active.test(tool.name())) {
                definitions.add(tool.definition());
            }
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

    /**
     * Führt einen Werkzeugaufruf des Modells aus. Unbekannte und inzwischen abgeschaltete Werkzeuge sowie Fehler
     * werden zum Ergebnistext ({@code Fehler: …}), damit das Modell reagieren kann.
     *
     * @param activity erfährt den Namen vor der Ausführung und danach, ob sie gelang; darf {@code null} sein
     */
    public String execute(String name, String arguments, ToolActivityListener activity) {
        return run(name, arguments, activity).text();
    }

    /**
     * Like {@link #execute}, returning the whole output: the same text plus the images of a successful call (a
     * screenshot); failures carry no images and no context.
     */
    public ToolOutput run(String name, String arguments, ToolActivityListener activity) {
        AiTool tool = find(name);
        if (tool == null) {
            return ToolOutput.text(ERROR_PREFIX + " unbekanntes Werkzeug " + name);
        }
        if (!active.test(name)) {
            return ToolOutput.text(ERROR_PREFIX + " Werkzeug " + name + " ist abgeschaltet");
        }
        if (activity != null) {
            activity.onToolCall(name, arguments);
        }
        String output;
        boolean succeeded;
        List<ToolContextResource> context = Collections.emptyList();
        List<ChatImage> images = Collections.emptyList();
        try {
            ToolOutput result = tool.executeWithContext(arguments);
            output = result == null ? "" : result.text();
            succeeded = !output.startsWith(ERROR_PREFIX);
            if (succeeded && result != null) {
                context = result.context();
                images = result.images();
            }
        } catch (RuntimeException e) {
            String message = e.getMessage();
            output = ERROR_PREFIX + " "
                    + (message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message);
            succeeded = false;
        }
        if (activity != null) {
            if (!context.isEmpty()) {
                activity.onToolContext(name, context);
            }
            activity.onToolResult(name, succeeded);
            activity.onToolExecuted(name, arguments, output, succeeded);
        }
        return new ToolOutput(output, context, images);
    }
}
