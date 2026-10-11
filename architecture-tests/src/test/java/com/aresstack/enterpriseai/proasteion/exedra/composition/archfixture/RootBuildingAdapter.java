package com.aresstack.enterpriseai.proasteion.exedra.composition.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneIndex;

/** Erlaubt: Nur die Composition Root ruft Konstruktoren von Adaptern auf, die einen Port implementieren. */
public final class RootBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
