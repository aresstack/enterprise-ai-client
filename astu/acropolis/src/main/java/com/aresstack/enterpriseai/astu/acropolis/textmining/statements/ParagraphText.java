package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One unit of analysis: a paragraph of the canonical document text (or a run of its sentences when the paragraph is
 * longer than the limit), split into sentences with word spans. Everything points into the document text, so a
 * statement keeps its place in exactly this document version.
 */
public final class ParagraphText {

    private static final Pattern BLANK_LINES = Pattern.compile("\\n[ \\t]*\\n");

    private final String documentText;
    private final int index;
    private final TextSpan span;
    private final List<SentenceText> sentences;

    private ParagraphText(String documentText, int index, TextSpan span, List<SentenceText> sentences) {
        this.documentText = documentText;
        this.index = index;
        this.span = span;
        this.sentences = Collections.unmodifiableList(sentences);
    }

    /** One text as a single unit (evaluation, tests): its sentences by {@code segmenter}. */
    public static ParagraphText of(String text, SentenceSegmenter segmenter) {
        List<ParagraphText> units = split(text, segmenter, Integer.MAX_VALUE);
        if (units.size() == 1) {
            return units.get(0);
        }
        return new ParagraphText(text, 0, new TextSpan(0, text.length()), sentences(text, 0, text.length(),
                segmenter));
    }

    /**
     * The analysable units of a document text: paragraphs at blank lines, long ones cut between sentences so no unit
     * exceeds {@code maxChars} (a single longer sentence stays whole). Headings, tables, code and lines without
     * running text are left out.
     */
    public static List<ParagraphText> split(String documentText, SentenceSegmenter segmenter, int maxChars) {
        List<ParagraphText> units = new ArrayList<ParagraphText>();
        if (documentText == null || documentText.isEmpty()) {
            return units;
        }
        int start = 0;
        Matcher blank = BLANK_LINES.matcher(documentText);
        List<TextSpan> paragraphs = new ArrayList<TextSpan>();
        while (blank.find()) {
            paragraphs.add(new TextSpan(start, blank.start()));
            start = blank.end();
        }
        paragraphs.add(new TextSpan(start, documentText.length()));
        boolean inCode = false;
        for (TextSpan raw : paragraphs) {
            TextSpan paragraph = trim(documentText, raw);
            String text = paragraph.of(documentText);
            boolean opens = text.startsWith("```");
            boolean closes = text.endsWith("```") && (!opens || text.length() >= 6);
            if (opens || inCode) {
                // A fenced code block: skip it whole, also across blank lines inside it.
                inCode = !closes;
                continue;
            }
            if (!isRunningText(text)) {
                continue;
            }
            List<SentenceText> all = sentences(documentText, paragraph.start(), paragraph.end(), segmenter);
            List<SentenceText> run = new ArrayList<SentenceText>();
            for (SentenceText sentence : all) {
                if (!run.isEmpty() && sentence.span().end() - run.get(0).span().start() > maxChars) {
                    units.add(unit(documentText, units.size(), run));
                    run = new ArrayList<SentenceText>();
                }
                run.add(sentence);
            }
            if (!run.isEmpty()) {
                units.add(unit(documentText, units.size(), run));
            }
        }
        return units;
    }

    /** 0-based position among the units of the document. */
    public int index() {
        return index;
    }

    public TextSpan span() {
        return span;
    }

    public String text() {
        return span.of(documentText);
    }

    /** The canonical text of the whole document. */
    public String documentText() {
        return documentText;
    }

    /** The sentences, numbered from 1. */
    public List<SentenceText> sentences() {
        return sentences;
    }

    /** The sentence with {@code number}, or {@code null}. */
    public SentenceText sentence(int number) {
        return number < 1 || number > sentences.size() ? null : sentences.get(number - 1);
    }

    /** The unit as the model sees it: one line per sentence with numbered words. */
    public String numbered() {
        StringBuilder out = new StringBuilder();
        for (SentenceText sentence : sentences) {
            out.append("Satz ").append(sentence.number()).append(": ").append(sentence.numbered(documentText))
                    .append('\n');
        }
        return out.toString();
    }

    private static ParagraphText unit(String documentText, int index, List<SentenceText> run) {
        List<SentenceText> renumbered = new ArrayList<SentenceText>();
        for (SentenceText sentence : run) {
            renumbered.add(SentenceText.of(documentText, renumbered.size() + 1, sentence.span()));
        }
        return new ParagraphText(documentText, index, new TextSpan(run.get(0).span().start(),
                run.get(run.size() - 1).span().end()), renumbered);
    }

    /** Sentence spans of {@code [start, end)}: the segmenter's sentences found again in order. */
    private static List<SentenceText> sentences(String documentText, int start, int end, SentenceSegmenter segmenter) {
        String paragraph = documentText.substring(start, end);
        List<String> found = segmenter.sentences(paragraph);
        List<SentenceText> sentences = new ArrayList<SentenceText>();
        int cursor = 0;
        for (String sentence : found) {
            String trimmed = sentence == null ? "" : sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int at = paragraph.indexOf(trimmed, cursor);
            if (at < 0) {
                // The segmenter changed whitespace: give up on sentences and keep the paragraph whole.
                sentences.clear();
                sentences.add(SentenceText.of(documentText, 1, trim(documentText, new TextSpan(start, end))));
                return sentences;
            }
            sentences.add(SentenceText.of(documentText, sentences.size() + 1,
                    new TextSpan(start + at, start + at + trimmed.length())));
            cursor = at + trimmed.length();
        }
        if (sentences.isEmpty()) {
            sentences.add(SentenceText.of(documentText, 1, trim(documentText, new TextSpan(start, end))));
        }
        return sentences;
    }

    /** Running text: several words with lower-case letters, no heading, table row or list of identifiers. */
    static boolean isRunningText(String text) {
        if (text.isEmpty() || text.startsWith("#") || text.startsWith("|")) {
            return false;
        }
        int letters = 0;
        int lower = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isLowerCase(c)) {
                    lower++;
                }
            }
        }
        int words = text.trim().split("\\s+").length;
        return words >= 3 && letters * 2 >= text.length() && lower * 2 >= letters;
    }

    private static TextSpan trim(String text, TextSpan span) {
        int start = span.start();
        int end = span.end();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new TextSpan(start, end);
    }
}
