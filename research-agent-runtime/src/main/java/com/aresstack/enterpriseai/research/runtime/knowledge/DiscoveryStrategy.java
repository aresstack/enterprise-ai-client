package com.aresstack.enterpriseai.research.runtime.knowledge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the Research Agent turns a natural-language research task into discovery lookups for the knowledge tools. A
 * property of the agent's current strategy, not of the knowledge sources: the task itself stays untouched.
 *
 * <p>{@link KnowledgeResearch} runs an ordered list of strategies as stages: cheap ones first, each lookup's hits are
 * read and judged, and a later stage runs only when the evidence so far does not answer the task. A strategy names
 * the tool it asks (default {@value KnowledgeResearch#SEARCH_TOOL}) and whether the model plans its lookups
 * ({@link #planned()}) or they follow from the task without the model ({@link #directQueries}). Every tool answers in
 * the hit format of {@code knowledge_search}, so all stages are read and cited the same way.
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

    /** The knowledge tool this stage asks; it must answer in the hit format of {@code knowledge_search}. */
    default String tool() {
        return KnowledgeResearch.SEARCH_TOOL;
    }

    /** The arguments of one lookup. */
    default Map<String, Object> arguments(String query, int limit) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", query);
        args.put("limit", Integer.valueOf(limit));
        return args;
    }

    /** Whether the model plans the lookups ({@link #planningRules}); otherwise {@link #directQueries} gives them. */
    default boolean planned() {
        return true;
    }

    /** The lookups of an unplanned stage, derived from the task without the model; empty skips the stage. */
    default List<String> directQueries(String task) {
        return Collections.emptyList();
    }

    /** How a lookup is listed among the searched expressions of the result. */
    default String label(String query) {
        return query;
    }
}
