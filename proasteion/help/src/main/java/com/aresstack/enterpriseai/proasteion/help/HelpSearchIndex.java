package com.aresstack.enterpriseai.proasteion.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory BM25 over the help, in two parts: one over chapters (title and keywords, which say what a chapter is
 * about) and one over sections (heading and body, which say where in the chapter the answer is). A section scores
 * its own match plus the match of its chapter. Query terms also match index terms they start or end at a reduced
 * weight, so German compounds are found by their parts ({@code anhang} finds {@code dateianhang}, {@code sprach}
 * finds {@code sprachausgabe}).
 *
 * <p>Built once from an immutable catalog and read-only afterwards, hence safe for concurrent searches.
 */
final class HelpSearchIndex {

    /** BM25 term saturation (Lucene default). */
    static final double K1 = 1.2;
    /** BM25 length normalisation (Lucene default). */
    static final double B = 0.75;
    static final int TITLE_WEIGHT = 2;
    static final int KEYWORD_WEIGHT = 1;
    static final int HEADING_WEIGHT = 2;
    static final int BODY_WEIGHT = 1;
    /** Weight of a partial (prefix or suffix) match relative to an exact term match. */
    static final double PARTIAL_MATCH = 0.5;
    /** Shortest query term that may match parts of longer terms. */
    static final int MIN_PARTIAL_LENGTH = 4;

    private final Bm25 chapters = new Bm25();
    private final Bm25 sections = new Bm25();
    private final List<HelpTopic> topicOfSection = new ArrayList<HelpTopic>();
    private final List<HelpSection> sectionList = new ArrayList<HelpSection>();
    private final Map<String, Integer> chapterOf = new HashMap<String, Integer>();
    private final Set<String> vocabulary = new LinkedHashSet<String>();

    HelpSearchIndex(HelpCatalog catalog) {
        for (HelpTopic topic : catalog.topics()) {
            Map<String, Integer> chapter = new HashMap<String, Integer>();
            add(chapter, topic.title(), TITLE_WEIGHT);
            for (String keyword : topic.keywords()) {
                add(chapter, keyword, KEYWORD_WEIGHT);
            }
            chapterOf.put(topic.id(), chapters.add(chapter));
            for (HelpSection section : topic.sections()) {
                Map<String, Integer> terms = new HashMap<String, Integer>();
                add(terms, section.heading(), HEADING_WEIGHT);
                add(terms, section.markdown(), BODY_WEIGHT);
                sections.add(terms);
                topicOfSection.add(topic);
                sectionList.add(section);
            }
        }
        chapters.seal();
        sections.seal();
        vocabulary.addAll(chapters.documentFrequency.keySet());
        vocabulary.addAll(sections.documentFrequency.keySet());
    }

    private static void add(Map<String, Integer> frequencies, String text, int weight) {
        for (String term : HelpText.terms(text)) {
            Integer known = frequencies.get(term);
            frequencies.put(term, known == null ? weight : known + weight);
        }
    }

    /**
     * Scored sections for {@code query}, best first; sections without any matching term are left out. Each query term
     * counts once per part: with its exact match or, failing that, its best partial match.
     */
    List<ScoredSection> search(String query) {
        List<String> queryTerms = new ArrayList<String>();
        List<Map<String, Double>> expanded = new ArrayList<Map<String, Double>>();
        for (String term : new LinkedHashSet<String>(HelpText.terms(query))) {
            Map<String, Double> matches = expand(term);
            if (!matches.isEmpty()) {
                queryTerms.add(term);
                expanded.add(matches);
            }
        }
        if (expanded.isEmpty()) {
            return Collections.emptyList();
        }
        List<Match> chapterScores = new ArrayList<Match>();
        for (int i = 0; i < chapters.size(); i++) {
            chapterScores.add(chapters.score(i, queryTerms, expanded));
        }
        List<ScoredSection> scored = new ArrayList<ScoredSection>();
        for (int i = 0; i < sections.size(); i++) {
            HelpTopic topic = topicOfSection.get(i);
            Match chapter = chapterScores.get(chapterOf.get(topic.id()));
            Match section = sections.score(i, queryTerms, expanded);
            double score = chapter.score + section.score;
            if (score > 0) {
                Set<String> matched = new LinkedHashSet<String>(section.terms);
                matched.addAll(chapter.terms);
                scored.add(new ScoredSection(topic, sectionList.get(i), score, new ArrayList<String>(matched)));
            }
        }
        Collections.sort(scored, (a, b) -> Double.compare(b.score, a.score));
        return scored;
    }

