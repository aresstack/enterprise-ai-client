package com.aresstack.enterpriseai.domain.chat.archfixture;

import com.aresstack.enterpriseai.domain.embedding.archfixture.FakeVector;

/** Absichtlicher Verstoß: das Chat-Modell kennt das Embedding-Modell (ChatBoundaryTest). */
public final class ChatValueUsingEmbedding {

    private final FakeVector target = new FakeVector();

    public Object use() {
        return target.dimension();
    }
}
