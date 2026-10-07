package com.aresstack.enterpriseai.knowledge.api.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: knowledge-api kennt Lucene. */
public final class PortUsingLucene {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
