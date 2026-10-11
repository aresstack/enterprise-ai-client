package com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeIndexPortContract;

/** Steht für einen Adapter, der einen Port implementiert (anagraphai). Nur für CompositionRootBoundaryTest. */
public final class FakeLuceneIndex implements FakeIndexPortContract {

    @Override
    public int search(String query) {
        return query.length();
    }
}
