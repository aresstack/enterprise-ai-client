package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

/** The next step the plan proposes; a kind of action, not a tool. */
public enum NextAction {
    ANSWER_DIRECTLY,
    USE_APPLICATION_HELP,
    SEARCH_INTERNAL_KNOWLEDGE,
    SEARCH_EXTERNAL_KNOWLEDGE,
    /** The message is not understood well enough; the clarifying question is the reply, the answer does not start. */
    ASK_CLARIFICATION,
    UNKNOWN
}
