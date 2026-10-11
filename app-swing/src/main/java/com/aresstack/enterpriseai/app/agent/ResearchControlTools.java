package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Research-Control-Endpoint des Hosts (askai-java8 arch: die MCP-Werkzeuge der Research-Steuerung, die der
 * Agentenprozess {@code ResearchAgentMain} über MCP ruft). Der Host bleibt die einzige Zustandsautorität: der
 * Agent liest {@code research_status}, meldet die Begrüßung und Weidezaun-Vorschläge und benutzt die
 * Konzeptwerkzeuge des aktuellen Projekts.
 *
 * <p>Das Hauptmodell erreicht der Agent über {@code main_model_complete}: der Host führt die Anfrage über seinen
 * Chat-Port aus (Modellauswahl, Zugangsdaten und Proxy bleiben im Client; der Agentenprozess bekommt keine
 * Secrets).
 */
public final class ResearchControlTools {

    static final String PHASE_SCOPING = "scoping";

    private ResearchControlTools() {
    }

    /** Alle Werkzeuge des Endpoints; die Konzeptwerkzeuge gehen je Aufruf an das aktuelle Projekt. */
    public static List<McpToolContribution> toolsFor(final ResearchAgentHost host, final MainModelChat model) {
        if (host == null || model == null) {
            throw new IllegalArgumentException("host and model must not be null");
        }
        List<McpToolContribution> tools = new ArrayList<McpToolContribution>();
        tools.add(McpToolContribution.of("research_status",
                "Liest den Zustand des Research-Projekts (Phase, Zustand, Begrüßung, angebotene Suchen) als JSON.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        JsonObject status = new JsonObject();
                        boolean greeted = host.isGreeted();
                        status.addProperty("phase", PHASE_SCOPING);
                        status.addProperty("state", greeted ? "scoping" : "new");
                        status.addProperty("greeted", greeted);
                        status.addProperty("searchTagsOffered", host.conceptHost().searchTagsOffered());
                        status.addProperty("model", model.modelName());
                        return McpToolResult.ok(status.toString());
                    }
                }));
        tools.add(McpToolContribution.of("research_turn_begin",
                "Beginnt einen Turn: vermerkt den Auftrag, beantwortet ein eindeutiges Ja/Nein auf eine offene "
                        + "Konfliktfrage selbst (Antworttext, sonst leer) und frischt die Gesprächsführung auf.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        String text = call.getString("text");
                        ResearchConceptHost concept = host.conceptHost();
                        if (text != null && !text.trim().isEmpty()) {
                            concept.recordMissionIfAbsent(text);
                            String answer = concept.hostAnswerFor(text);
                            if (answer != null) {
                                return McpToolResult.ok(answer);
                            }
                        }
                        concept.refreshConversationPolicy();
                        return McpToolResult.ok("");
                    }
                }, McpToolParameter.string("text", false, "Die Nachricht des Nutzers")));
        tools.add(McpToolContribution.of("research_greeted",
                "Vermerkt, dass der Agent das Projekt begrüßt hat; die Begrüßung wird nie wiederholt.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        host.markGreeted();
                        return McpToolResult.ok("OK");
                    }
                }));
        tools.add(McpToolContribution.of("research_scope_fence",
                "Der maßgebliche Weidezaun des Projekts als Text für den Kontext des Modells.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(host.renderedFence());
                    }
                }));
        tools.add(McpToolContribution.of("research_scope_update",
                "Wendet einen geprüften Scoping-Vorschlag (ScopeUpdateDocument als JSON) auf den Weidezaun an.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        host.applyScopeUpdate(call.getString("document_json"));
                        return McpToolResult.ok("OK");
                    }
                }, McpToolParameter.string("document_json", true, "ScopeUpdateDocument als JSON")));
        tools.add(McpToolContribution.of("research_scope_rejected",
                "Meldet einen verworfenen Scoping-Vorschlag sichtbar (der ganze Vorschlag entfällt).",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        host.scopeUpdateRejected(call.getString("violations"));
                        return McpToolResult.ok("OK");
                    }
                }, McpToolParameter.string("violations", true, "Die Verstöße des Vorschlags")));
        tools.add(McpToolContribution.of("main_model_complete",
                "Führt eine Anfrage an das Hauptmodell des Clients aus (Chat-Port, Modellwahl des Agent-Modus).",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        List<ChatMessage> messages;
                        double temperature;
                        try {
                            messages = parseMessages(call.getString("messages_json"));
                            String t = call.getString("temperature");
                            temperature = t == null || t.trim().isEmpty() ? 0.2d : Double.parseDouble(t.trim());
                        } catch (RuntimeException invalid) {
                            return McpToolResult.error(MainModelChatResult.Status.INVALID_RESPONSE.name()
                                    + ": invalid request");
                        }
                        MainModelChatResult result = model.complete(messages, temperature,
                                (int) call.getInteger("max_output_tokens", 0L));
                        return result.isOk() ? McpToolResult.ok(result.getText())
                                : McpToolResult.error(result.getStatus().name() + ": " + result.getDetail());
                    }
                }, McpToolParameter.string("messages_json", true, "Nachrichten als JSON-Array {role, content}"),
                McpToolParameter.string("temperature", false, "Temperatur als Dezimalzahl"),
                McpToolParameter.integer("max_output_tokens", false, "Ausgabebudget, 0 = Standard")));
        for (final McpToolContribution concept : host.conceptHost().tools()) {
            tools.add(new McpToolContribution(concept.getName(), concept.getDescription(), concept.getParameters(),
                    new McpToolHandler() {
                        @Override
                        public McpToolResult invoke(McpToolCall call) {
                            McpToolResult result = host.conceptHost().call(concept.getName(),
                                    call.getArguments());
                            return result != null ? result : McpToolResult.error("Not allowed: "
                                    + concept.getName() + " is not offered in this phase");
                        }
                    }));
        }
        return tools;
    }

    static List<ChatMessage> parseMessages(String json) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement element : array) {
            JsonObject message = element.getAsJsonObject();
            String role = message.get("role").getAsString();
            String content = message.has("content") && !message.get("content").isJsonNull()
                    ? message.get("content").getAsString() : "";
            if ("system".equals(role)) {
                messages.add(ChatMessage.system(content));
            } else if ("assistant".equals(role)) {
                messages.add(ChatMessage.assistant(content));
            } else {
                messages.add(ChatMessage.user(content));
            }
        }
        return messages;
    }
}
