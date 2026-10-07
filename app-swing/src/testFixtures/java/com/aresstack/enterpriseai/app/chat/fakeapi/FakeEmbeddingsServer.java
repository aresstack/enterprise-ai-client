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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lokaler Fake des Enterprise-Endpunkts {@code /v1/embeddings} auf 127.0.0.1 für den Integrationstest der
 * RAG-Shell (AP22), nach dem Vorbild von {@code FakeEmbeddingServer} aus embedding-openai (Strang C). Antwortet
 * im OpenAI-Format ({@code data[].embedding} als Float-Liste, {@code usage} mit Nullen) auf String- und
 * Array-Input und zeichnet jede Anfrage auf.
 *
 * <p>Die Vektoren sind eine "semantische" Attrappe per Feature-Hashing: jedes Wort ab vier Zeichen (Füllwörter
 * wie "die", "ist", "wie" fallen weg) und sein Fünf-Zeichen-Präfix (damit "Urlaubstage" und "Urlaub" verwandt
 * sind) zählen auf je eine Dimension ein, danach wird normiert. Texte mit gemeinsamen Wörtern liegen damit im
 * Cosinus nahe beieinander, so dass die semantische Suche im Test deterministisch sinnvolle Treffer liefert.
 */
public final class FakeEmbeddingsServer implements AutoCloseable {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** Eine aufgezeichnete Anfrage. */
    public static final class Recorded {
        private final String authorization;
        private final List<String> inputs;

        Recorded(String authorization, List<String> inputs) {
            this.authorization = authorization;
            this.inputs = Collections.unmodifiableList(new ArrayList<String>(inputs));
        }

        public String authorization() {
            return authorization;
        }

        public List<String> inputs() {
            return inputs;
        }
    }

    private final HttpServer server;
    private final int dimension;
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<Recorded>());
    private volatile int failStatus;
    private volatile String failBody = "";
    private volatile CountDownLatch gate;

    public FakeEmbeddingsServer(int dimension) throws IOException {
        if (dimension < 8) {
            throw new IllegalArgumentException("dimension must be at least 8");
        }
        this.dimension = dimension;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/embeddings", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                serve(exchange);
            }
        });
        server.start();
    }

    /** Basis-URL für {@code OpenAiCompatibleEmbeddingConfiguration}; der Adapter hängt {@code /embeddings} an. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public int dimension() {
        return dimension;
    }

    /** Alle folgenden Anfragen scheitern mit diesem Status, bis {@link #recover()} gerufen wird. */
    public FakeEmbeddingsServer failWith(int status, String body) {
        failStatus = status;
        failBody = body == null ? "" : body;
        return this;
    }

    public FakeEmbeddingsServer recover() {
        failStatus = 0;
        return this;
    }

    /**
     * Hält jede Antwort zurück, bis {@link #release()} gerufen wird. Die Anfrage wird trotzdem sofort
     * aufgezeichnet, so dass ein Test mit {@link #awaitRequests(int, long, TimeUnit)} auf sie warten und
     * währenddessen z. B. einen Abbruch auslösen kann.
     */
    public FakeEmbeddingsServer hold() {
        gate = new CountDownLatch(1);
        return this;
    }

    public FakeEmbeddingsServer release() {
        CountDownLatch current = gate;
        gate = null;
        if (current != null) {
            current.countDown();
        }
        return this;
    }

    /** Wartet, bis mindestens {@code count} Anfragen eingegangen sind. */
    public boolean awaitRequests(int count, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (requests.size() < count) {
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

    public int requestCount() {
        return requests.size();
    }

    /** Der normierte Hashing-Vektor, den der Fake für {@code text} liefert (für Erwartungen im Test). */
    public float[] vectorFor(String text) {
        float[] values = new float[dimension];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() < 4) {
                continue;
            }
            count(values, word);
            if (word.length() > 5) {
                count(values, word.substring(0, 5));
            }
        }
        double norm = 0;
        for (float v : values) {
            norm += v * v;
        }
        if (norm == 0) {
            values[0] = 1f; // leerer Text: fester Einheitsvektor statt Nullvektor
            return values;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < values.length; i++) {
            values[i] *= scale;
        }
        return values;
    }

    private static void count(float[] values, String feature) {
        int hash = feature.hashCode();
        values[Math.floorMod(hash, values.length)] += (hash & 1) == 0 ? 1f : -1f;
    }

    @Override
    public void close() {
        release();
        server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        JsonObject body = JsonParser.parseString(new String(readAll(exchange.getRequestBody()), UTF_8)).getAsJsonObject();
        List<String> inputs = new ArrayList<String>();
        JsonElement input = body.get("input");
        if (input != null && input.isJsonArray()) {
            for (JsonElement element : input.getAsJsonArray()) {
                inputs.add(element.getAsString());
            }
        } else if (input != null && !input.isJsonNull()) {
            inputs.add(input.getAsString());
        }
        requests.add(new Recorded(exchange.getRequestHeaders().getFirst("Authorization"), inputs));
        CountDownLatch current = gate;
        if (current != null) {
            try {
                current.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                send(exchange, 503, "");
                return;
            }
        }
        if (failStatus != 0) {
            send(exchange, failStatus, failBody);
            return;
        }
        JsonArray data = new JsonArray();
        for (int i = 0; i < inputs.size(); i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("object", "embedding");
            entry.addProperty("index", i);
            JsonArray vector = new JsonArray();
            for (float v : vectorFor(inputs.get(i))) {
                vector.add(v);
            }
            entry.add("embedding", vector);
            data.add(entry);
        }
        JsonObject response = new JsonObject();
        response.addProperty("object", "list");
        response.add("data", data);
        response.addProperty("model", body.has("model") ? body.get("model").getAsString() : "fake");
        JsonObject usage = new JsonObject();
        usage.addProperty("prompt_tokens", 0);
        usage.addProperty("total_tokens", 0);
        response.add("usage", usage);
        send(exchange, 200, response.toString());
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(bytes);
        } finally {
            out.close();
            exchange.close();
        }
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
