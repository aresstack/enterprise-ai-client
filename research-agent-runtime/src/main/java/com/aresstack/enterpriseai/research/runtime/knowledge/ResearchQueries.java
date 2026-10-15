package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Query formulation of the Research Agent: how a natural-language research request becomes short search expressions
 * for the knowledge sources. The model does the reasoning ({@link #RULES} go into its prompt); this class holds the
 * shared rules, reads the model's plan and supplies the deterministic fallbacks when the model gives nothing usable.
 * Lexical sources (e.g. a full-text search that joins all words with AND) never see the whole sentence.
 * Pure and stateless.
 */
public final class ResearchQueries {

    /** At most this many words in one search expression; longer ones are a sentence, not a query. */
    public static final int MAX_QUERY_WORDS = 6;
    /** Strong literal terms of the {@link #literalQuery} fallback. */
    static final int LITERAL_TERMS = 3;

    /**
     * The query rules for the model, shared by every planning prompt of the Research Agent: short keyword queries,
     * internal identifiers kept literally, no paraphrasing and no synonym chains.
     */
    public static final String RULES = "Suchanfragen sind kurze Schlüsselwortfolgen mit 1 bis 3 Wörtern (höchstens "
            + MAX_QUERY_WORDS + "), keine Fragen und keine ganzen Sätze, ohne Füllwörter, denn die Volltextsuche der "
            + "Quellen verknüpft alle Wörter mit UND. Übernimm interne Bezeichner, Abkürzungen, System-, Produkt- und "
            + "Projektnamen wörtlich und in ihrer Schreibweise (z. B. HaVdB, BS-PIMA, IAR, RSL, Hashicorp Vault); "
            + "übersetze, erkläre oder umschreibe sie nie. Keine Synonymketten und keine Varianten derselben Begriffe.";

    private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(
            "aber", "alle", "als", "auch", "auf", "aus", "bei", "bitte", "das", "dass", "dem", "den", "der", "des",
            "die", "dir", "doch", "du", "ein", "eine", "einen", "einer", "etwas", "finde", "finden", "für", "gibt",
            "hat", "ich", "ist", "kannst", "mal", "mehr", "mir", "mit", "nach", "noch", "oder", "sich", "sind",
            "über", "und", "uns", "von", "was", "welche", "welcher", "wer", "wie", "wir", "wo", "zu", "zum", "zur",
            "confluence", "wiki", "infos", "informationen", "information", "suche", "such", "genau", "the", "and",
            "what", "about", "find"));

    /** Words of a request sentence that never make a literal search term (in addition to {@link #STOP_WORDS}). */
    private static final Set<String> REQUEST_WORDS = new HashSet<String>(Arrays.asList(
            "am", "an", "im", "in", "es", "er", "sie", "wird", "werden", "wurde", "wurden", "beschrieben",
            "beschreibt", "heraus", "bedeutet", "bedeutung", "haben", "kann", "soll", "unternehmenswissen",
            "internen", "interne", "internes", "unternehmen", "welches", "welchen", "warum", "wann", "erkläre",
            "erklären", "recherchiere", "zeige", "nenne", "gib", "is", "are", "how", "which", "does", "of", "in",
            "for", "with", "an", "a"));

    private static final Pattern SEARCH_REQUEST = Pattern.compile(
            "\\b(such\\w*|recherch\\w*|finde\\w*|find|search\\w*|nachschlagen|schau\\w* nach|look up)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}\\p{N}_-]+");
    private static final int PAGE_EXCERPT_CHARS = 3000;

    private ResearchQueries() {
    }

    /**
     * An explicit search request ("Suche in Confluence nach …", "recherchiere …", "finde Informationen zu …"): then
     * the agent always searches, even if the model sees it differently.
     */
    public static boolean asksForSearch(String message) {
        return message != null && SEARCH_REQUEST.matcher(message).find();
    }

    /** Whether {@code query} is a search expression and not a sentence (at most {@link #MAX_QUERY_WORDS} words). */
    public static boolean isConcise(String query) {
        String trimmed = query == null ? "" : query.trim();
        return !trimmed.isEmpty() && trimmed.split("\\s+").length <= MAX_QUERY_WORDS;
    }

    /**
     * The queries of a model plan ({@code {"search": …, "queries": […]}}).
     *
     * @return {@code null} when the answer is no plan; empty for {@code search=false}
     */
    public static List<String> parsePlan(String text, int maxQueries) {
        String json = clean(text);
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        Object parsed;
        try {
            parsed = MiniJson.parse(json.substring(start, end + 1));
        } catch (RuntimeException invalid) {
            return null;
        }
        if (!(parsed instanceof Map)) {
            return null;
        }
        Map<?, ?> plan = (Map<?, ?>) parsed;
        Object search = plan.get("search");
        if (Boolean.FALSE.equals(search) || "false".equals(String.valueOf(search))) {
            return new ArrayList<String>();
        }
        Set<String> queries = new LinkedHashSet<String>();
        if (plan.get("queries") instanceof List) {
            for (Object query : (List<?>) plan.get("queries")) {
                String value = query == null ? "" : query.toString().trim();
                if (!value.isEmpty() && queries.size() < maxQueries) {
                    queries.add(value);
                }
            }
        }
        return queries.isEmpty() ? null : new ArrayList<String>(queries);
    }

    /** Fallback without model: the first keywords of the message, one by one and the first two together. */
    public static List<String> keywordQueries(String message, int maxQueries) {
        List<String> words = new ArrayList<String>();
        for (String word : WORD_SPLIT.split(message)) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (word.length() >= 2 && !STOP_WORDS.contains(lower) && !words.contains(word)) {
                words.add(word);
            }
        }
        List<String> queries = new ArrayList<String>();
        for (String word : words) {
            if (queries.size() >= maxQueries - 1) {
                break;
            }
            queries.add(word);
        }
        if (words.size() > 1 && queries.size() < maxQueries) {
            queries.add(words.get(0) + " " + words.get(1));
        }
        if (queries.isEmpty() && !message.trim().isEmpty()) {
            queries.add(shorten(message.trim()));
        }
        return queries;
    }

    /**
     * Fallback without model for a lexical source: the strongest literal terms of the request as one short query.
     * Identifier-like words come first (acronyms, mixed case such as {@code HaVdB}, digits, hyphens such as {@code
     * BS-PIMA}), then capitalized names, in the order of the request; question and filler words never count.
     */
    public static String literalQuery(String request) {
        String text = request == null ? "" : request.trim();
        final List<String> words = new ArrayList<String>();
        for (String word : WORD_SPLIT.split(text)) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (word.length() >= 2 && !STOP_WORDS.contains(lower) && !REQUEST_WORDS.contains(lower)
                    && !words.contains(word)) {
                words.add(word);
            }
        }
        List<String> ranked = new ArrayList<String>(words);
        Collections.sort(ranked, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int strength = strength(b) - strength(a);
                return strength != 0 ? strength : words.indexOf(a) - words.indexOf(b);
            }
        });
        List<String> chosen = ranked.subList(0, Math.min(LITERAL_TERMS, ranked.size()));
        StringBuilder query = new StringBuilder();
        for (String word : words) {
            if (chosen.contains(word)) {
                query.append(query.length() == 0 ? "" : " ").append(word);
            }
        }
        return query.length() == 0 ? shorten(text) : query.toString();
    }

    /** 2: identifier-like, 1: capitalized name, 0: ordinary word. */
    static int strength(String word) {
        boolean digit = false;
        int upper = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            digit |= Character.isDigit(c);
            if (Character.isUpperCase(c)) {
                upper++;
            }
        }
        if (digit || word.indexOf('-') > 0 || upper >= 2) {
            return 2;
        }
        return upper == 1 && Character.isUpperCase(word.charAt(0)) ? 1 : 0;
    }

    /**
     * The best part of a page for the question: paragraphs ordered by the number of search terms, in page order up to
     * {@value #PAGE_EXCERPT_CHARS} characters; without matches the beginning of the page.
     */
    public static String excerpt(String page, String terms) {
        String[] paragraphs = page.split("\\r?\\n\\s*\\r?\\n|\\r?\\n");
        Set<String> words = new HashSet<String>();
        for (String word : terms.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        final int[] scores = new int[paragraphs.length];
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < paragraphs.length; i++) {
            String lower = paragraphs[i].toLowerCase(Locale.ROOT);
            for (String word : words) {
                if (lower.contains(word)) {
                    scores[i]++;
                }
            }
            order.add(Integer.valueOf(i));
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return scores[b.intValue()] != scores[a.intValue()] ? scores[b.intValue()] - scores[a.intValue()]
                        : a.intValue() - b.intValue();
            }
        });
        boolean[] chosen = new boolean[paragraphs.length];
        int length = 0;
        for (Integer index : order) {
            String paragraph = paragraphs[index.intValue()].trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (length > 0 && length + paragraph.length() > PAGE_EXCERPT_CHARS) {
                continue;
            }
            chosen[index.intValue()] = true;
            length += paragraph.length() + 1;
            if (length >= PAGE_EXCERPT_CHARS) {
                break;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            if (chosen[i]) {
                text.append(text.length() == 0 ? "" : "\n").append(paragraphs[i].trim());
            }
        }
        return text.length() > PAGE_EXCERPT_CHARS ? text.substring(0, PAGE_EXCERPT_CHARS) + "…" : text.toString();
    }

    /** Without code fence and framing blank lines. */
    public static String clean(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            trimmed = firstBreak < 0 ? "" : trimmed.substring(firstBreak + 1);
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private static String shorten(String text) {
        String[] words = text.split("\\s+");
        if (words.length <= MAX_QUERY_WORDS) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < MAX_QUERY_WORDS; i++) {
            out.append(i == 0 ? "" : " ").append(words[i]);
        }
        return out.toString();
    }
}
