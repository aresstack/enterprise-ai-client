package com.aresstack.enterpriseai.source.ndv.pal.transaction.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.api.IPalTypeObject;

/**
 * Ergebnis einer Quellcode-Suche nach Objektname.
 */
public interface ISourceLookupResult {

    IPalTypeObject getObject();

    String getLibrary();

    int getDatabaseId();

    int getFileNumber();
}

