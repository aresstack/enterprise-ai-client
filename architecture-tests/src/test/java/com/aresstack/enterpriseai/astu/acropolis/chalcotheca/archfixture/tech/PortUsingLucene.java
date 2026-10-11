package com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.tech;

import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: chalcotheca kennt Lucene. */
public final class PortUsingLucene {

    private final LuceneStub lucene = new LuceneStub();

    public String describe() {
        return lucene.describe();
    }
}
