package com.aresstack.enterpriseai.chat.openai;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.domain.chat.ChatFinishReason;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.chat.ChatUsage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Liest Antworten und Stream-Chunks von {@code /chat/completions}. Zustandslos. */
final class OpenAiResponseParser {

    /** Ein gelesener Stream-Chunk; alle Felder dürfen fehlen. */
    static final class Chunk {
        final String content;
        final String finishReason;
        final String model;
        final ChatUsage usage;

        Chunk(String content, String finishReason, String model, ChatUsage usage) {
            this.content = content;
            this.finishReason = finishReason;
            this.model = model;
            this.usage = usage;
        }
    }

    /** Liest eine vollständige (Non-Streaming-)Antwort. */
    ChatResponse parseCompletion(String json) {
        JsonObject root = parseObject(json);
        failOnErrorObject(root);
        JsonObject choice = firstChoice(root);
        if (choice == null) {
            throw protocol("response contains no choices");
        }
        String content = "";
        JsonObject message = object(choice, "message");
        if (message != null) {
            String value = string(message, "content");
            content = value == null ? "" : value;
        }
        return new ChatResponse(ChatMessage.assistant(content), finishReason(string(choice, "finish_reason")),
                usage(root), string(root, "model"));
    }

    /** Liest den JSON-Teil einer SSE-{@code data:}-Zeile. */
    Chunk parseChunk(String json) {
        JsonObject root = parseObject(json);
        failOnErrorObject(root);
        JsonObject choice = firstChoice(root);
        String content = null;
        String finish = null;
        if (choice != null) {
            JsonObject delta = object(choice, "delta");
            if (delta == null) {
                // Manche Server senden auch im Stream "message" statt "delta".
                delta = object(choice, "message");
            }
            if (delta != null) {
                content = string(delta, "content");
            }
            finish = string(choice, "finish_reason");
        }
        return new Chunk(content, finish, string(root, "model"), root.has("usage") ? usage(root) : null);
    }

    static ChatFinishReason finishReason(String value) {
        if (value == null) {
            return ChatFinishReason.UNKNOWN;
        }
        if ("stop".equals(value)) {
            return ChatFinishReason.STOP;
        }
        if ("length".equals(value)) {
            return ChatFinishReason.LENGTH;
        }
        if ("content_filter".equals(value)) {
            return ChatFinishReason.CONTENT_FILTER;
        }
        return ChatFinishReason.UNKNOWN;
    }

    /** Liest {@code usage}; ein fehlender oder komplett leerer Block (alles 0) gilt als nicht gemeldet. */
    static ChatUsage usage(JsonObject root) {
        JsonObject usage = object(root, "usage");
        if (usage == null) {
            return ChatUsage.notReported();
        }
        int prompt = integer(usage, "prompt_tokens");
        int completion = integer(usage, "completion_tokens");
        int total = integer(usage, "total_tokens");
        if (prompt <= 0 && completion <= 0 && total <= 0) {
            return ChatUsage.notReported();
        }
        return ChatUsage.of(Math.max(prompt, 0), Math.max(completion, 0), Math.max(total, 0));
    }

    /** Liest die Fehlermeldung aus einem Fehler-Body ({@code {"error":{"message":...}}}) oder {@code null}. */
    static String errorMessage(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonElement error = element.getAsJsonObject().get("error");
            if (error == null || error.isJsonNull()) {
                return null;
            }
            if (error.isJsonPrimitive()) {
                return error.getAsString();
            }
            if (error.isJsonObject()) {
                return string(error.getAsJsonObject(), "message");
            }
            return null;
        } catch (JsonParseException e) {
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static void failOnErrorObject(JsonObject root) {
        JsonElement error = root.get("error");
        if (error != null && !error.isJsonNull()) {
            String message = errorMessage(root.toString());
            throw new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR,
                    "provider reported an error: " + OpenAiErrors.shorten(message == null ? "unknown" : message));
        }
    }

    private static JsonObject parseObject(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw protocol("expected a JSON object");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "response is not valid JSON", e);
        }
    }

    private static JsonObject firstChoice(JsonObject root) {
        JsonElement choices = root.get("choices");
        if (choices == null || !choices.isJsonArray()) {
            return null;
        }
        JsonArray array = choices.getAsJsonArray();
        if (array.size() == 0 || !array.get(0).isJsonObject()) {
            return null;
        }
        return array.get(0).getAsJsonObject();
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static int integer(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return -1;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static ChatCompletionException protocol(String message) {
        return new ChatCompletionException(ChatErrorKind.PROTOCOL, message);
    }
}
