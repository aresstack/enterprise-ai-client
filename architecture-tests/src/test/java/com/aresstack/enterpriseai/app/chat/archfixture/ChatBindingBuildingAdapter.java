package com.aresstack.enterpriseai.app.chat.archfixture;

import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneIndex;

/** Absichtlicher Verstoß: Ein Binding baut sich seinen Adapter selbst, statt den Port zu bekommen. */
public final class ChatBindingBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
