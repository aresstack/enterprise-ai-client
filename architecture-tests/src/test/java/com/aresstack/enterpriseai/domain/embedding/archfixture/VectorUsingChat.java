package com.aresstack.enterpriseai.domain.embedding.archfixture;

import com.aresstack.enterpriseai.domain.chat.archfixture.FakeChatValue;

/** Absichtlicher Verstoß: das Embedding-Modell kennt das Chat-Modell (EmbeddingBoundaryTest). */
public final class VectorUsingChat {

    private final FakeChatValue target = new FakeChatValue();

    public Object use() {
        return target.text();
    }
}
