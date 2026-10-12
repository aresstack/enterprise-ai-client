package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

/** The next step the plan proposes; a kind of action, not a tool. */
public enum NextAction {
    ANSWER_DIRECTLY,
    USE_APPLICATION_HELP,
    SEARCH_INTERNAL_KNOWLEDGE,
    SEARCH_EXTERNAL_KNOWLEDGE,
    /**
     * A term of the message is not resolved here and may have a meaning of its own in the organization: the client
     * searches the term as said first, answers with what it finds, and without a hit asks back, naming other readings
     * as questions only. The other readings are never searched automatically.
     */
    RESOLVE_INTERNAL_TERM,
    /** The message is not understood well enough; the clarifying question is the reply, the answer does not start. */
    ASK_CLARIFICATION,
    UNKNOWN
}