    /** The query term itself plus index terms it starts or ends, with the weight of each match. */
    private Map<String, Double> expand(String query) {
        Map<String, Double> expanded = new LinkedHashMap<String, Double>();
        if (vocabulary.contains(query)) {
            expanded.put(query, 1.0);
        }
        if (query.length() < MIN_PARTIAL_LENGTH) {
            return expanded;
        }
        for (String term : vocabulary) {
            if (!term.equals(query) && (term.startsWith(query) || term.endsWith(query))) {
                expanded.put(term, PARTIAL_MATCH);
            }
        }
        return expanded;
    }

    int size() {
        return sections.size();
    }

    /** One hit of the index: a section of a topic with its score and the index terms that matched. */
    static final class ScoredSection {
        final HelpTopic topic;
        final HelpSection section;
        final double score;
        final List<String> matchedTerms;

        ScoredSection(HelpTopic topic, HelpSection section, double score, List<String> matchedTerms) {
            this.topic = topic;
            this.section = section;
            this.score = score;
            this.matchedTerms = matchedTerms;
        }
    }

    private static final class Match {
        final double score;
        final List<String> terms;

        Match(double score, List<String> terms) {
            this.score = score;
            this.terms = terms;
        }
    }

    /** One BM25 index over weighted term frequencies. */
    private static final class Bm25 {
        final List<Map<String, Integer>> entries = new ArrayList<Map<String, Integer>>();
        final List<Integer> lengths = new ArrayList<Integer>();
        final Map<String, Integer> documentFrequency = new HashMap<String, Integer>();
        double averageLength = 1;

        int add(Map<String, Integer> frequencies) {
            int length = 0;
            for (Map.Entry<String, Integer> term : frequencies.entrySet()) {
                length += term.getValue();
                Integer known = documentFrequency.get(term.getKey());
                documentFrequency.put(term.getKey(), known == null ? 1 : known + 1);
            }
            entries.add(frequencies);
            lengths.add(length);
            return entries.size() - 1;
        }

        void seal() {
            long total = 0;
            for (int length : lengths) {
                total += length;
            }
            averageLength = entries.isEmpty() ? 1 : Math.max(1.0, (double) total / entries.size());
        }

        int size() {
            return entries.size();
        }

        double idf(String term) {
            Integer df = documentFrequency.get(term);
            double frequency = df == null ? 0 : df;
            return Math.log(1 + (entries.size() - frequency + 0.5) / (frequency + 0.5));
        }

        Match score(int index, List<String> queryTerms, List<Map<String, Double>> expanded) {
            Map<String, Integer> frequencies = entries.get(index);
            int length = lengths.get(index);
            double score = 0;
            List<String> matched = new ArrayList<String>();
            for (int q = 0; q < expanded.size(); q++) {
                String queryTerm = queryTerms.get(q);
                // An exact match wins; a partial match counts only without one and never more than the query term
                // itself would (a rare compound must not outrank the word that was asked for).
                boolean exact = frequencies.containsKey(queryTerm);
                double best = 0;
                String bestTerm = null;
                for (Map.Entry<String, Double> term : expanded.get(q).entrySet()) {
                    Integer frequency = frequencies.get(term.getKey());
                    if (frequency == null || (exact && !term.getKey().equals(queryTerm))) {
                        continue;
                    }
                    double idf = idf(term.getKey());
                    if (!term.getKey().equals(queryTerm) && documentFrequency.containsKey(queryTerm)) {
                        idf = Math.min(idf, idf(queryTerm));
                    }
                    double tf = frequency;
                    double norm = tf + K1 * (1 - B + B * length / averageLength);
                    double value = term.getValue() * idf * (tf * (K1 + 1)) / norm;
                    if (value > best) {
                        best = value;
                        bestTerm = term.getKey();
                    }
                }
                if (bestTerm != null) {
                    score += best;
                    matched.add(bestTerm);
                }
            }
            return new Match(score, matched);
        }
    }
}
