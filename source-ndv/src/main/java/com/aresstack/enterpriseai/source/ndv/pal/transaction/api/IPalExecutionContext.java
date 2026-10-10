package com.aresstack.enterpriseai.source.ndv.pal.transaction.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.api.IPalTypeLibId;

/**
 * Schnittstelle für den Anwendungsausführungskontext.
 */
public interface IPalExecutionContext {

    IPalTypeLibId[] getLibrarySearchOrder(String library);

    EStepLibFormat getLibrarySearchOrderFormat(String library);
}

