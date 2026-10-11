package com.aresstack.enterpriseai.astu.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: astu kennt Lucene. */
public final class LuceneInDomain {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
