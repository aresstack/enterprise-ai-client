package com.aresstack.enterpriseai.proasteion.help;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Searches the product help: BM25 over all sections, at most one hit per topic (its best section), so a question
 * returns several chapters instead of several sections of one chapter.
 */
public final class HelpSearchUseCase {

    /** Characters of context shown before the first matching word of a snippet. */
    static final int SNIPPET_LEAD = 60;

    private final HelpSearchIndex index;

    public HelpSearchUseCase(HelpCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog is required");
        }
        this.index = new HelpSearchIndex(catalog);
    }

    /**
     * Up to {@code limit} hits for {@code query}, best first.
     *
     * @param snippetChars maximum length of each snippet
     */
    public List<HelpHit> search(String query, int limit, int snippetChars) {
        List<HelpHit> hits = new ArrayList<HelpHit>();
        if (query == null || query.trim().isEmpty() || limit < 1) {
            return hits;
        }
        List<HelpSearchIndex.ScoredSection> scored = index.search(query);
        if (scored.isEmpty()) {
            return hits;
        }
        double best = scored.get(0).score;
        Set<String> topics = new HashSet<String>();
        for (HelpSearchIndex.ScoredSection candidate : scored) {
            if (!topics.add(candidate.topic.id())) {
                continue;
            }
            hits.add(new HelpHit(candidate.topic, candidate.section, candidate.score / best,
                    snippet(candidate.section, candidate.matchedTerms, snippetChars)));
            if (hits.size() >= limit) {
                break;
            }
        }
        return hits;
    }

    /** Number of indexed sections (all topics). */
    public int sectionCount() {
        return index.size();
    }

    static String snippet(HelpSection section, List<String> matchedTerms, int maxChars) {
        String text = HelpText.plain(section.markdown());
        int at = firstMatch(text, new HashSet<String>(matchedTerms));
        int start = Math.max(0, at - SNIPPET_LEAD);
        if (start > 0) {
            int space = text.indexOf(' ', start);
            start = space < 0 || space > at ? start : space + 1;
        }
        int end = Math.min(text.length(), start + Math.max(1, maxChars));
        String excerpt = text.substring(start, end);
        return (start > 0 ? "… " : "") + excerpt + (end < text.length() ? " …" : "");
    }

    /** Offset of the first word whose stem is a matched term, or 0. */
    private static int firstMatch(String text, Set<String> matched) {
        int start = -1;
        for (int i = 0; i <= text.length(); i++) {
            boolean letter = i < text.length() && Character.isLetterOrDigit(text.charAt(i));
            if (letter && start < 0) {
                start = i;
            } else if (!letter && start >= 0) {
                List<String> terms = HelpText.terms(text.substring(start, i));
                if (!terms.isEmpty() && matched.contains(terms.get(0))) {
                    return start;
                }
                start = -1;
            }
        }
        return 0;
    }
}
