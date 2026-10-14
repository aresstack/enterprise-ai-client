package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/**
 * Proposes mentions, relations and events for one paragraph (issue #108). Exchangeable: the first one asks the
 * configured chat model for a strict JSON answer; a dependency parser could implement the same contract later and be
 * compared on the same reference sentences. Proposals are never trusted as such ({@link StatementValidator}).
 */
public interface StatementExtractor {

    /** Whether the extractor can run now (e.g. a chat model is selected). */
    boolean isAvailable();

    /** Identifies extractor, prompt and model; part of the stored extractor version. */
    String version();

    /**
     * Blocks (model call): never on the Swing event thread, never inside a storage transaction.
     *
     * @throws StatementExtractionException if no proposal could be obtained
     */
    StatementCandidates extract(ParagraphText paragraph);
}
