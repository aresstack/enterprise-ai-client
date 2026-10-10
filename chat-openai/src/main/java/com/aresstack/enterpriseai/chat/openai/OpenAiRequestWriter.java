package com.aresstack.enterpriseai.chat.openai;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Übersetzt eine neutrale {@link ChatRequest} in den JSON-Body von {@code /chat/completions}. */
final class OpenAiRequestWriter {

    private final String defaultModel;
    private final DeveloperRolePolicy developerRolePolicy;

    OpenAiRequestWriter(String defaultModel, DeveloperRolePolicy developerRolePolicy) {
        this.defaultModel = defaultModel;
        this.developerRolePolicy = developerRolePolicy;
    }

    String write(ChatRequest request, boolean stream) {
        ChatOptions options = request.options();
        JsonObject body = new JsonObject();
        body.addProperty("model", options.model() != null ? options.model() : defaultModel);

        JsonArray messages = new JsonArray();
        for (ChatMessage message : request.messages()) {
            JsonObject json = new JsonObject();
            json.addProperty("role", roleOf(message));
            json.addProperty("content", message.content());
            messages.add(json);
        }
        body.add("messages", messages);

        // Streaming nur über den Body; der Server ignoriert ?stream=true als Queryparameter.
        body.addProperty("stream", stream);
        addIfSet(body, "temperature", options.temperature());
        addIfSet(body, "top_p", options.topP());
        addIfSet(body, "top_k", options.topK());
        addIfSet(body, "max_tokens", options.maxTokens());
        addIfSet(body, "presence_penalty", options.presencePenalty());
        addIfSet(body, "frequency_penalty", options.frequencyPenalty());
        if (!options.stop().isEmpty()) {
            // Immer als Array: der Server lehnt stop als einzelnen String ab.
            JsonArray stop = new JsonArray();
            for (String sequence : options.stop()) {
                stop.add(sequence);
            }
            body.add("stop", stop);
        }
        if (options.endUserId() != null) {
            body.addProperty("user", options.endUserId());
        }
        if (options.reasoningEffort() != null) {
            // Nur wenn im Composer gewählt; gegen das Gateway UNVERIFIED.
            body.addProperty("reasoning_effort", options.reasoningEffort());
        }
        // "n" wird bewusst nie gesendet: der Server akzeptiert es, ignoriert es aber.
        return body.toString();
    }

    private String roleOf(ChatMessage message) {
        switch (message.role()) {
            case SYSTEM:
                return "system";
            case DEVELOPER:
                if (developerRolePolicy == DeveloperRolePolicy.SEND_AS_DEVELOPER) {
                    return "developer";
                }
                if (developerRolePolicy == DeveloperRolePolicy.SEND_AS_SYSTEM) {
                    return "system";
                }
                throw new ChatCompletionException(ChatErrorKind.INVALID_REQUEST,
                        "role DEVELOPER is not supported by the configured chat API (it answers HTTP 500)");
            case USER:
                return "user";
            case ASSISTANT:
                return "assistant";
            default:
                throw new IllegalArgumentException("unsupported role " + message.role());
        }
    }

    private static void addIfSet(JsonObject body, String name, Number value) {
        if (value != null) {
            body.addProperty(name, value);
        }
    }
}
