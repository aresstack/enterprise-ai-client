package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;

/**
 * Where the statements of a document are kept (outbound port; the composition root connects the metadata catalog of
 * issue #106). Every call blocks on storage: never on the Swing event thread.
 */
public interface StatementSink {

    /** Whether statements of this extractor state were already stored for the same content (hash, else revision). */
    boolean isCurrent(KnowledgeResourceRef resource, String extractorId, String extractorVersion);

    /**
     * Replace the automatic statements of this extractor on this resource in one step. Statements a person reviewed
     * or added, and those of other extractors, stay.
     *
     * @param documentText the canonical text the spans of {@code statements} refer to (for short evidence quotes)
     * @throws RuntimeException if storing fails; then nothing changed
     */
    void replace(KnowledgeResourceRef resource, String extractorId, String extractorVersion, String documentText,
                 DocumentStatements statements);
}
