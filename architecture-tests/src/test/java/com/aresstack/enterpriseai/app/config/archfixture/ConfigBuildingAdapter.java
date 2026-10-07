package com.aresstack.enterpriseai.app.config.archfixture;

import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeIndexPortContract;
import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneIndex;

/** Absichtlicher Verstoß: Die Konfiguration baut einen Adapter, statt nur Snapshots zu liefern. */
public final class ConfigBuildingAdapter {

    public FakeIndexPortContract build() {
        return new FakeLuceneIndex();
    }
}
