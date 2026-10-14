package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One sentence of a paragraph with its words, all as spans in the canonical document text. Words are numbered from 1
 * for the model; punctuation is no word.
 */
public final class SentenceText {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:[-'’.][\\p{L}\\p{N}]+)*");

    private final int number;
    private final TextSpan span;
    private final String text;
    private final List<TextSpan> words;

    private SentenceText(int number, TextSpan span, String text, List<TextSpan> words) {
        this.number = number;
        this.span = span;
        this.text = text;
        this.words = Collections.unmodifiableList(words);
    }

    /**
     * @param documentText the canonical text
     * @param number       1-based number within its paragraph
     */
    static SentenceText of(String documentText, int number, TextSpan span) {
        List<TextSpan> words = new ArrayList<TextSpan>();
        Matcher word = WORD.matcher(documentText).region(span.start(), span.end());
        while (word.find()) {
            words.add(new TextSpan(word.start(), word.end()));
        }
        return new SentenceText(number, span, span.of(documentText), words);
    }

    /** 1-based number within its paragraph. */
    public int number() {
        return number;
    }

    public TextSpan span() {
        return span;
    }

    public String text() {
        return text;
    }

    /** The words in order, as spans in the document text. */
    public List<TextSpan> words() {
        return words;
    }

    /** The span from word {@code first} to word {@code last} (1-based, inclusive), or {@code null} if out of range. */
    public TextSpan wordRange(int first, int last) {
        if (first < 1 || last < first || last > words.size()) {
            return null;
        }
        return new TextSpan(words.get(first - 1).start(), words.get(last - 1).end());
    }

    /** Whether {@code inner} lies inside this sentence. */
    public boolean contains(TextSpan inner) {
        return inner.start() >= span.start() && inner.end() <= span.end();
    }

    /** The sentence with numbered words, as the model sees it: {@code [1]Die [2]zuständige ...}. */
    String numbered(String documentText) {
        StringBuilder out = new StringBuilder();
        int cursor = span.start();
        for (int i = 0; i < words.size(); i++) {
            TextSpan word = words.get(i);
            out.append(documentText, cursor, word.start()).append('[').append(i + 1).append(']')
                    .append(documentText, word.start(), word.end());
            cursor = word.end();
        }
        out.append(documentText, cursor, span.end());
        return out.toString().replaceAll("\\s+", " ").trim();
    }
}
