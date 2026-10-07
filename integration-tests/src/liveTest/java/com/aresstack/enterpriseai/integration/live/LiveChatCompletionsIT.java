package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Stufe 1 der Live-Verifikation: Slice A gegen die echte Enterprise-API ({@code /chat/completions}), Streaming
 * über den {@code ChatService} und Nicht-Streaming direkt über den Port; dazu die Form einer Fehlerantwort.
 * Parameter: {@code -Dlive.chat.baseUrl=https://…/v1 -Dlive.chat.model=…}, API-Key in
 * {@code ENTERPRISE_AI_LIVE_API_KEY}.
 */
public class LiveChatCompletionsIT {

    private static final int STAGE = 1;
    private static final String SYSTEM_PROMPT = "Antworte mit genau einem Wort.";
    private static final String QUESTION = "Welche Farbe hat der Himmel bei klarem Wetter?";

    private static OpenAiCompatibleChatAdapter adapter() {
        URI baseUrl = URI.create(LiveSettings.required("live.chat.baseUrl"));
        String model = LiveSettings.required("live.chat.model");
        return new OpenAiCompatibleChatAdapter(OpenAiCompatibleChatConfig.builder(baseUrl, model)
                .bearerToken(LiveSettings.chatApiKey())
                .readTimeoutMillis(120000)
                .build());
    }

    /**
     * Einordnung eines Fehlers ohne seine Meldung: Adapter-Meldungen nennen bei Transportfehlern den Host und bei
     * HTTP-Fehlern bis zu 300 Zeichen des Antwortkörpers, beides gehört nicht in die Rückmeldung.
     */
    private static String describe(ChatCompletionException error) {
        StringBuilder text = new StringBuilder(error.kind().toString());
        if (error.statusCode() >= 0) {
            text.append(", HTTP ").append(error.statusCode());
        }
        Throwable cause = error.getCause();
        if (cause != null) {
            text.append(", Ursache ").append(cause.getClass().getSimpleName());
            for (Throwable deeper = cause.getCause(); deeper != null && deeper != cause; deeper = deeper.getCause()) {
                text.append(" <- ").append(deeper.getClass().getSimpleName());
                cause = deeper;
            }
        }
        return text.toString();
    }

    private static String describe(ChatResponse response) {
        String configuredModel = LiveSettings.optional("live.chat.model");
        String modelEcho = response.model() == null ? "Antwort nennt kein Modell"
                : (response.model().equals(configuredModel) ? "Modell wie konfiguriert" : "Antwort nennt ein anderes Modell");
        return "finish_reason " + response.finishReason() + ", usage " + (response.usage().isReported()
                ? "gemeldet (prompt=" + response.usage().promptTokens() + ", completion="
                        + response.usage().completionTokens() + ", total=" + response.usage().totalTokens() + ")"
                : "nicht gemeldet (fehlt oder alles 0)") + ", " + modelEcho;
    }

    @Test
    public void streamsAnAnswerFromTheEnterpriseApi() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            ChatService chat = new ChatService(adapter());
            ChatConversationId conversation = chat.openConversation(SYSTEM_PROMPT);

            final StringBuilder answer = new StringBuilder();
            final AtomicInteger deltas = new AtomicInteger();
            final AtomicReference<ChatResponse> completed = new AtomicReference<ChatResponse>();
            final AtomicReference<String> failure = new AtomicReference<String>();
            final CountDownLatch done = new CountDownLatch(1);
            chat.send(conversation, QUESTION, new ChatTurnListener() {
                @Override
                public void onDelta(String text) {
                    synchronized (answer) {
                        answer.append(text);
                    }
                    deltas.incrementAndGet();
                }

                @Override
                public void onCompleted(ChatResponse response) {
                    completed.set(response);
                    done.countDown();
                }

                @Override
                public void onFailed(ChatCompletionException error) {
                    failure.set("Chat-Anfrage gescheitert: " + describe(error));
                    done.countDown();
                }

                @Override
                public void onCancelled() {
                    failure.set("cancelled");
                    done.countDown();
                }
            });
            assertTrue("keine Antwort innerhalb von 180 s", done.await(180, TimeUnit.SECONDS));
            if (failure.get() != null) {
                fail(failure.get());
            }
            String text;
            synchronized (answer) {
                text = answer.toString().trim();
            }
            assertFalse("leere Antwort", text.isEmpty());
            assertTrue("Antwort nicht in der Historie", chat.conversation(conversation).messages().size() == 2);
            LiveSettings.report(STAGE, "Streaming: " + deltas.get() + " Deltas, " + text.length() + " Zeichen, "
                    + describe(completed.get()) + "; Historie: 2 Nachrichten");
        }, LiveSettings.API_KEY_ENV);
    }

    @Test
    public void completesWithoutStreaming() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            ChatResponse response = adapter().complete(new ChatRequest(
                    Arrays.asList(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(QUESTION)),
                    ChatOptions.defaults()));
            String text = response.content() == null ? "" : response.content().trim();
            assertFalse("leere Antwort ohne Streaming", text.isEmpty());
            LiveSettings.report(STAGE, "ohne Streaming (stream=false): " + text.length() + " Zeichen, " + describe(response));
        }, LiveSettings.API_KEY_ENV);
    }

    /**
     * Zusatzbefund zu "Fehler-Bodies außer HTTP 500" (UNVERIFIED): Wie antwortet die API auf ein unbekanntes
     * Modell, und wie ordnet der Adapter das ein? Jede Antwort ist ein Befund, der Test wird davon nicht rot.
     */
    @Test
    public void reportsHowAnUnknownModelIsAnswered() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            ChatOptions options = ChatOptions.builder().model("live-verification-unknown-model").build();
            try {
                ChatResponse response = adapter().complete(new ChatRequest(
                        Arrays.asList(ChatMessage.user(QUESTION)), options));
                LiveSettings.report(STAGE, "unbekanntes Modell: KEIN Fehler, API antwortet trotzdem ("
                        + describe(response) + ")");
            } catch (ChatCompletionException error) {
                LiveSettings.report(STAGE, "unbekanntes Modell: Fehler, " + describe(error));
            }
        }, LiveSettings.API_KEY_ENV);
    }
}
