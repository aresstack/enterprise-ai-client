package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService;
import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;
import com.aresstack.enterpriseai.speech.api.TaggedToken;
import com.aresstack.enterpriseai.speech.api.WordClass;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * {@link PredicateCheck} with the local tokenizer and part-of-speech models behind {@link TextAnalysisService}: the
 * verb span must carry a verb tag. Loads the models on first use and keeps them (like the sentence segmentation of the
 * index); without installed models every answer is unknown.
 */
public final class PosPredicateCheck implements PredicateCheck {

    private final TextAnalysisService analysis;
    private final String language;
    private final BooleanSupplier installed;
    private boolean acquired;

    /** @param installed whether tokenizer and part-of-speech model of {@code language} are installed (asked per call) */
    public PosPredicateCheck(TextAnalysisService analysis, String language, BooleanSupplier installed) {
        if (analysis == null || language == null || installed == null) {
            throw new IllegalArgumentException("analysis, language and installed must not be null");
        }
        this.analysis = analysis;
        this.language = language;
        this.installed = installed;
    }

    @Override
    public synchronized Boolean isVerbal(SentenceText sentence, List<TextSpan> predicate) {
        if (predicate.isEmpty() || !installed.getAsBoolean()) {
            return null;
        }
        if (!acquired) {
            acquired = true;
            analysis.acquire(this); // stays loaded
        }
        String text = sentence.text();
        List<String> tokens = analysis.tokens(text, language);
        if (tokens.isEmpty()) {
            return null;
        }
        List<TaggedToken> tags = analysis.tag(tokens, language);
        if (tags.size() != tokens.size()) {
            return null;
        }
        TextSpan verb = predicate.get(0);
        int cursor = 0;
        boolean covered = false;
        for (int i = 0; i < tokens.size(); i++) {
            int at = text.indexOf(tokens.get(i), cursor);
            if (at < 0) {
                return null; // the tokenizer changed the text; no reliable mapping
            }
            cursor = at + tokens.get(i).length();
            TextSpan token = new TextSpan(sentence.span().start() + at, sentence.span().start() + cursor);
            if (token.overlaps(verb)) {
                covered = true;
                if (isVerb(tags.get(i).wordClass())) {
                    return Boolean.TRUE;
                }
            }
        }
        return covered ? Boolean.FALSE : null;
    }

    @Override
    public String id() {
        return installed.getAsBoolean() ? "pos-" + language : "none";
    }

    private static boolean isVerb(WordClass wordClass) {
        return wordClass == WordClass.VERB_FINITE || wordClass == WordClass.VERB_NONFINITE
                || wordClass == WordClass.VERB_IMPERATIVE;
    }
}
