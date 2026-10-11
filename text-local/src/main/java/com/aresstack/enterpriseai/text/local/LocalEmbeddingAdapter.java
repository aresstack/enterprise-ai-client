package com.aresstack.enterpriseai.text.local;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.text.runtime.EncoderBatch;
import com.aresstack.enterpriseai.text.runtime.EncoderOutput;
import com.aresstack.enterpriseai.text.runtime.TextEncoderRuntime;
import com.aresstack.enterpriseai.text.runtime.TextRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link EmbeddingPort} für ein lokal installiertes Embedding-Modell (ONNX-Export vom Hugging Face Hub, ohne
 * Umwandlung): Tokenizer aus {@code tokenizer.json} in reinem Java, Encoder über {@link TextEncoderRuntime}, Pooling
 * und Normalisierung nach der Sentence-Transformers-Konfiguration ({@code modules.json}, {@code 1_Pooling/config.json});
 * fehlt sie, Mittelwert über die Token und L2-Normalisierung. Dimension aus {@code config.json}. E5-Modelle (Ordnername
 * wie {@code intfloat--e5-base-v2}) bekommen wie im Sidecar aus askai-java8 je Text {@code passage: } bzw. für
 * Suchanfragen {@code query: } vorangestellt. Blockiert.
 */
public final class LocalEmbeddingAdapter implements EmbeddingPort {

    private static final int BATCH = 16;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final String QUERY_PREFIX = "query: ";
    private static final String PASSAGE_PREFIX = "passage: ";

    private final Path directory;
    private final TextEncoderRuntime runtime;
    private final EmbeddingModelIdentity identity;
    private final Pooling pooling;
    private final boolean normalize;
    private final int maxTokens;
    private final long padId;
    private final boolean e5;
    private HfTokenizer tokenizer;

    /**
     * @param catalogId Katalog der Modellauswahl (Teil der Identität)
     * @param modelId   Ordnername unter {@code modelRoot}
     * @throws IllegalArgumentException wenn das Modell fehlt oder seine Konfiguration unlesbar ist
     */
    public LocalEmbeddingAdapter(String catalogId, String modelId, Path modelRoot, TextEncoderRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        this.directory = LocalModelFiles.directory(modelRoot, modelId);
        this.runtime = runtime;
        this.e5 = isE5(modelId);
        try {
            Map<String, Object> config = Json.read(LocalModelFiles.require(directory, "config.json"));
            LocalModelFiles.require(directory, "tokenizer.json");
            Map<String, Object> tokenizerConfig = Json.readIfPresent(directory.resolve("tokenizer_config.json"));
            List<Object> modules = Json.list(Json.parse(Files.isRegularFile(directory.resolve("modules.json"))
                    ? new String(Files.readAllBytes(directory.resolve("modules.json")), "UTF-8") : "[]"));
            Pooling configured = null;
            boolean normalizeModule = false;
            int dense = 0;
            for (Object entry : modules) {
                Map<String, Object> module = Json.object(entry);
                String type = Json.text(module.get("type"), "");
                String path = Json.text(module.get("path"), "");
                if (type.endsWith(".Pooling")) {
                    configured = Pooling.of(Json.readIfPresent(directory.resolve(path).resolve("config.json")));
                } else if (type.endsWith(".Normalize")) {
                    normalizeModule = true;
                } else if (type.endsWith(".Dense")) {
                    dense = Json.integer(Json.readIfPresent(directory.resolve(path).resolve("config.json"))
                            .get("out_features"), 0);
                }
            }
            this.pooling = configured == null ? Pooling.MEAN : configured;
            this.normalize = modules.isEmpty() || normalizeModule;
            int dimension = dense > 0 ? dense : Json.integer(config.get("hidden_size"),
                    Json.integer(config.get("d_model"), 0));
            if (dimension <= 0) {
                throw new IllegalArgumentException("config.json von " + modelId + " nennt keine hidden_size");
            }
            this.maxTokens = maxTokens(config, tokenizerConfig);
            this.padId = Json.integer(config.get("pad_token_id"), 0);
            EmbeddingModelIdentity base = EmbeddingModelIdentity.of(modelId, dimension).withAttribute("encoding", "float")
                    .withAttribute("catalog", catalogId).withAttribute("pooling", pooling.name().toLowerCase(Locale.ROOT))
                    .withAttribute("normalized", String.valueOf(normalize));
            // Vektoren mit Präfix sind eine eigene Welt: ein ohne Präfix gebauter Index wird neu aufgebaut.
            this.identity = e5 ? base.withAttribute("prefix", "query/passage") : base;
        } catch (IOException e) {
            throw new IllegalArgumentException("Lokales Embedding-Modell " + modelId + " nicht lesbar: "
                    + e.getMessage(), e);
        }
    }

    private static int maxTokens(Map<String, Object> config, Map<String, Object> tokenizerConfig) {
        int limit = DEFAULT_MAX_TOKENS;
        int fromTokenizer = Json.integer(tokenizerConfig.get("model_max_length"), 0);
        if (fromTokenizer > 0 && fromTokenizer < limit) {
            limit = fromTokenizer;
        }
        int positions = Json.integer(config.get("max_position_embeddings"), 0);
        if (Json.text(config.get("model_type"), "").contains("roberta")) {
            positions -= 2;
        }
        if (positions > 0 && positions < limit) {
            limit = positions;
        }
        return limit;
    }

    /** E5-Familie am Ordnernamen (Repository mit {@code /} → {@code --}): ein Namensteil {@code e5}. */
    static boolean isE5(String modelId) {
        return modelId != null && modelId.toLowerCase(Locale.ROOT).matches("(.*[^a-z0-9])?e5([^a-z0-9].*)?");
    }

