package com.aresstack.enterpriseai.source.ndv.pal.transaction.api;

import com.aresstack.enterpriseai.source.ndv.pal.core.api.IPalTypeDbgaRecord;

/**
 * Callback-Schnittstelle für Debug-Attach-Wartevorgang.
 */
public interface IDebugAttachWaitCallBack {

    boolean isAborted();

    void recordFound(IPalTypeDbgaRecord record);
}

