package com.aresstack.enterpriseai.chat.api.archfixture.boundary;

import com.aresstack.enterpriseai.embedding.api.archfixture.FakeEmbeddingPort;

/** Absichtlicher Verstoß: der Chat-Port kennt den Embedding-Port (ChatBoundaryTest). */
public final class PortUsingEmbedding {

    public FakeEmbeddingPort embeddings() {
        return null;
    }
}
