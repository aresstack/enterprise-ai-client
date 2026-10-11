package com.aresstack.enterpriseai.proasteion.exedra.security.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneAdapter;

/**
 * Erlaubt: Ein Wert- oder Konfigurationstyp aus einem Adaptermodul (ohne Port-Schnittstelle, z. B.
 * {@code MediaWikiCredentials}, {@code KeePassRpcConfig}) darf überall in exedra gebaut werden.
 */
public final class BridgeBuildingAdapterValue {

    public Object build() {
        return new FakeLuceneAdapter();
    }
}
