package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

/**
 * Settings of the sentence analysis (issue #108), read for every document: whether opened pages are analysed at all
 * (off by default: page text goes to the configured chat model), whether each claim gets the yes/no check, and how
 * much of a page is analysed.
 */
public final class StatementSettings {

    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_VERIFY = true;
    public static final int DEFAULT_MAX_PARAGRAPHS = 40;
    public static final int DEFAULT_MAX_PARAGRAPH_CHARS = 1500;

    private final boolean enabled;
    private final boolean verify;
    private final int maxParagraphs;
    private final int maxParagraphChars;

    /**
     * @param maxParagraphs     paragraphs analysed per document (model requests); later ones are left out
     * @param maxParagraphChars longer paragraphs are analysed in runs of whole sentences up to this length
     */
    public StatementSettings(boolean enabled, boolean verify, int maxParagraphs, int maxParagraphChars) {
        if (maxParagraphs < 1 || maxParagraphChars < 1) {
            throw new IllegalArgumentException("limits must be positive: " + maxParagraphs + ", "
                    + maxParagraphChars);
        }
        this.enabled = enabled;
        this.verify = verify;
        this.maxParagraphs = maxParagraphs;
        this.maxParagraphChars = maxParagraphChars;
    }

    public static StatementSettings defaults() {
        return new StatementSettings(DEFAULT_ENABLED, DEFAULT_VERIFY, DEFAULT_MAX_PARAGRAPHS,
                DEFAULT_MAX_PARAGRAPH_CHARS);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean verify() {
        return verify;
    }

    public int maxParagraphs() {
        return maxParagraphs;
    }

    public int maxParagraphChars() {
        return maxParagraphChars;
    }

    @Override
    public String toString() {
        return "StatementSettings[enabled=" + enabled + ", verify=" + verify + ", paragraphs=" + maxParagraphs
                + ", chars=" + maxParagraphChars + "]";
    }
}
