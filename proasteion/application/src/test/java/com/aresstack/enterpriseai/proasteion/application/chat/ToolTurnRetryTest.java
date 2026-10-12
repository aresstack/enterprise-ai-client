package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolRegistry;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.proasteion.application.security.RetryingSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.SecretBackedTokenSource;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Der beobachtete Fall aus dem Betrieb, durch echten Adapter, Werkzeugschleife und Security-Port: zwei Wissenssuchen
 * laufen, vor der dritten Modellanfrage ist KeePass kurz nicht erreichbar. Der Turn endet trotzdem mit der Antwort,
 * und keine Suche läuft doppelt.
 */
public class ToolTurnRetryTest {

    private static final SecretRef REF = SecretRef.of("keepass:KI-Dienst");

    private final LinkedList<String[]> responses = new LinkedList<String[]>();
    private final List<String> bodies = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> searches = Collections.synchronizedList(new ArrayList<String>());
    private final FlakyVault vault = new FlakyVault();
    private HttpServer server;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(read(exchange.getRequestBody()));
            String[] response;
            synchronized (responses) {
                response = responses.poll();
            }
            byte[] body = response[1].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(Integer.parseInt(response[0]), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    @Test
    public void threeRoundToolTurnSurvivesATransientKeePassFailure() throws Exception {
        respond(200, toolCall("c1", "Schulung"));
        respond(200, toolCall("c2", "Weiterbildung"));
        respond(200, answer("Antwort aus beiden Suchen"));
        vault.failOnCall(3);

        ChatResponse response = streamTurn();

        assertEquals("Antwort aus beiden Suchen", response.content());
        assertEquals("keine Suche doppelt", Arrays.asList("Schulung", "Weiterbildung"), searches);
        assertEquals("drei Modellanfragen", 3, bodies.size());
        assertEquals("vierter Secret-Zugriff nach der Wiederholung", 4, vault.calls);
        String third = bodies.get(2);
        assertTrue(third, third.contains("\"tool_call_id\":\"c1\"") && third.contains("\"tool_call_id\":\"c2\""));
    }

    @Test
    public void http503IsRetriedWithTheSameHistory() throws Exception {
        respond(200, toolCall("c1", "Schulung"));
        respond(503, "{\"error\":{\"message\":\"busy\"}}");
        respond(200, answer("Fertig"));

        assertEquals("Fertig", streamTurn().content());
        assertEquals(Collections.singletonList("Schulung"), searches);
        assertEquals(3, bodies.size());
        assertEquals("Wiederholung mit genau demselben Verlauf", bodies.get(1), bodies.get(2));
    }

    @Test
    public void http401IsNotRetried() throws Exception {
        respond(401, "{\"error\":{\"message\":\"invalid key\"}}");
        respond(200, answer("nie"));
        try {
            streamTurn();
            fail();
        } catch (ChatCompletionException e) {
            assertEquals(ChatErrorKind.AUTHENTICATION, e.kind());
        }
        assertEquals(1, bodies.size());
    }

    @Test
    public void exhaustedKeePassRetriesAreReportedAsUnavailableNotAsWrongKey() throws Exception {
        respond(200, answer("nie"));
        vault.failAlways = true;
        try {
            streamTurn();
            fail();
        } catch (ChatCompletionException e) {
            assertEquals(ChatErrorKind.CREDENTIALS_UNAVAILABLE, e.kind());
        }
        assertEquals("kein Request ohne Key", 0, bodies.size());
        assertEquals("erster Versuch plus zwei Wiederholungen", 3, vault.calls);
    }

    private ChatResponse streamTurn() throws InterruptedException {
        OpenAiCompatibleChatAdapter adapter = new OpenAiCompatibleChatAdapter(OpenAiCompatibleChatConfig
                .builder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "m")
                .bearerToken(new SecretBackedTokenSource(new RetryingSecretProvider(vault, () -> RetryPolicy.of(2, 0)),
                        REF))
                .build());
        ChatCompletionsToolLoop loop = new ChatCompletionsToolLoop(
                new RetryingChatCompletionPort(adapter, RetryPolicy.of(2, 0)), tools(), Runnable::run, null, 8);
        final CountDownLatch done = new CountDownLatch(1);
        final Object[] result = new Object[1];
        loop.stream(new ChatRequest(Collections.singletonList(ChatMessage.user("Was gibt es zu Schulungen?")),
                ChatOptions.defaults()), new ChatStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onDelta(String text) {
            }

            @Override
            public void onComplete(ChatResponse response) {
                result[0] = response;
                done.countDown();
            }

            @Override
            public void onError(ChatCompletionException error) {
                result[0] = error;
                done.countDown();
            }

            @Override
            public void onCancelled() {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (result[0] instanceof ChatCompletionException) {
            throw (ChatCompletionException) result[0];
        }
        return (ChatResponse) result[0];
    }

    private ToolRegistry tools() {
        return new ToolRegistry(Collections.<AiTool>singletonList(new AiTool() {
            @Override
            public String name() {
                return "knowledge_search";
            }

            @Override
            public ToolDefinition definition() {
                return ToolDefinition.function("knowledge_search", "sucht",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}");
            }

            @Override
            public String execute(String arguments) {
                String query = arguments.replaceAll(".*\"query\"\\s*:\\s*\"([^\"]*)\".*", "$1");
                searches.add(query);
                return "Treffer zu " + query;
            }
        }));
    }

    private void respond(int status, String body) {
        synchronized (responses) {
            responses.add(new String[]{String.valueOf(status), body});
        }
    }

    private static String toolCall(String id, String query) {
        return "{\"model\":\"m\",\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\",\"message\":{"
                + "\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"" + id + "\",\"type\":\"function\","
                + "\"function\":{\"name\":\"knowledge_search\",\"arguments\":\"{\\\"query\\\":\\\"" + query
                + "\\\"}\"}}]}}]}";
    }

    private static String answer(String text) {
        return "{\"model\":\"m\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{"
                + "\"role\":\"assistant\",\"content\":\"" + text + "\"}}]}";
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /** KeePass, das beim angegebenen Zugriff einmal nicht erreichbar ist (oder immer). */
    private static final class FlakyVault implements SecretProvider {
        volatile int calls;
        volatile int failOn = -1;
        volatile boolean failAlways;

        void failOnCall(int call) {
            failOn = call;
        }

        @Override
        public synchronized SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
            calls++;
            if (failAlways || calls == failOn) {
                throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_AVAILABLE, ref,
                        "KeePassRPC antwortet nicht");
            }
            return new SecretMaterial(ref, "user", "api-key".toCharArray());
        }
    }
}
