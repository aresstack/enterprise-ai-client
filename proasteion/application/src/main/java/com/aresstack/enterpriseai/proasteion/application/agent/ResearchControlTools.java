package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.research.domain.state.oo.ResearchStateIds;
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
                        status.addProperty("phase", host.phaseId());
                        status.addProperty("state", host.stateId());
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
                        String started = startResearchIfAsked(host, text);
                        if (started != null) {
                            return McpToolResult.ok(started);
                        }
                        requestOutlineChangesIfAsked(host, text);
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
        tools.add(McpToolContribution.of("research_progress",
                "Meldet den Fortschritt des autonomen Recherche-Laufs (Tätigkeit, Suche, Seite, Zähler) für die "
                        + "Anzeige im Client.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        host.reportProgress(call.getString("activity"), call.getString("query"),
                                call.getString("title"), call.getString("url"),
                                (int) call.getInteger("pages", 0L), (int) call.getInteger("accepted", 0L));
                        return McpToolResult.ok("OK");
                    }
                }, McpToolParameter.string("activity", true, "STARTED, SEARCHING, READING_PAGE, SOURCE_ACCEPTED, "
                        + "PAGE_SKIPPED"),
                McpToolParameter.string("query", false, "Die aktuelle Suche"),
                McpToolParameter.string("title", false, "Titel der Seite"),
                McpToolParameter.string("url", false, "Adresse der Seite"),
                McpToolParameter.integer("pages", false, "Gelesene Seiten"),
                McpToolParameter.integer("accepted", false, "Übernommene Quellen")));
        tools.add(McpToolContribution.of("research_run_finished",
                "Meldet das Ende des Recherche-Laufs; der Host geht in die Freigabe der Quellen und liefert die "
                        + "Zusammenfassung für den Nutzer.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(host.reportRunFinished(call.getString("reason"),
                                (int) call.getInteger("pages", 0L), (int) call.getInteger("accepted", 0L)));
                    }
                }, McpToolParameter.string("reason", true, "Der ResearchStopReason"),
                McpToolParameter.integer("pages", false, "Gelesene Seiten"),
                McpToolParameter.integer("accepted", false, "Übernommene Quellen")));
        tools.add(McpToolContribution.of("research_search_plan",
                "Die Suchen des autonomen Laufs aus Konzept und Weidezaun, eine je Zeile.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        StringBuilder lines = new StringBuilder();
                        for (String query : host.searchPlan()) {
                            lines.append(query).append('\n');
                        }
                        return McpToolResult.ok(lines.toString().trim());
                    }
                }));
        tools.add(McpToolContribution.of("research_sources",
                "Die übernommenen Quellen des Projekts, je Zeile resource-ID, Titel und URL (tabgetrennt).",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        StringBuilder lines = new StringBuilder();
                        for (ResearchSourceRef source : host.sourceRefs()) {
                            lines.append(source.resourceId()).append('\t').append(source.title()).append('\t')
                                    .append(source.url()).append('\n');
                        }
                        return McpToolResult.ok(lines.toString().trim());
                    }
                }));
        tools.add(McpToolContribution.of("project_search",
                "Durchsucht nur die übernommenen Quellen des Projekts (hybrid über den Wissensindex); je Treffer "
                        + "passage:, resource:, title: und der Passagentext. Beispiel: query=\"Meilensteine\"",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        String query = call.getString("query");
                        if (query == null || query.trim().isEmpty()) {
                            return McpToolResult.error("Missing argument: query — example: query=\"Meilensteine\"");
                        }
                        String hits = host.projectSearch(query, (int) call.getInteger("limit", 8L));
                        return McpToolResult.ok(hits.isEmpty() ? "No passages found in the project sources." : hits);
                    }
                }, McpToolParameter.string("query", true, "Die Frage"),
                McpToolParameter.integer("limit", false, "Höchstens so viele Passagen (Standard 8)")));
        tools.add(McpToolContribution.of("research_outline",
                "Die aktuelle Gliederung des Projekts (Markdown), leer ohne Gliederung.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(host.outline());
                    }
                }));
        tools.add(McpToolContribution.of("outline_propose",
                "Legt die Gliederung (Markdown) zur Freigabe vor; nur in der Phase Gliederung.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        String rejected = host.proposeOutline(call.getString("outline"));
                        return rejected == null ? McpToolResult.ok("OK") : McpToolResult.error(rejected);
                    }
                }, McpToolParameter.string("outline", true, "Die Gliederung als Markdown")));
        tools.add(McpToolContribution.of("research_document",
                "Das Research-Dokument des Projekts als Markdown mit Quellenverzeichnis, leer ohne Dokument.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        ResearchDocument document = host.document();
                        return McpToolResult.ok(document == null ? "" : document.toMarkdown());
                    }
                }));
        tools.add(McpToolContribution.of("document_save",
                "Speichert das Research-Dokument: {title, sections:[{heading, text mit [[passage-id]]}], "
                        + "passages:{passage-id: resource-id}}; nur in der Phase Dokument.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        String json = call.getString("document_json");
                        String title;
                        List<ResearchDocument.Section> sections = new ArrayList<ResearchDocument.Section>();
                        java.util.Map<String, String> passages = new java.util.LinkedHashMap<String, String>();
                        try {
                            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                            title = root.has("title") ? root.get("title").getAsString() : "";
                            for (JsonElement element : root.getAsJsonArray("sections")) {
                                JsonObject section = element.getAsJsonObject();
                                sections.add(new ResearchDocument.Section(
                                        section.has("heading") ? section.get("heading").getAsString() : "",
                                        section.has("text") ? section.get("text").getAsString() : ""));
                            }
                            if (root.has("passages") && root.get("passages").isJsonObject()) {
                                for (java.util.Map.Entry<String, JsonElement> entry
                                        : root.getAsJsonObject("passages").entrySet()) {
                                    passages.put(entry.getKey(), entry.getValue().getAsString());
                                }
                            }
                        } catch (RuntimeException invalid) {
                            return McpToolResult.error("invalid document_json: " + invalid.getMessage());
                        }
                        String rejected = host.saveDocument(title, sections, passages);
                        return rejected == null ? McpToolResult.ok("OK") : McpToolResult.error(rejected);
                    }
                }, McpToolParameter.string("document_json", true, "Das Dokument als JSON")));
        tools.add(McpToolContribution.of("project_source_add",
                "Übernimmt bei einer Nachfrage eine weitere gelesene Seite ins Projekt; nur in der Phase Dokument. "
                        + "Beispiel: capture_id=\"123456\" title=\"Betriebshandbuch\"",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        String id = call.getString("capture_id");
                        if (id == null || id.trim().isEmpty()) {
                            return McpToolResult.error("Missing argument: capture_id");
                        }
                        String result = host.addFollowUpSource(id, call.getString("title"), call.getString("url"));
                        return result.startsWith("Not allowed") ? McpToolResult.error(result)
                                : McpToolResult.ok(result);
                    }
                }, McpToolParameter.string("capture_id", true, "Die Dokument-ID aus knowledge_search"),
                McpToolParameter.string("title", false, "Titel der Seite"),
                McpToolParameter.string("url", false, "Adresse der Seite")));
        tools.add(McpToolContribution.of("search_scope_begin",
                "Friert den Weidezaun für einen Recherche-Lauf ein (ACTIVE handle=…, INACTIVE oder UNAVAILABLE).",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(host.scopeControl().begin());
                    }
                }));
        tools.add(McpToolContribution.of("search_scope_evaluate",
                "Prüft Trefferauszüge gegen den eingefrorenen Weidezaun (KEEP/OUT/UNCLASSIFIED je Zeile).",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(host.scopeControl().evaluate(call.getString("handle"),
                                call.getString("lane"), call.getString("items_json")));
                    }
                }, McpToolParameter.string("handle", true, "Handle aus search_scope_begin"),
                McpToolParameter.string("lane", false, "serp, links oder page"),
                McpToolParameter.string("items_json", true, "JSON-Array {id, text}")));
        tools.add(McpToolContribution.of("search_scope_end",
                "Gibt den eingefrorenen Weidezaun eines Laufs frei.",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(host.scopeControl().end(call.getString("handle")));
                    }
                }, McpToolParameter.string("handle", true, "Handle aus search_scope_begin")));
        if (ResearchStateIds.RESEARCH.equals(host.phaseId())) {
            tools.add(McpToolContribution.of("source_passages",
                    "Liest die Passagen einer übernommenen Quelle aus dem Wissensindex, jede mit passage-ID für Belege. "
                            + "Beispiel: capture_id=\"123456\"",
                    new McpToolHandler() {
                        @Override
                        public McpToolResult invoke(McpToolCall call) {
                            String id = call.getString("capture_id");
                            if (id == null || id.trim().isEmpty()) {
                                return McpToolResult.error("Missing argument: capture_id — example: "
                                        + "capture_id=\"123456\"");
                            }
                            String text = host.passagesOf(id.trim());
                            return McpToolResult.ok(text.isEmpty()
                                    ? "No passages in the index for " + id.trim() + " yet (read it with knowledge_read first)."
                                    : text);
                        }
                    }, McpToolParameter.string("capture_id", true, "Die Quelle aus source_accept")));
            tools.add(McpToolContribution.of("source_images",
                    "Liest die Bilder einer übernommenen Quelle: erkannter Text (OCR) und Beschreibung getrennt. "
                            + "Beispiel: capture_id=\"123456\"",
                    new McpToolHandler() {
                        @Override
                        public McpToolResult invoke(McpToolCall call) {
                            String id = call.getString("capture_id");
                            if (id == null || id.trim().isEmpty()) {
                                return McpToolResult.error("Missing argument: capture_id — example: "
                                        + "capture_id=\"123456\"");
                            }
                            String text = host.imageAnalysesOf(id.trim());
                            return McpToolResult.ok(text.isEmpty()
                                    ? "No image analysis yet for " + id.trim() + " (no images, no model chosen, "
                                    + "or still running)." : text);
                        }
                    }, McpToolParameter.string("capture_id", true, "Die Quelle aus source_accept")));
        }
        if (host.isWritable(ResearchStateIds.RESEARCH)) {
            tools.add(McpToolContribution.of("source_accept",
                    "Übernimmt eine gelesene Seite als Quelle des Projekts. Beispiel: capture_id=\"123456\" "
                            + "title=\"Betriebshandbuch\" url=\"Wiki-Adresse der Seite\"",
                    new McpToolHandler() {
                        @Override
                        public McpToolResult invoke(McpToolCall call) {
                            if (!host.isWritable(ResearchStateIds.RESEARCH)) {
                                return notAllowed(host);
                            }
                            String id = call.getString("capture_id");
                            if (id == null || id.trim().isEmpty()) {
                                return McpToolResult.error("Missing argument: capture_id — example: "
                                        + "capture_id=\"123456\"");
                            }
                            return McpToolResult.ok(host.acceptSource(id, call.getString("title"),
                                    call.getString("url")));
                        }
                    }, McpToolParameter.string("capture_id", true, "Die capture_id aus web_open"),
                    McpToolParameter.string("title", false, "Titel der Seite"),
                    McpToolParameter.string("url", false, "Adresse der Seite")));
            tools.add(McpToolContribution.of("source_park",
                    "Merkt eine Seite vor, die nicht übernommen wurde. Beispiel: url=\"Wiki-Adresse der Seite\" "
                            + "title=\"Altes Konzept\" excerpt=\"…\"",
                    new McpToolHandler() {
                        @Override
                        public McpToolResult invoke(McpToolCall call) {
                            if (!host.isWritable(ResearchStateIds.RESEARCH)) {
                                return notAllowed(host);
                            }
                            host.parkSource(call.getString("url"), call.getString("title"),
                                    call.getString("excerpt"), call.getString("score"));
                            return McpToolResult.ok("parked");
                        }
                    }, McpToolParameter.string("url", true, "Adresse der Seite"),
                    McpToolParameter.string("title", false, "Titel der Seite"),
                    McpToolParameter.string("excerpt", false, "Auszug"),
                    McpToolParameter.string("score", false, "Relevanz als Dezimalzahl")));
        }
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

    /**
     * Der Startbefehl des Nutzers (askai arch: das {@code research:}-Präfix; dazu „Recherche starten“): der Host
     * schließt das Konzept ab und startet die Phase Quellen. Das Modell kann keine Phase wechseln.
     *
     * @return {@code null}, wenn der Text kein Startbefehl ist; sonst leer (der Agent recherchiert weiter) oder
     *         der Grund, warum nicht gestartet wurde
     */
    static String startResearchIfAsked(ResearchAgentHost host, String text) {
        String lower = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("research:") || lower.startsWith("recherche:")
                || lower.startsWith("recherche starten"))) {
            return null;
        }
        if (host.isWritable(ResearchStateIds.RESEARCH)) {
            return "";
        }
        if (ResearchStateIds.SCOPING.equals(host.phaseId())) {
            if (ResearchStateIds.NEW.equals(host.stateId())) {
                host.dispatch(ResearchCommandType.START);
            }
            String rejected = host.dispatch(ResearchCommandType.SUBMIT_SCOPE);
            if (rejected == null) {
                rejected = host.dispatch(ResearchCommandType.START_RESEARCH);
            }
            return rejected == null ? "" : "Die Recherche kann nicht starten: " + rejected;
        }
        if (ResearchStateIds.RESEARCH.equals(host.phaseId())
                && ResearchStateIds.WAITING.equals(host.stateId())) {
            String rejected = host.dispatch(ResearchCommandType.START_RESEARCH);
            return rejected == null ? "" : "Die Recherche kann nicht starten: " + rejected;
        }
        return "Die Recherche kann in der Phase " + host.phaseId() + "/" + host.stateId() + " nicht starten.";
    }

    /**
     * Eine Nachricht, während die Gliederung auf Freigabe wartet, ist ein Änderungswunsch: der Host geht über
     * REQUEST_OUTLINE_CHANGES zurück in die Gliederung, der Agent überarbeitet sie mit dieser Nachricht.
     */
    static void requestOutlineChangesIfAsked(ResearchAgentHost host, String text) {
        if (text != null && !text.trim().isEmpty() && ResearchStateIds.OUTLINE.equals(host.phaseId())
                && ResearchStateIds.WAITING_APPROVAL.equals(host.stateId())) {
            host.dispatch(ResearchCommandType.REQUEST_OUTLINE_CHANGES);
        }
    }

    private static McpToolResult notAllowed(ResearchAgentHost host) {
        return McpToolResult.error("Not allowed in the current state (" + host.phaseId() + "/"
                + host.stateId() + ").");
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
