package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The one place where statement terms become search values (issue #108). A search value is for finding, never an
 * identity: equal values from two documents are two mentions. Versioned by {@link #VERSION}; the method stored with
 * each value says whether it came from a proposed base form (unverified) or from the surface form.
 *
 * <p>Rules (version 1): Unicode NFC, lower case (locale-independent), inner whitespace collapsed, quotes and
 * punctuation around the term removed. From a surface form, leading articles and determiners are dropped as well
 * ("den eingereichten Beleg" becomes "eingereichten beleg"); no inflection is undone, because there is no verified
 * German lemmatizer.
 */
public final class TermNormalizer {

    /** Version of the rules; part of every method name. */
    public static final String VERSION = "1";
    /** Method of a value made from a base form proposed by a model (unverified). */
    public static final String FROM_LEMMA_CANDIDATE = "lemma-candidate/nfc-lower/" + VERSION;
    /** Method of a value made from the text itself without articles. */
    public static final String FROM_SURFACE = "surface-without-determiner/nfc-lower/" + VERSION;
    /** Status of every proposed base form: no morphological check confirms it. */
    public static final String LEMMA_STATUS = "PROPOSED_UNVERIFIED";

    private static final Set<String> DETERMINERS = new HashSet<String>(Arrays.asList("der", "die", "das", "den", "dem",
            "des", "ein", "eine", "einen", "einem", "einer", "eines", "the", "a", "an"));
    private static final String EDGE = "\"'„“”‚‘’«»()[]{}.,;:!?";

    private TermNormalizer() {
    }

    /** The search value of a proposed base form. */
    public static String fromLemma(String lemma) {
        return basic(lemma);
    }

    /** The search value of a surface form: without leading articles. */
    public static String fromSurface(String surface) {
        String value = basic(surface);
        String[] words = value.split(" ");
        int first = 0;
        while (first < words.length - 1 && DETERMINERS.contains(words[first])) {
            first++;
        }
        StringBuilder rest = new StringBuilder();
        for (int i = first; i < words.length; i++) {
            if (rest.length() > 0) {
                rest.append(' ');
            }
            rest.append(words[i]);
        }
        return rest.toString();
    }

    private static String basic(String text) {
        if (text == null) {
            return "";
        }
        String value = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
        int start = 0;
        int end = value.length();
        while (start < end && EDGE.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && EDGE.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end).trim();
    }
}
