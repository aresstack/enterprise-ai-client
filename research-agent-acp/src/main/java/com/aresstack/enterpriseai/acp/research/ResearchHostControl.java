package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCallException;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolClient;
import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Research-Steuerung des Hosts über MCP (askai-java8 arch: {@code researchMcp} in {@code ResearchAgentMain}).
 * Ein Werkzeugfehler wird zur {@link ToolInvoker.ToolFailure}, ein nicht erreichbarer Endpoint zur
 * {@link ToolInvoker.EndpointUnavailable} (Fehlerzuordnung wie {@code SolonToolInvoker}).
 */
final class ResearchHostControl implements ToolInvoker {

    /** Der Zustand des Projekts aus {@code research_status}. */
    static final class Status {
        final String phase;
        final String state;
        final boolean searchTagsOffered;
        final String model;

        Status(String phase, String state, boolean searchTagsOffered, String model) {
            this.phase = phase;
            this.state = state;
            this.searchTagsOffered = searchTagsOffered;
            this.model = model;
        }
    }

    /** Sieht jeden Werkzeugaufruf an den Host (für die sichtbaren Tool-Blasen des Turns). */
    interface ToolObserver {
        void started(String callId, String toolName, Map<String, Object> args);

        /** @param failure {@code null} bei Erfolg, sonst die Fehlermeldung */
        void finished(String callId, String toolName, Map<String, Object> args, String result, String failure);
    }

    private final McpToolClient client;
    private final java.util.concurrent.atomic.AtomicLong calls = new java.util.concurrent.atomic.AtomicLong();
    private volatile ToolObserver observer;

    ResearchHostControl(McpToolClient client) {
        this.client = client;
    }

    /** Der Beobachter des laufenden Turns; {@code null} schaltet ihn ab. */
    void setObserver(ToolObserver observer) {
        this.observer = observer;
    }

    @Override
    public String call(String toolName, Map<String, Object> args) throws ToolFailure, EndpointUnavailable {
        Map<String, Object> arguments = args == null ? Collections.<String, Object>emptyMap() : args;
        ToolObserver current = observer;
        String callId = "call-" + calls.incrementAndGet();
        if (current != null) {
            current.started(callId, toolName, arguments);
        }
        try {
            String result = client.callTool(toolName, arguments);
            if (current != null) {
                current.finished(callId, toolName, arguments, result, null);
            }
            return result;
        } catch (McpToolCallException failed) {
            String message = failed.getMessage() == null ? failed.toString() : failed.getMessage();
            if (current != null) {
                current.finished(callId, toolName, arguments, null, message);
            }
            if (failed.isEndpointUnavailable()) {
                throw new EndpointUnavailable(message);
            }
            throw new ToolFailure(message);
        }
    }

    /** Werkzeugnamen des Endpoints (frisch vom Host). */
    Map<String, String> tools() throws EndpointUnavailable {
        try {
            return client.listTools();
        } catch (McpToolCallException failed) {
            throw new EndpointUnavailable(failed.getMessage());
        }
    }

    Status status() throws ToolFailure, EndpointUnavailable {
        String json = call("research_status", null);
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map)) {
            throw new ToolFailure("research_status: no JSON object");
        }
        Map<?, ?> map = (Map<?, ?>) parsed;
        return new Status(text(map.get("phase"), "scoping"), text(map.get("state"), "new"),
                Boolean.TRUE.equals(map.get("searchTagsOffered")), text(map.get("model"), ""));
    }

    /** Antwort des Hosts auf ein eindeutiges Ja/Nein zu einer offenen Konfliktfrage, sonst {@code null}. */
    String beginTurn(String text) throws ToolFailure, EndpointUnavailable {
        String answer = call("research_turn_begin", single("text", text));
        return answer == null || answer.trim().isEmpty() ? null : answer;
    }

    void greeted() throws ToolFailure, EndpointUnavailable {
        call("research_greeted", null);
    }

    String scopeFence() {
        try {
            return call("research_scope_fence", null);
        } catch (Exception unavailable) {
            return "";
        }
    }

    void applyScopeUpdate(String documentJson) throws ToolFailure, EndpointUnavailable {
        call("research_scope_update", single("document_json", documentJson));
    }

    void scopeUpdateRejected(String violations) throws ToolFailure, EndpointUnavailable {
        call("research_scope_rejected", single("violations", violations));
    }

    void close() {
        client.close();
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put(key, value == null ? "" : value);
        return args;
    }

    private static String text(Object value, String fallback) {
        return value instanceof String && !((String) value).isEmpty() ? (String) value : fallback;
    }

    /** Ein String als JSON-Literal (für Segmente und Nachrichten). */
    static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        String s = value == null ? "" : value;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /** Karten-Segmente als JSON-Array (nie mit Schrägstrich verbunden: '/' bleibt ein Zeichen im Namen). */
    static String segmentsJson(List<String> segments) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(segments.get(i)));
        }
        return sb.append(']').toString();
    }
}
