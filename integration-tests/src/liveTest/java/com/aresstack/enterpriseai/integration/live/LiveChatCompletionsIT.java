package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Slice A gegen die echte Enterprise-API ({@code /chat/completions}, Streaming). Parameter:
 * {@code -Dlive.chat.baseUrl=https://…/v1 -Dlive.chat.model=…}, API-Key in {@code ENTERPRISE_AI_LIVE_API_KEY}.
 */
public class LiveChatCompletionsIT {

    @Test
    public void streamsAnAnswerFromTheEnterpriseApi() throws Exception {
        URI baseUrl = URI.create(LiveSettings.required("live.chat.baseUrl"));
        String model = LiveSettings.required("live.chat.model");
        char[] apiKey = LiveSettings.secret(LiveSettings.API_KEY_ENV);
        OpenAiCompatibleChatAdapter adapter;
        try {
            adapter = new OpenAiCompatibleChatAdapter(OpenAiCompatibleChatConfig.builder(baseUrl, model)
                    .bearerToken(OpenAiCompatibleChatConfig.TokenSource.fixed(new String(apiKey)))
                    .readTimeoutMillis(120000)
                    .build());
        } finally {
            Arrays.fill(apiKey, '\0');
        }
        ChatService chat = new ChatService(adapter);
        ChatConversationId conversation = chat.openConversation("Antworte mit genau einem Wort.");

        final StringBuilder answer = new StringBuilder();
        final AtomicInteger deltas = new AtomicInteger();
        final AtomicReference<String> failure = new AtomicReference<String>();
        final CountDownLatch done = new CountDownLatch(1);
        chat.send(conversation, "Welche Farbe hat der Himmel bei klarem Wetter?", new ChatTurnListener() {
            @Override
            public void onDelta(String text) {
                synchronized (answer) {
                    answer.append(text);
                }
                deltas.incrementAndGet();
            }

            @Override
            public void onCompleted(ChatResponse response) {
                done.countDown();
            }

            @Override
            public void onFailed(ChatCompletionException error) {
                failure.set(error.getClass().getSimpleName() + ": " + error.getMessage());
                done.countDown();
            }

            @Override
            public void onCancelled() {
                failure.set("cancelled");
                done.countDown();
            }
        });
        assertTrue("keine Antwort innerhalb von 180 s", done.await(180, TimeUnit.SECONDS));
        assertNull(failure.get(), failure.get());
        String text;
        synchronized (answer) {
            text = answer.toString().trim();
        }
        assertTrue("leere Antwort", !text.isEmpty());
        System.out.println("[live] chat: " + deltas.get() + " Deltas, " + text.length() + " Zeichen");
    }
}
