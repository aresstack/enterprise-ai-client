package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

/** Which kind of knowledge an answer needs; names no tool, the chat model chooses its action itself. */
public enum InformationNeed {
    /** Operating, functions or settings of this application. */
    APPLICATION_HELP,
    /** Knowledge of the user's organization. */
    INTERNAL_KNOWLEDGE,
    /** Unambiguous general knowledge. */
    GENERAL_KNOWLEDGE,
    /** The meaning of a term that is not resolved in the current context. */
    TERM_RESOLUTION,
    /** Greeting, small talk, thanks, statement, opinion, or a task without knowledge. */
    NO_INFORMATION_NEEDED,
    UNKNOWN
}
