package com.aresstack.enterpriseai.proasteion.application.memory;

/**
 * Asks the user to confirm a deletion with policy {@link MemoryPolicy#ASK}. The generic host approval of #145 (yellow
 * tool bubble) plugs in here; this package has no UI of its own.
 */
public interface MemoryDeletionApproval {

    /**
     * Blocks until the user decided. {@code true} only on explicit confirmation; reject, cancel and timeout are
     * {@code false}.
     */
    boolean approve(MemoryDeletionRequest request);
}
