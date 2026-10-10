package com.aresstack.enterpriseai.application.resource.policy;

/**
 * Classification of the caller requesting a resource operation.
 */
public enum ActorType {
    /** A natural human user. */
    HUMAN,
    /** A bot or AI agent acting autonomously or on behalf of a user. */
    BOT,
    /** A backend service or system process. */
    SERVICE
}
