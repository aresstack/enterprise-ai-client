package com.aresstack.enterpriseai.chat.openai.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: Lucene außerhalb von anagraphai. */
public final class LuceneInChatAdapter {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
