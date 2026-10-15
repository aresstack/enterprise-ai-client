package com.aresstack.enterpriseai.research.runtime.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cheap first stage of the Research Agent: terms of the task spelled like acronyms or short identifiers (e.g.
 * {@code IRR}, {@code HaVdB}) are looked up in the structured findings of already analysed resources with
 * {@value #TOOL}, without the model and without a source search. A hit names a resource that states the term; it is
 * read like any other hit and is only evidence once read. A property of the term, not of any source: a task without
 * such a term skips this stage.
 */
public final class TermLookupStrategy implements DiscoveryStrategy {

    /** The low-level tool answering with the resources that state a term, in the hit format of knowledge_search. */
    public static final String TOOL = "knowledge_term_lookup";
    public static final String PARAM_TERM = "term";

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 12;

    public TermLookupStrategy() {
    }

    @Override
    public String name() {
        return "term";
    }

    @Override
    public String planningRules() {
        return "";
    }

    @Override
    public boolean accepts(String query) {
        return isTerm(query);
    }

    @Override
    public String fallbackQuery(String task) {
        List<String> terms = terms(task);
        return terms.isEmpty() ? "" : terms.get(0);
    }

    @Override
    public String tool() {
        return TOOL;
    }

    @Override
    public Map<String, Object> arguments(String query, int limit) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put(PARAM_TERM, query);
        args.put("limit", Integer.valueOf(limit));
        return args;
    }

    @Override
    public boolean planned() {
        return false;
    }

    @Override
    public List<String> directQueries(String task) {
        return terms(task);
    }

    @Override
    public String label(String query) {
        return "term: " + query;
    }

    /** The acronym-like terms of the task, in order, each once. */
    static List<String> terms(String task) {
        List<String> terms = new ArrayList<String>();
        if (task == null) {
            return terms;
        }
        for (String token : task.split("[^\\p{L}\\p{N}_-]+")) {
            String term = trimDashes(token);
            if (isTerm(term) && !terms.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    /**
     * Spelled like an acronym or short identifier: starts with a letter, at least two capitals, and capitals make up
     * at least half of the letters ({@code IRR}, {@code HaVdB}, {@code BS-PIMA}; not {@code Bedeutung}).
     */
    static boolean isTerm(String token) {
        if (token == null || token.length() < MIN_LENGTH || token.length() > MAX_LENGTH
                || !Character.isLetter(token.charAt(0))) {
            return false;
        }
        int letters = 0;
        int capitals = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    capitals++;
                }
            } else if (!Character.isDigit(c) && c != '-' && c != '_') {
                return false;
            }
        }
        return capitals >= 2 && capitals * 2 >= letters;
    }

    private static String trimDashes(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && (token.charAt(start) == '-' || token.charAt(start) == '_')) {
            start++;
        }
        while (end > start && (token.charAt(end - 1) == '-' || token.charAt(end - 1) == '_')) {
            end--;
        }
        return token.substring(start, end);
    }
}
