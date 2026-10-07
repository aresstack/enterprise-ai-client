package com.aresstack.enterpriseai.knowledge.lucene.archfixture.signature;

import java.util.List;
import org.apache.lucene.archstub.LuceneStub;

/** Absichtlicher Verstoß: Lucene-Typ als Typargument im öffentlichen Konstruktor. */
public final class IndexExposingLucene {

    private final int size;

    public IndexExposingLucene(List<LuceneStub> documents) {
        this.size = documents.size();
    }

    public int size() {
        return size;
    }
}
