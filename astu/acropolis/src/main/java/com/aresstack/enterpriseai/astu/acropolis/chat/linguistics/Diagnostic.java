package com.aresstack.enterpriseai.astu.acropolis.chat.linguistics;

/** Findings about the form of a message that may matter for understanding it; hints, never conclusions. */
public enum Diagnostic {

    /** At least half of the words were tagged with a probability below one half. */
    LOW_PARSE_CONFIDENCE,

    /** A word was tagged with a probability below one half. */
    LOW_CONFIDENCE_TOKEN,

    /** A sentence of at least three words has no verb at all. */
    NO_PLAUSIBLE_PREDICATE,

    /** A dictated message whose form suggests a speech recognition error (see the suspected tokens). */
    POSSIBLE_ASR_ERROR
}
