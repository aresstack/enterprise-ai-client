package com.aresstack.enterpriseai.astu.acropolis.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: acropolis kennt Lucene. */
public final class UseCaseUsingLucene {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
