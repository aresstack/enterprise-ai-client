package com.aresstack.enterpriseai.application.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: application kennt Lucene. */
public final class UseCaseUsingLucene {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
