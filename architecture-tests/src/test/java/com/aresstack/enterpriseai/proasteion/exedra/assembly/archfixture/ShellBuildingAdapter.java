package com.aresstack.enterpriseai.proasteion.exedra.assembly.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneIndex;

/** Verboten: Die Schale (exedra) baut einen Adapter selbst, statt ihn vom Kompositionspunkt zu bekommen. */
public final class ShellBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
