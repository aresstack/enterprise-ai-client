package com.aresstack.enterpriseai.proasteion.help;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Text helpers of the help search: folding, tokenizing with a light German/English stemmer and turning Markdown
 * into plain text for snippets. Stateless.
 */
public final class HelpText {

    /** Shortest token that counts; shorter ones (single letters) carry no meaning in the help. */
    static final int MIN_TOKEN_LENGTH = 2;
    /** Shortest stem a suffix may be cut down to. */
    static final int MIN_STEM_LENGTH = 4;
    /** Suffixes cut once, longest first ({@code einstellungen → einstellung}, {@code dateien → datei}). */
    private static final String[] SUFFIXES = {"ungen", "ern", "en", "er", "es", "e", "n", "s"};

    /** Words that occur in nearly every question and chapter; they only add noise to BM25. */
    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines", "und", "oder",
            "aber", "ist", "sind", "war", "wird", "werden", "wurde", "kann", "kannst", "koennen", "muss", "musst",
            "ich", "du", "er", "sie", "es", "wir", "ihr", "mein", "meine", "dein", "deine", "mir", "mich", "dir",
            "dich", "mit", "von", "vom", "zu", "zum", "zur", "im", "in", "an", "am", "auf", "aus", "bei", "fuer",
            "ueber", "unter", "nach", "vor", "wie", "wo", "was", "warum", "wieso", "weshalb", "wann", "welche",
            "welcher", "welches", "nicht", "kein", "keine", "auch", "noch", "nur", "so", "da", "dann", "wenn", "als",
            "ob", "dass", "man", "sich", "hier", "dort", "the", "a", "an", "and", "or", "of", "to", "in", "on", "is",
            "are", "how", "what", "where", "why", "do", "does", "can", "my", "i", "it", "for", "with",
            // Verbs of how-to questions ("Wo stelle ich ... ein?", "Wo finde ich ...?") that say nothing about the topic.
            "stelle", "stellt", "stellen", "finde", "findet", "finden", "will", "willst", "moechte", "moechtest",
            "mache", "machen", "aendere", "aendern", "gehe", "geht", "benutze", "nutze", "bekomme", "sehe", "sieht")));

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|`)");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private HelpText() {
    }

    /** Lower case with umlauts folded ({@code ä → ae}, {@code ß → ss}). */
    static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder folded = new StringBuilder(lower.length() + 8);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'ä':
                    folded.append("ae");
                    break;
                case 'ö':
                    folded.append("oe");
                    break;
                case 'ü':
                    folded.append("ue");
                    break;
                case 'ß':
                    folded.append("ss");
                    break;
                default:
                    folded.append(c);
            }
        }
        return folded.toString();
    }

    /** Stemmed index terms of {@code text} in order, stop words removed. */
    static List<String> terms(String text) {
        List<String> terms = new ArrayList<String>();
        if (text == null) {
            return terms;
        }
        String folded = fold(text);
        int start = -1;
        for (int i = 0; i <= folded.length(); i++) {
            boolean letter = i < folded.length() && Character.isLetterOrDigit(folded.charAt(i));
            if (letter && start < 0) {
                start = i;
            } else if (!letter && start >= 0) {
                String token = folded.substring(start, i);
                if (token.length() >= MIN_TOKEN_LENGTH && !STOP_WORDS.contains(token)) {
                    terms.add(stem(token));
                }
                start = -1;
            }
        }
        return terms;
    }

    /** Cuts one common inflection suffix while at least {@link #MIN_STEM_LENGTH} characters remain. */
    static String stem(String token) {
        for (String suffix : SUFFIXES) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= MIN_STEM_LENGTH) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    /** Markdown as one line of plain text: links keep their label, emphasis and heading marks vanish. */
    static String plain(String markdown) {
        String text = LINK.matcher(markdown).replaceAll("$1");
        text = HEADING.matcher(text).replaceAll("");
        text = EMPHASIS.matcher(text).replaceAll("");
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /** Cuts {@code text} to at most {@code maxChars} characters with a marker naming the omitted length. */
    public static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        String marker = "\n… [gekürzt: " + text.length() + " Zeichen gesamt; einzelne Abschnitte mit #anker lesen]";
        int cut = Math.max(0, maxChars - marker.length());
        int lineBreak = text.lastIndexOf('\n', cut);
        if (lineBreak > cut / 2) {
            cut = lineBreak;
        }
        return text.substring(0, cut) + marker;
    }
}
