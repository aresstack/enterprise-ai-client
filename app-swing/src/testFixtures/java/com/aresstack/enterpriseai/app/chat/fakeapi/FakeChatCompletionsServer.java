package com.aresstack.enterpriseai.app.chat.fakeapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lokaler Fake des Enterprise-Endpunkts {@code /v1/chat/completions} auf 127.0.0.1 für den Integrationstest der
 * RAG-Shell (AP22), nach dem Vorbild von {@code FakeOpenAiServer} aus chat-openai (Strang A). Spielt das im
 * Nachtrag festgehaltene Wire-Verhalten ab: SSE-{@code data:}-Zeilen in stark gebündelten Stücken, Null-Content-
 * Chunks, {@code usage} mit Nullen, {@code [DONE]} zum Schluss; auf Wunsch hängt die Antwort danach, bis der
 * Client trennt (Stop), oder der Server antwortet mit einem HTTP-Fehler. Jede Anfrage wird mit Header und Body
 * aufgezeichnet, damit der Test den Kontextblock im Request prüfen kann.
 */
public final class FakeChatCompletionsServer implements AutoCloseable {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** Eine aufgezeichnete Anfrage. */
    public static final class Recorded {
        private final String authorization;
        private final String contentType;
        private final JsonObject body;

        Recorded(String authorization, String contentType, JsonObject body) {
            this.authorization = authorization;
            this.contentType = contentType;
            this.body = body;
        }

        public String authorization() {
            return authorization;
        }

        public String contentType() {
            return contentType;
        }

        public JsonObject body() {
            return body;
        }

        /** Alle Nachrichten der Anfrage als {@code role: content}-Paare in Reihenfolge. */
        public List<String> messages() {
            List<String> result = new ArrayList<String>();
            for (JsonElement element : body.getAsJsonArray("messages")) {
                JsonObject message = element.getAsJsonObject();
                JsonElement content = message.get("content");
                result.add(message.get("role").getAsString() + ": "
                        + (content == null || content.isJsonNull() ? "" : content.getAsString()));
            }
            return result;
        }

        /** Der Inhalt der ersten System-Nachricht oder leer. */
        public String systemContent() {
            for (String message : messages()) {
                if (message.startsWith("system: ")) {
                    return message.substring("system: ".length());
                }
            }
            return "";
        }

        public boolean streamRequested() {
            JsonElement stream = body.get("stream");
            return stream != null && !stream.isJsonNull() && stream.getAsBoolean();
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<Recorded>());
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile List<String> scriptedDeltas = Arrays.asList("Antwort");
    private volatile boolean hangAfterDeltas;
    private volatile int failStatus;
    private volatile String failBody = "";
    private final CountDownLatch firstDeltaSent = new CountDownLatch(1);
    private final java.util.concurrent.atomic.AtomicInteger expectedRequests = new java.util.concurrent.atomic.AtomicInteger();

    public FakeChatCompletionsServer() throws IOException {
        // Explizit 127.0.0.1 statt getLoopbackAddress(): Letzteres liefert bei preferIPv6Addresses ::1, die
        // Basis-URL nennt aber immer 127.0.0.1 (Bind- und Advertise-Adresse müssen übereinstimmen).
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/chat/completions", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                serve(exchange);
            }
        });
        server.setExecutor(executor);
        server.start();
    }

    /** Basis-URL für {@code OpenAiCompatibleChatConfig}; der Adapter hängt {@code chat/completions} an. */
    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    /** Die nächsten Antworten streamen diese Deltas (gebündelt in zwei Stücken) und enden regulär. */
    public FakeChatCompletionsServer answerWith(String... deltas) {
        scriptedDeltas = Arrays.asList(deltas);
        hangAfterDeltas = false;
        failStatus = 0;
        return this;
    }

    /** Wie {@link #answerWith}, aber die Antwort bleibt nach den Deltas offen, bis der Client trennt. */
    public FakeChatCompletionsServer answerAndHang(String... deltas) {
        answerWith(deltas);
        hangAfterDeltas = true;
        return this;
    }

    /** Die nächsten Anfragen scheitern mit diesem Status und Body. */
    public FakeChatCompletionsServer failWith(int status, String body) {
        failStatus = status;
        failBody = body == null ? "" : body;
        return this;
    }

    public boolean awaitFirstDelta(long timeout, TimeUnit unit) throws InterruptedException {
        return firstDeltaSent.await(timeout, unit);
    }

    /** Wartet, bis eine weitere Anfrage als die bisher gezählten eingegangen ist. */
    public boolean awaitRequest(long timeout, TimeUnit unit) throws InterruptedException {
        int expected = expectedRequests.incrementAndGet();
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (requests.size() < expected) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10L);
        }
        return true;
    }

    public List<Recorded> requests() {
        synchronized (requests) {
            return new ArrayList<Recorded>(requests);
        }
    }

    public Recorded lastRequest() {
        List<Recorded> all = requests();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    private void serve(HttpExchange exchange) throws IOException {
        byte[] bytes = readAll(exchange.getRequestBody());
        JsonObject body = JsonParser.parseString(new String(bytes, UTF_8)).getAsJsonObject();
        requests.add(new Recorded(exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"), body));
        if (failStatus != 0) {
            byte[] error = failBody.getBytes(UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(failStatus, error.length == 0 ? -1 : error.length);
            OutputStream out = exchange.getResponseBody();
            try {
                out.write(error);
            } finally {
                out.close();
                exchange.close();
            }
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        try {
            List<String> deltas = scriptedDeltas;
            // Stark gebatcht wie der echte Server: erstes Delta allein, der Rest in einem Stück.
            StringBuilder first = new StringBuilder();
            StringBuilder rest = new StringBuilder();
            for (int i = 0; i < deltas.size(); i++) {
                (i == 0 ? first : rest).append(deltaChunk(deltas.get(i), null));
            }
            rest.append(deltaChunk(null, null)); // Null-Content-Chunk ist zulässig
            out.write(first.toString().getBytes(UTF_8));
            out.flush();
            firstDeltaSent.countDown();
            if (hangAfterDeltas) {
                out.write(rest.toString().getBytes(UTF_8));
                out.flush();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            rest.append(deltaChunk("", "stop"));
            rest.append("data: [DONE]\n\n");
            out.write(rest.toString().getBytes(UTF_8));
            out.flush();
        } catch (IOException clientGone) {
            // Client hat getrennt (Stop)
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
                // Client bereits weg
            }
            exchange.close();
        }
    }

    private static String deltaChunk(String content, String finishReason) {
        JsonObject delta = new JsonObject();
        if (content == null) {
            delta.add("content", null);
        } else {
            delta.addProperty("content", content);
        }
        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.add("delta", delta);
        if (finishReason == null) {
            choice.add("finish_reason", null);
        } else {
            choice.addProperty("finish_reason", finishReason);
        }
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject usage = new JsonObject();
        usage.addProperty("prompt_tokens", 0);
        usage.addProperty("completion_tokens", 0);
        usage.addProperty("total_tokens", 0);
        JsonObject chunk = new JsonObject();
        chunk.addProperty("id", "chatcmpl-fake");
        chunk.addProperty("object", "chat.completion.chunk");
        chunk.addProperty("model", "openai/gpt-oss-120b");
        chunk.add("choices", choices);
        chunk.add("usage", usage);
        return "data: " + chunk + "\n\n";
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
