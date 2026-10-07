package com.aresstack.enterpriseai.app.security.archfixture;

import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;

/**
 * Erlaubt: Ein Wert- oder Konfigurationstyp aus einem Adaptermodul (ohne Port-Schnittstelle, z. B.
 * {@code MediaWikiCredentials}, {@code KeePassRpcConfig}) darf überall in app-swing gebaut werden.
 */
public final class BridgeBuildingAdapterValue {

    public Object build() {
        return new FakeLuceneAdapter();
    }
}
