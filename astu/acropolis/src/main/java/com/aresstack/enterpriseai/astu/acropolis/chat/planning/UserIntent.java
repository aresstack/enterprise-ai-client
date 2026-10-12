package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

/** What the user does with a message, as the answer planning step reads it. */
public enum UserIntent {
    GREETING,
    SMALL_TALK,
    QUESTION,
    REQUEST_DEFINITION,
    REQUEST_EXPLANATION,
    REQUEST_SEARCH,
    TASK,
    ASSERTION,
    FEEDBACK,
    UNKNOWN
}
