package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Hauptmodell des Agentenprozesses über das Host-Werkzeug {@code main_model_complete} (ersetzt askais
 * {@code HttpMainModelChatClient}, der Ollama direkt ansprach): Modellwahl, Zugangsdaten und Proxy bleiben im
 * Client. Wie jeder {@link MainModelChat} wirft er nie; jeder Fehler kommt als benanntes Ergebnis zurück.
 */
final class McpMainModelChat implements MainModelChat {

    private final ResearchHostControl host;
    private volatile String modelName = "";

    McpMainModelChat(ResearchHostControl host) {
        this.host = host;
    }

    void setModelName(String name) {
        this.modelName = name == null ? "" : name;
    }

    @Override
    public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"role\":").append(ResearchHostControl.quote(message.getRole().wire()))
                    .append(",\"content\":").append(ResearchHostControl.quote(message.getContent())).append('}');
        }
        json.append(']');
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("messages_json", json.toString());
        args.put("temperature", String.format(Locale.ROOT, "%.3f", temperature));
        args.put("max_output_tokens", Integer.valueOf(Math.max(0, maxOutputTokens)));
        try {
            return MainModelChatResult.ok(host.call("main_model_complete", args));
        } catch (ToolInvoker.ToolFailure failed) {
            return failure(failed.getMessage());
        } catch (ToolInvoker.EndpointUnavailable unavailable) {
            return MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE,
                    "host endpoint unavailable");
        }
    }

    /** Der Host meldet {@code STATUS: Detail}; unbekannte Status gelten als Providerfehler. */
    private static MainModelChatResult failure(String message) {
        String text = message == null ? "" : message.trim();
        for (MainModelChatResult.Status status : MainModelChatResult.Status.values()) {
            String prefix = status.name() + ":";
            if (status != MainModelChatResult.Status.OK && text.startsWith(prefix)) {
                return MainModelChatResult.failure(status, text.substring(prefix.length()).trim());
            }
        }
        return MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE, text);
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
