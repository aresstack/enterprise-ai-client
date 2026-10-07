package com.aresstack.enterpriseai.app.ui.archfixture;

import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: Die Oberfläche greift an der Application vorbei direkt auf einen Adapter zu. */
public final class UiCallingAdapter {

    private final FakeLuceneAdapter adapter = new FakeLuceneAdapter();

    public Object adapter() {
        return adapter;
    }
}
