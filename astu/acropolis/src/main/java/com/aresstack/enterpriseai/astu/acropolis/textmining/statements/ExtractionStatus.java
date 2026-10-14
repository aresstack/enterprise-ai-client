package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/**
 * How far a machine-made statement got (issue #108); separate from a person's review, which no model ever sets.
 */
public enum ExtractionStatus {
    /** Structurally valid and grounded in the text (spans, sentence, predicate), not checked further. */
    EXTRACTED,
    /** A second, independent model call agreed that the sentence says exactly this. */
    MODEL_VALIDATED,
    /** The second call could not decide; keep as candidate only. */
    UNCERTAIN,
    /** A participant is a pronoun or another reference that is not resolved; nothing was guessed. */
    UNRESOLVED_REFERENCE,
    /** Machine-rejected: the check denied the claim. Not kept as a statement. */
    REJECTED
}
