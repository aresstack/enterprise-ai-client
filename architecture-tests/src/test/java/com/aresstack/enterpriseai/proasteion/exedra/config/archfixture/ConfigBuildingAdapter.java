package com.aresstack.enterpriseai.proasteion.exedra.config.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneIndex;

/** Absichtlicher Verstoß: Die Konfiguration baut einen Adapter, statt nur Snapshots zu liefern. */
public final class ConfigBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
