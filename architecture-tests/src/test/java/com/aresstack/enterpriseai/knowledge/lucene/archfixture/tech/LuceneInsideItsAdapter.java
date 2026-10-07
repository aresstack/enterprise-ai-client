package com.aresstack.enterpriseai.knowledge.lucene.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Gegenprobe: Lucene in knowledge-lucene ist erlaubt. */
public final class LuceneInsideItsAdapter {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
