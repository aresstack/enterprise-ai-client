package com.aresstack.enterpriseai.domain.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: domain kennt Lucene. */
public final class LuceneInDomain {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
