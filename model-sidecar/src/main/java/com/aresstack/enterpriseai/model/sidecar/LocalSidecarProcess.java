package com.aresstack.enterpriseai.model.sidecar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Besitzer des Sidecar-Prozesses (übernommen aus askai-java8 arch {@code LocalModelRuntimeManager}, ohne
 * Installer und ohne Konsolenausgabe): startet ihn bei Bedarf, liest die eine Bereitschaftszeile von stdout,
 * verwirft stderr, merkt sich die Basis-URL und beendet ihn idempotent. Ein gescheiterter Start wird 15 s lang
 * nicht wiederholt. stdin bleibt offen: der Sidecar beendet sich selbst, wenn dieser Prozess stirbt.
 */
final class LocalSidecarProcess {

    private static final long START_RETRY_BACKOFF_MILLIS = 15000L;

    private final LocalSidecarConfig config;
    private Process process;
    private String baseUrl;
    private long lastStartFailureMillis;

    LocalSidecarProcess(LocalSidecarConfig config) {
        this.config = config;
    }

    synchronized boolean isRunning() {
        return process != null && process.isAlive() && baseUrl != null;
    }

    /** @return die Basis-URL des laufenden Sidecars; startet ihn bei Bedarf */
    synchronized String ensureStarted() throws IOException {
        if (isRunning()) {
            return baseUrl;
        }
        if (System.currentTimeMillis() - lastStartFailureMillis < START_RETRY_BACKOFF_MILLIS) {
            throw new IOException("Start des lokalen Sidecars kürzlich gescheitert; neuer Versuch später");
        }
        if (!Files.isRegularFile(config.javaExecutable())) {
            throw new IOException("Java 21 nicht gefunden: " + config.javaExecutable());
        }
        if (!Files.isRegularFile(config.sidecarJar())) {
            throw new IOException("Sidecar-Jar nicht gefunden: " + config.sidecarJar());
        }
        stop();
        List<String> command = new ArrayList<String>();
        command.add(config.javaExecutable().toString());
        command.add("-jar");
        command.add(config.sidecarJar().toAbsolutePath().toString());
        command.add("--host=127.0.0.1");
        command.add("--port=0");
        command.add("--model-root=" + config.modelRoot().toAbsolutePath());
        command.add("--backend=cpu");
        final Process started = new ProcessBuilder(command).start();
        final CountDownLatch ready = new CountDownLatch(1);
        final String[] readyBaseUrl = new String[1];
        daemon("local-sidecar-stdout", new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(started.getInputStream(),
                            StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String parsed = readyBaseUrl(line);
                        if (parsed != null && readyBaseUrl[0] == null) {
                            readyBaseUrl[0] = parsed;
                            ready.countDown();
                        }
                    }
                } catch (IOException ignored) {
                    // Strom endet mit dem Prozess.
                }
            }
        });
        daemon("local-sidecar-stderr", new Runnable() {
            @Override
            public void run() {
                drain(started.getErrorStream());
            }
        });
        boolean up = false;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.readyTimeoutMillis());
        try {
            while (System.nanoTime() < deadline) {
                if (ready.await(250, TimeUnit.MILLISECONDS)) {
                    up = true;
                    break;
                }
                if (!started.isAlive()) {
                    break;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!up || readyBaseUrl[0] == null) {
            boolean died = !started.isAlive();
            started.destroyForcibly();
            lastStartFailureMillis = System.currentTimeMillis();
            throw new IOException(died ? "Lokaler Sidecar beim Start beendet (Java 21 und Jar prüfen)"
                    : "Lokaler Sidecar nicht bereit nach " + config.readyTimeoutMillis() + " ms");
        }
        process = started;
        baseUrl = readyBaseUrl[0];
        return baseUrl;
    }

    /** Idempotent: beenden, kurz warten, sonst hart beenden. */
    synchronized void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        process = null;
        baseUrl = null;
    }

    /** {@code {"event":"ready","baseUrl":"http://127.0.0.1:…"}} → Basis-URL, sonst {@code null}. */
    static String readyBaseUrl(String line) {
        if (line == null || !line.contains("\"ready\"")) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement event = object.get("event");
            JsonElement url = object.get("baseUrl");
            if (event == null || !"ready".equals(event.getAsString()) || url == null || !url.isJsonPrimitive()) {
                return null;
            }
            String value = url.getAsString();
            return value.startsWith("http://127.0.0.1:") || value.startsWith("http://localhost:") ? value : null;
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    private static void drain(InputStream in) {
        byte[] buffer = new byte[4096];
        try {
            while (in.read(buffer) != -1) {
                // verworfen: der Sidecar protokolliert selbst, hier gibt es keine Konsolenausgabe
            }
        } catch (IOException ignored) {
            // Strom endet mit dem Prozess.
        }
    }

    private static void daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
