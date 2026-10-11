package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Hauptmodell des Recherche-Agenten über den Chat-Port des Clients (Enterprise-API oder lokales Modell, je nach
 * Auswahl der Kategorie CHAT). Ersetzt den {@code HttpMainModelChatClient} aus askai-java8, der Ollama
 * {@code /api/chat} direkt ansprach: die Gesprächs-Engine kennt nur {@link MainModelChat}, das Routing zum
 * Modell bleibt beim Port.
 *
 * <p>JSON-Modus ({@link #completeJson}) gibt es am Chat-Port nicht; es gilt die Rückfalllösung aus askai (eine
 * normale Antwort, danach die strenge Prüfung der Engine samt einer Reparaturrunde).
 */
public final class ChatPortMainModelChat implements MainModelChat {

    private final ChatCompletionPort chat;
    private final ChatOptions defaults;

    public ChatPortMainModelChat(ChatCompletionPort chat, ChatOptions defaults) {
        if (chat == null) {
            throw new IllegalArgumentException("chat must not be null");
        }
        this.chat = chat;
        this.defaults = defaults == null ? ChatOptions.defaults() : defaults;
    }

    @Override
    public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
        List<com.aresstack.enterpriseai.domain.chat.ChatMessage> request =
                new ArrayList<com.aresstack.enterpriseai.domain.chat.ChatMessage>();
        for (ChatMessage message : messages) {
            switch (message.getRole()) {
                case SYSTEM:
                    request.add(com.aresstack.enterpriseai.domain.chat.ChatMessage.system(message.getContent()));
                    break;
                case ASSISTANT:
                    request.add(com.aresstack.enterpriseai.domain.chat.ChatMessage.assistant(message.getContent()));
                    break;
                default:
                    request.add(com.aresstack.enterpriseai.domain.chat.ChatMessage.user(message.getContent()));
                    break;
            }
        }
        ChatOptions options = defaults.toBuilder()
                .temperature(Double.valueOf(temperature))
                .maxTokens(maxOutputTokens > 0 ? Integer.valueOf(maxOutputTokens) : null)
                .build();
        try {
            ChatResponse response = chat.complete(new ChatRequest(request, options));
            String content = response == null ? null : response.content();
            if (content == null || content.trim().isEmpty()) {
                return MainModelChatResult.failure(MainModelChatResult.Status.INVALID_RESPONSE,
                        "empty answer");
            }
            return MainModelChatResult.ok(content);
        } catch (ChatCompletionException failed) {
            return MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE,
                    failed.kind() + (failed.statusCode() > 0 ? " " + failed.statusCode() : ""));
        } catch (RuntimeException failed) {
            return MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE,
                    failed.getClass().getSimpleName());
        }
    }

    @Override
    public String modelName() {
        String model = defaults.model();
        return model == null ? "" : model;
    }
}
