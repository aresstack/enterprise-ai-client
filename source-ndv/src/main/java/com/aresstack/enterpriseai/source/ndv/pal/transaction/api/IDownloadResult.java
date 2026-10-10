package com.aresstack.enterpriseai.source.ndv.pal.transaction.api;

/**
 * Ergebnis eines Quellcode-Downloads.
 */
public interface IDownloadResult {

    String[] getSource();

    int getLineIncrement();
}
