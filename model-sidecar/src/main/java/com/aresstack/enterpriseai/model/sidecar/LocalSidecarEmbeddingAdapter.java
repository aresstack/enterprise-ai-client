package com.aresstack.enterpriseai.model.sidecar;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingInputs;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link EmbeddingPort} des lokalen Java-21-Sidecars über seine vorhandene {@code POST /api/embed}
 * ({@code {"model":…,"input":[…]}} → {@code {"embeddings":[[…],…]}}, ohne E5-Präfix, {@code input_type=raw}).
 * Der Sidecar meldet keine Dimension vorab; sie kommt aus der Konfiguration, und jeder Vektor wird streng dagegen
 * geprüft. Die Identität trägt {@code catalog=local}, damit ein lokaler Index nie mit einem der Enterprise-API
 * gemischt wird.
 */
public final class LocalSidecarEmbeddingAdapter implements EmbeddingPort {

    private static final int READ_TIMEOUT_MILLIS = 300000;
    private static final int MAX_BODY_BYTES = 64 * 1024 * 1024;
    private static final int BATCH_SIZE = 32;

    private final LocalSidecarProcess process;
    private final String modelId;
    private final EmbeddingModelIdentity identity;

    LocalSidecarEmbeddingAdapter(LocalSidecarProcess process, String modelId, int dimension) {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalArgumentException("modelId must not be empty");
        }
        this.process = process;
        this.modelId = modelId;
        this.identity = identity(modelId, dimension);
    }

    /** Die Embedding-Welt eines lokalen Modells (ohne Prozess, für die Composition Root). */
    public static EmbeddingModelIdentity identity(String modelId, int dimension) {
        return EmbeddingModelIdentity.of(modelId, dimension).withAttribute("encoding", "float")
                .withAttribute("catalog", LocalSidecarModelCatalogAdapter.CATALOG_ID);
    }

    @Override
    public EmbeddingModelIdentity modelIdentity() {
        return identity;
    }

    @Override
    public EmbeddingBatch embed(List<String> texts) {
        List<String> inputs = EmbeddingInputs.requireValid(texts);
        if (inputs.isEmpty()) {
            return EmbeddingBatch.empty(identity);
        }
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(inputs.size());
        for (int from = 0; from < inputs.size(); from += BATCH_SIZE) {
            vectors.addAll(embedBatch(inputs.subList(from, Math.min(inputs.size(), from + BATCH_SIZE))));
        }
        return EmbeddingBatch.of(identity, inputs.size(), vectors);
    }

    private List<EmbeddingVector> embedBatch(List<String> batch) {
        JsonObject request = new JsonObject();
        request.addProperty("model", modelId);
        request.addProperty("input_type", "raw");
        JsonArray input = new JsonArray();
        for (String text : batch) {
            input.add(text);
        }
        request.add("input", input);
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        String text;
        try {
            connection = LocalSidecarHttp.post(process.ensureStarted(), "/api/embed", body, READ_TIMEOUT_MILLIS);
            int status = connection.getResponseCode();
            if (status != 200) {
                String detail = LocalSidecarHttp.error(connection);
                throw new EmbeddingException(status >= 500 ? EmbeddingFailureKind.PROVIDER_ERROR
                        : EmbeddingFailureKind.REJECTED, "Lokales Embedding-Modell: HTTP " + status
                        + (detail.isEmpty() ? "" : " " + detail));
            }
            text = new String(LocalSidecarHttp.read(connection.getInputStream(), MAX_BODY_BYTES),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Lokaler Sidecar nicht erreichbar ("
                    + e.getClass().getSimpleName() + ")", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        try {
            JsonArray rows = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("embeddings");
            if (rows == null || rows.size() != batch.size()) {
                throw new EmbeddingException(EmbeddingFailureKind.INVALID_RESPONSE,
                        "Sidecar lieferte " + (rows == null ? 0 : rows.size()) + " statt " + batch.size()
                                + " Vektoren");
            }
            List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(rows.size());
            for (JsonElement row : rows) {
                JsonArray values = row.getAsJsonArray();
                if (values.size() != identity.dimension()) {
                    throw new EmbeddingException(EmbeddingFailureKind.INVALID_RESPONSE, "Lokales Modell " + modelId
                            + " liefert " + values.size() + " Dimensionen, konfiguriert sind "
                            + identity.dimension() + " (embedding.dimension)");
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = values.get(i).getAsFloat();
                }
                vectors.add(EmbeddingVector.of(identity, vector));
            }
            return vectors;
        } catch (EmbeddingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EmbeddingException(EmbeddingFailureKind.INVALID_RESPONSE, "Antwort des Sidecars nicht lesbar",
                    e);
        }
    }

    @Override
    public String toString() {
        return "LocalSidecarEmbeddingAdapter[" + identity + "]";
    }
}