    @Override
    public EmbeddingModelIdentity modelIdentity() {
        return identity;
    }

    @Override
    public EmbeddingBatch embed(List<String> texts) {
        return embed(texts, PASSAGE_PREFIX);
    }

    @Override
    public EmbeddingBatch embedQueries(List<String> queries) {
        return embed(queries, QUERY_PREFIX);
    }

    private EmbeddingBatch embed(List<String> texts, String prefix) {
        if (texts == null) {
            throw new IllegalArgumentException("texts must not be null");
        }
        for (String text : texts) {
            if (text == null) {
                throw new IllegalArgumentException("texts must not contain null");
            }
        }
        if (texts.isEmpty()) {
            return EmbeddingBatch.empty(identity);
        }
        HfTokenizer tokens = tokenizer();
        Path graph;
        try {
            graph = LocalModelFiles.graph(directory);
        } catch (IOException e) {
            throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, e.getMessage(), e);
        }
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH) {
            List<String> part = texts.subList(start, Math.min(texts.size(), start + BATCH));
            vectors.addAll(embedBatch(tokens, graph, e5 ? prefixed(part, prefix) : part));
        }
        return EmbeddingBatch.of(identity, texts.size(), vectors);
    }

    private static List<String> prefixed(List<String> texts, String prefix) {
        List<String> prefixed = new ArrayList<String>(texts.size());
        for (String text : texts) {
            prefixed.add(prefix + text);
        }
        return prefixed;
    }

    private List<EmbeddingVector> embedBatch(HfTokenizer tokens, Path graph, List<String> texts) {
        int[][] encoded = new int[texts.size()][];
        int length = 1;
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = tokens.encodeTruncated(texts.get(i), maxTokens);
            length = Math.max(length, encoded[i].length);
        }
        long[][] ids = new long[encoded.length][length];
        long[][] mask = new long[encoded.length][length];
        for (int row = 0; row < encoded.length; row++) {
            for (int position = 0; position < length; position++) {
                boolean token = position < encoded[row].length;
                ids[row][position] = token ? encoded[row][position] : padId;
                mask[row][position] = token ? 1 : 0;
            }
            if (encoded[row].length == 0) {
                mask[row][0] = 1;
            }
        }
        EncoderBatch batch = new EncoderBatch(ids, mask);
        EncoderOutput output;
        try {
            output = runtime.encode(graph, batch);
        } catch (TextRuntimeException e) {
            throw new EmbeddingException(EmbeddingFailureKind.PROVIDER_ERROR, "Lokales Embedding ("
                    + runtime.name() + "): " + e.getMessage(), e);
        }
        if (output.width() != identity.dimension()) {
            throw new EmbeddingException(EmbeddingFailureKind.INVALID_RESPONSE, "Modell liefert " + output.width()
                    + " Dimensionen, erwartet " + identity.dimension());
        }
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(texts.size());
        for (int row = 0; row < texts.size(); row++) {
            float[] vector = output.pooled() ? row(output, row) : pooling.pool(output, batch, row);
            if (normalize) {
                normalizeInPlace(vector);
            }
            vectors.add(EmbeddingVector.of(identity, vector));
        }
        return vectors;
    }

    private static float[] row(EncoderOutput output, int row) {
        float[] vector = new float[output.width()];
        for (int d = 0; d < vector.length; d++) {
            vector[d] = output.value(row, 0, d);
        }
        return vector;
    }

    static void normalizeInPlace(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm > 1e-12) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
    }

    private synchronized HfTokenizer tokenizer() {
        if (tokenizer == null) {
            try {
                tokenizer = HfTokenizer.load(directory.resolve("tokenizer.json"));
            } catch (IOException | TokenizerFormatException e) {
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "tokenizer.json von "
                        + identity.modelId() + " nicht nutzbar: " + e.getMessage(), e);
            }
        }
        return tokenizer;
    }

    /** Wie aus den Token-Vektoren ein Satzvektor wird (Sentence-Transformers-Pooling). */
    enum Pooling {
        MEAN, CLS, MAX, LAST;

        static Pooling of(Map<String, Object> config) {
            if (Json.bool(config.get("pooling_mode_cls_token"), false)) {
                return CLS;
            }
            if (Json.bool(config.get("pooling_mode_max_tokens"), false)) {
                return MAX;
            }
            if (Json.bool(config.get("pooling_mode_lasttoken"), false)) {
                return LAST;
            }
            return MEAN;
        }

        float[] pool(EncoderOutput output, EncoderBatch batch, int row) {
            int width = output.width();
            float[] vector = new float[width];
            if (this == CLS) {
                for (int d = 0; d < width; d++) {
                    vector[d] = output.value(row, 0, d);
                }
                return vector;
            }
            if (this == LAST) {
                int last = 0;
                for (int p = 0; p < output.length(); p++) {
                    if (batch.mask(row, p) != 0) {
                        last = p;
                    }
                }
                for (int d = 0; d < width; d++) {
                    vector[d] = output.value(row, last, d);
                }
                return vector;
            }
            if (this == MAX) {
                java.util.Arrays.fill(vector, Float.NEGATIVE_INFINITY);
            }
            int count = 0;
            for (int p = 0; p < output.length(); p++) {
                if (batch.mask(row, p) == 0) {
                    continue;
                }
                count++;
                for (int d = 0; d < width; d++) {
                    float value = output.value(row, p, d);
                    vector[d] = this == MAX ? Math.max(vector[d], value) : vector[d] + value;
                }
            }
            if (this == MEAN && count > 0) {
                for (int d = 0; d < width; d++) {
                    vector[d] /= count;
                }
            }
            return vector;
        }
    }
}
