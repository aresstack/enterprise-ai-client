package com.aresstack.enterpriseai.astu.acropolis.knowledge.archfixture;

import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;

/** Absichtlicher Verstoß: die Indexierung kennt den Chat-Port (RagBoundaryTest). */
public final class IndexingUsingChat {

    public FakeChatPort chat() {
        return null;
    }
}
