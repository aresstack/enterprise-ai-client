package com.aresstack.enterpriseai.research.runtime.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * The local stage of the Research Agent: the content already indexed on this machine is searched with {@value #TOOL}
 * before any live source is asked. The tool runs the hybrid retrieval of the index (keyword and, where embeddings
 * exist, semantic), so the task is not pinned to one query form: one lookup passes the task as written (for the
 * semantic path and all of its terms), one passes its strongest literal terms (for exact keyword matches). Derived
 * without the model; a property of the agent, not of any source.
 */
public final class LocalIndexStrategy implements DiscoveryStrategy {

    /** The low-level tool searching only the local index, in the hit format of knowledge_search. */
    public static final String TOOL = "knowledge_index_search";

    public LocalIndexStrategy() {
    }

    @Override
    public String name() {
        return "local-index";
    }

    @Override
    public String planningRules() {
        return "";
    }

    @Override
    public boolean accepts(String query) {
        return query != null && !query.trim().isEmpty();
    }

    @Override
    public String fallbackQuery(String task) {
        return task == null ? "" : task.trim();
    }

    @Override
    public String tool() {
        return TOOL;
    }

    @Override
    public boolean planned() {
        return false;
    }

    /** The task as written, then its literal terms when they differ. */
    @Override
    public List<String> directQueries(String task) {
        List<String> queries = new ArrayList<String>();
        String request = task == null ? "" : task.trim();
        if (request.isEmpty()) {
            return queries;
        }
        queries.add(request);
        String literal = ResearchQueries.literalQuery(request);
        if (!literal.trim().isEmpty() && !literal.trim().equalsIgnoreCase(request)) {
            queries.add(literal.trim());
        }
        return queries;
    }

    @Override
    public String label(String query) {
        return "index: " + query;
    }
}
