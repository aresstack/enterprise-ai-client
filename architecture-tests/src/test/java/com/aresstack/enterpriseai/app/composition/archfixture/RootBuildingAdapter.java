package com.aresstack.enterpriseai.app.composition.archfixture;

import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneIndex;

/** Erlaubt: Nur die Composition Root ruft Konstruktoren von Adaptern auf, die einen Port implementieren. */
public final class RootBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
