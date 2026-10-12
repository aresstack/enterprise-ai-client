package com.aresstack.enterpriseai.model.kipitz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Parallele Requests an {@code /audio/speech} tragen alle dasselbe {@code model} (und dieselbe {@code voice}); die
 * Diagnose nennt Modell und Antwort-Header des Servers, aber weder Text noch API-Key.
 */
public class KipitzSpeechAdapterTest {

    private static final String TOKEN = "geheimer-api-key";
    private HttpServer server;
    private final List<JsonObject> bodies = new CopyOnWriteArrayList<JsonObject>();
    private final List<String> logLines = new CopyOnWriteArrayList<String>();
    private final Logger log = Logger.getLogger(KipitzSpeechAdapter.class.getName());
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logLines.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @Before
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/v1/audio/speech", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                JsonObject body = JsonParser.parseString(new String(readAll(exchange.getRequestBody()),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                bodies.add(body);
                byte[] wav = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
                exchange.getResponseHeaders().add("Content-Type", "audio/wav");
                exchange.getResponseHeaders().add("X-Served-Model", body.get("model").getAsString());
                exchange.getResponseHeaders().add("X-Api-Key-Hint", "nie loggen");
                exchange.sendResponseHeaders(200, wav.length);
                OutputStream out = exchange.getResponseBody();
                out.write(wav);
                out.close();
            }
        });
        server.start();
        log.addHandler(capture);
    }

    @After
    public void stop() {
        log.removeHandler(capture);
        server.stop(0);
    }

    @Test
    public void parallelParagraphRequestsAllCarryTheSameModelAndVoice() throws Exception {
        URI base = URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
                + server.getAddress().getPort() + "/v1");
        final KipitzSpeechAdapter adapter = new KipitzSpeechAdapter(KipitzModelCatalogConfig.builder(base)
                .bearerToken(new java.util.function.Supplier<String>() {
                    @Override
                    public String get() {
                        return TOKEN;
                    }
                }).build(), "thomas");
        ExecutorService workers = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (final String paragraph : Arrays.asList("Absatz eins.", "Absatz zwei.", "Absatz drei.",
                "Absatz vier.", "Absatz fünf.")) {
            futures.add(workers.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return adapter.synthesize("orpheus-tts-3b-de", paragraph);
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        workers.shutdown();

        assertEquals(5, bodies.size());
        for (JsonObject body : bodies) {
            assertEquals("orpheus-tts-3b-de", body.get("model").getAsString());
            assertEquals("thomas", body.get("voice").getAsString());
            assertEquals("wav", body.get("response_format").getAsString());
        }
        int requests = 0;
        int responses = 0;
        for (String line : logLines) {
            assertFalse(line, line.contains("Absatz"));
            assertFalse(line, line.contains(TOKEN));
            assertFalse(line, line.contains("nie loggen"));
            if (line.startsWith("TTS HTTP request:")) {
                requests++;
                assertTrue(line, line.contains(" model=orpheus-tts-3b-de voice=thomas "));
            } else if (line.startsWith("TTS HTTP response:")) {
                responses++;
                assertTrue(line, line.contains("x-served-model=orpheus-tts-3b-de"));
            }
        }
        assertEquals(5, requests);
        assertEquals(5, responses);
    }

    @Test
    public void requestedVoiceWinsOverTheConfiguredOne() throws Exception {
        URI base = URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
                + server.getAddress().getPort() + "/v1");
        KipitzSpeechAdapter adapter = new KipitzSpeechAdapter(KipitzModelCatalogConfig.builder(base).build(), null);
        adapter.synthesize("qwen3-tts", "Vivian", "Ein Absatz.");
        adapter.synthesize("qwen3-tts", "Ein Absatz.");
        assertEquals("Vivian", bodies.get(0).get("voice").getAsString());
        assertFalse(bodies.get(1).has("voice"));
    }

    @Test
    public void servedByKeepsModelHeadersOnly() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put(null, Collections.singletonList("HTTP/1.1 200 OK"));
        headers.put("X-Model-Id", Collections.singletonList("qwen3-tts"));
        headers.put("Authorization-Model", Collections.singletonList("x"));
        headers.put("Content-Length", Collections.singletonList("12"));
        assertEquals("{x-model-id=qwen3-tts}", KipitzSpeechAdapter.servedBy(headers));
        assertEquals("none", KipitzSpeechAdapter.servedBy(Collections.<String, List<String>>emptyMap()));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
