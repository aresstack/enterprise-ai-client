package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymSettings;

/**
 * Text mining in the reader (issue #107): whether opened pages are analysed for acronyms and the findings kept in the
 * metadata catalog ({@value #ACRONYMS_ENABLED_KEY}), whether the reader marks them from the start
 * ({@value #READER_HIGHLIGHT_KEY}), and the thresholds of the extractor. All apply without restart.
 */
public final class TextMiningConfig {

    public static final String ACRONYMS_ENABLED_KEY = "textMining.acronyms.enabled";
    public static final String READER_HIGHLIGHT_KEY = "reader.acronyms.highlight";
    public static final String MAX_LETTERS_KEY = "textMining.acronyms.maxLetters";
    public static final String SHOUTING_PERCENT_KEY = "textMining.acronyms.shoutingPercent";
    public static final String SHOUTING_MIN_WORDS_KEY = "textMining.acronyms.shoutingMinWords";
    /** Upper bounds of the thresholds in the configuration. */
    public static final int MAX_LETTERS_LIMIT = 20;
    public static final int SHOUTING_MIN_WORDS_LIMIT = 50;

    private final boolean acronymsEnabled;
    private final boolean readerHighlight;
    private final AcronymSettings acronyms;

    public TextMiningConfig(boolean acronymsEnabled, boolean readerHighlight, AcronymSettings acronyms) {
        this.acronymsEnabled = acronymsEnabled;
        this.readerHighlight = readerHighlight;
        this.acronyms = acronyms == null ? AcronymSettings.defaults() : acronyms;
    }

    public static TextMiningConfig defaults() {
        return new TextMiningConfig(true, true, AcronymSettings.defaults());
    }

    /** Whether opened pages are analysed and their acronym findings kept. */
    public boolean acronymsEnabled() {
        return acronymsEnabled;
    }

    /** Whether the reader shows the acronym marks when a page opens (the toolbar switch toggles them). */
    public boolean readerHighlight() {
        return readerHighlight;
    }

    public AcronymSettings acronyms() {
        return acronyms;
    }

    @Override
    public String toString() {
        return "TextMiningConfig[acronyms=" + acronymsEnabled + ", highlight=" + readerHighlight + ", " + acronyms
                + "]";
    }
}
