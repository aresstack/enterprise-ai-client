package com.aresstack.enterpriseai.proasteion.exedra.ui.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: Die Oberfläche greift an der Application vorbei direkt auf einen Adapter zu. */
public final class UiCallingAdapter {

    private final FakeLuceneAdapter adapter = new FakeLuceneAdapter();

    public Object adapter() {
        return adapter;
    }
}
