package com.aresstack.enterpriseai.proasteion.exedra.chat.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneIndex;

/** Absichtlicher Verstoß: Ein Binding baut sich seinen Adapter selbst, statt den Port zu bekommen. */
public final class ChatBindingBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
