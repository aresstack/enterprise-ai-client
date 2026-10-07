package com.aresstack.enterpriseai.chat.api.archfixture.matrix;

import com.aresstack.enterpriseai.embedding.api.archfixture.FakeEmbeddingPort;

/** Absichtlicher Verstoß: ein Port kennt einen anderen Port. */
public final class PortUsingOtherPort {

    public FakeEmbeddingPort embeddings() {
        return null;
    }
}
