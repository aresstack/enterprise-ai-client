package com.aresstack.enterpriseai.astu.chat.archfixture;

import com.aresstack.enterpriseai.astu.embedding.archfixture.FakeVector;

/** Absichtlicher Verstoß: das Chat-Modell kennt das Embedding-Modell (ChatBoundaryTest). */
public final class ChatValueUsingEmbedding {

    private final FakeVector target = new FakeVector();

    public Object use() {
        return target.dimension();
    }
}
