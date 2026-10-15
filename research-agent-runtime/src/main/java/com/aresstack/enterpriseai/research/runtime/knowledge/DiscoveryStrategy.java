package com.aresstack.enterpriseai.research.runtime.knowledge;

/**
 * How the Research Agent turns a natural-language research task into discovery queries for the knowledge tools. A
 * property of the agent's current strategy, not of the knowledge sources: the task itself stays untouched, and later
 * strategies (semantic, fact/acronym lookup, hierarchy, relations) plug in here without changing the caller.
 */
public interface DiscoveryStrategy {

    /** Short name for diagnostics, e.g. {@code lexical}. */
    String name();

    /** The rules for the model's query planning under this strategy. */
    String planningRules();

    /** Whether a planned query fits this strategy (otherwise it is dropped). */
    boolean accepts(String query);

    /** A query derived without the model, when the model gives nothing usable. */
    String fallbackQuery(String task);
}
