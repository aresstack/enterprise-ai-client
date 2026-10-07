package com.aresstack.enterpriseai.knowledge.lucene.archfixture;

import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeIndexPortContract;

/** Steht für einen Adapter, der einen Port implementiert (knowledge-lucene). Nur für CompositionRootBoundaryTest. */
public final class FakeLuceneIndex implements FakeIndexPortContract {

    @Override
    public int search(String query) {
        return query.length();
    }
}
