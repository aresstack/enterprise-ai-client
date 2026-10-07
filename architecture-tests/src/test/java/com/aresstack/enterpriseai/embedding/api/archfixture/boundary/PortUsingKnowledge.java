package com.aresstack.enterpriseai.embedding.api.archfixture.boundary;

import com.aresstack.enterpriseai.domain.knowledge.archfixture.FakeChunk;

/** Absichtlicher Verstoß: der Embedding-Port kennt das Wissensmodell (EmbeddingBoundaryTest). */
public final class PortUsingKnowledge {

    private final FakeChunk target = new FakeChunk();

    public Object use() {
        return target.text();
    }
}
