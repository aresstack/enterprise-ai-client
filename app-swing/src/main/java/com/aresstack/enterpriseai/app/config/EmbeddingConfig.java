package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.embedding.openai.EmbeddingInputMode;

import java.net.URI;

/**
 * Embedding-Endpunkt der Enterprise-API. Modellname und Dimension sind reine Konfiguration (Dimension von
 * {@code e5-base-sts-en-de} vermutlich 768, UNVERIFIED); beide bestimmen zusammen den Namensraum des Index.
 */
public final class EmbeddingConfig {

    private final URI baseUrl;
    private final String model;
    private final int dimension;
    private final SecretRef apiKeyRef;
    private final EmbeddingInputMode inputMode;
    private final int maxBatchSize;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    EmbeddingConfig(URI baseUrl, String model, int dimension, SecretRef apiKeyRef, EmbeddingInputMode inputMode,
                    int maxBatchSize, int connectTimeoutMillis, int readTimeoutMillis) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimension = dimension;
        this.apiKeyRef = apiKeyRef;
        this.inputMode = inputMode;
        this.maxBatchSize = maxBatchSize;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /** Basis der API; der Adapter hängt {@code /embeddings} an. Standard: die Chat-Basis-URL. */
    public URI baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public int dimension() {
        return dimension;
    }

    /** Verweis auf den API-Key oder {@code null}. Standard: der API-Key des Chats. */
    public SecretRef apiKeyRef() {
        return apiKeyRef;
    }

    public EmbeddingInputMode inputMode() {
        return inputMode;
    }

    public int maxBatchSize() {
        return maxBatchSize;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    @Override
    public String toString() {
        return "EmbeddingConfig[baseUrl=" + baseUrl + ", model=" + model + ", dimension=" + dimension
                + ", apiKeyRef=" + (apiKeyRef == null ? "keine" : apiKeyRef) + ", inputMode=" + inputMode + "]";
    }
}
