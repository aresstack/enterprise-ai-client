package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarConfig;

/**
 * Chat und Embeddings des lokalen Java-21-Sidecars für die Composition Root ({@link ModelCatalogs}: ein Prozess für
 * Katalog, Chat, Embeddings und Sprachausgabe, gestartet erst bei der ersten Anfrage).
 */
public interface LocalModelRuntime {

    ChatCompletionPort chat(LocalSidecarConfig config);

    EmbeddingPort embeddings(LocalSidecarConfig config, String modelId, int dimension);
}
