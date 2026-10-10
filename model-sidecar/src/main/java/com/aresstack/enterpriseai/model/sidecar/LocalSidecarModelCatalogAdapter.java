package com.aresstack.enterpriseai.model.sidecar;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.model.api.ModelCatalogException;
import com.aresstack.enterpriseai.model.api.ModelCatalogPort;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ModelCatalogPort} des optionalen lokalen Java-21-Sidecars: startet ihn beim ersten Abruf, fragt
 * {@code GET /api/tags} auf 127.0.0.1 (ohne Proxy) und meldet je Modell die Fähigkeiten seines Manifests
 * ({@code details.capabilities}). {@link #close()} beendet den Prozess.
 */
public final class LocalSidecarModelCatalogAdapter implements ModelCatalogPort, Closeable {

    /** Kennung dieses Katalogs, Präfix der Auswahl in der Konfiguration ({@code model.rerank=local:<modell>}). */
    public static final String CATALOG_ID = "local";
    static final String DISPLAY_NAME = "Lokal (Java 21)";
    private static final int TIMEOUT_MILLIS = 10000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final LocalSidecarConfig config;
    private final LocalSidecarProcess process;

    public LocalSidecarModelCatalogAdapter(LocalSidecarConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.process = new LocalSidecarProcess(config);
    }

    public LocalSidecarConfig config() {
        return config;
    }

    @Override
    public String catalogId() {
        return CATALOG_ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public List<ModelDescriptor> models() throws ModelCatalogException {
        String body;
        try {
            String baseUrl = process.ensureStarted();
            HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + "/api/tags").toURL()
                    .openConnection(Proxy.NO_PROXY);
            try {
                connection.setConnectTimeout(TIMEOUT_MILLIS);
                connection.setReadTimeout(TIMEOUT_MILLIS);
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                int status = connection.getResponseCode();
                if (status != 200) {
                    throw new ModelCatalogException("Lokaler Sidecar: HTTP " + status);
                }
                body = read(connection.getInputStream());
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new ModelCatalogException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
        }
        try {
            return parse(body);
        } catch (RuntimeException e) {
            throw new ModelCatalogException("Lokaler Sidecar: Antwort nicht lesbar (" + e.getClass().getSimpleName()
                    + ")");
        }
    }

    /** {@code {"models":[{"name":…,"details":{"capabilities":[…]}}]}} → Deskriptoren. */
    static List<ModelDescriptor> parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        List<ModelDescriptor> models = new ArrayList<ModelDescriptor>();
        if (!root.isJsonObject() || !root.getAsJsonObject().has("models")
                || !root.getAsJsonObject().get("models").isJsonArray()) {
            throw new IllegalStateException("no models array");
        }
        JsonArray entries = root.getAsJsonObject().getAsJsonArray("models");
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement name = entry.has("name") ? entry.get("name") : entry.get("model");
            if (name == null || !name.isJsonPrimitive() || name.getAsString().trim().isEmpty()) {
                continue;
            }
            List<String> capabilities = new ArrayList<String>();
            JsonElement details = entry.get("details");
            if (details != null && details.isJsonObject()) {
                JsonElement caps = details.getAsJsonObject().get("capabilities");
                if (caps != null && caps.isJsonArray()) {
                    for (JsonElement cap : caps.getAsJsonArray()) {
                        if (cap.isJsonPrimitive()) {
                            capabilities.add(cap.getAsString());
                        }
                    }
                }
            }
            models.add(ModelDescriptor.builder(CATALOG_ID, name.getAsString())
                    .catalogName(DISPLAY_NAME)
                    .capabilities(capabilities)
                    .build());
        }
        return models;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (buffer.size() + read > MAX_BODY_BYTES) {
                    throw new IOException("Modellliste des Sidecars zu groß");
                }
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        process.stop();
    }

    @Override
    public String toString() {
        return "LocalSidecarModelCatalogAdapter[" + config + "]";
    }
}
