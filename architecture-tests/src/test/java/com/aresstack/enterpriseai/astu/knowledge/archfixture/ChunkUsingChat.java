package com.aresstack.enterpriseai.astu.knowledge.archfixture;

import com.aresstack.enterpriseai.astu.chat.archfixture.FakeChatValue;

/** Absichtlicher Verstoß: das Wissensmodell kennt das Chat-Modell (KnowledgeBoundaryTest). */
public final class ChunkUsingChat {

    private final FakeChatValue target = new FakeChatValue();

    public Object use() {
        return target.text();
    }
}
