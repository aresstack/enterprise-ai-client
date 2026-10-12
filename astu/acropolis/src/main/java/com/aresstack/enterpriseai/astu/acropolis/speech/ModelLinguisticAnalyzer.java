package com.aresstack.enterpriseai.astu.acropolis.speech;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.Diagnostic;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticToken;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.SentenceType;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TermShape;
import com.aresstack.enterpriseai.speech.api.TaggedToken;
import com.aresstack.enterpriseai.speech.api.WordClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Describe a message with the local language models behind {@link TextAnalysisService}: language detection, sentence
 * splitting, tokenizer and part-of-speech tagger (Apache OpenNLP in the adapter; this class only sees neutral word
 * classes). Whatever model is missing leaves its hints out: without a tagger only language, sentence count, acronym
 * candidates and the question mark are known.
 */
public final class ModelLinguisticAnalyzer implements LinguisticAnalyzer {

    private static final String TRAILING = ".,!?;:)]}\"'»“”’";
    private static final String LEADING = "([{\"'«„“‘";

    private final TextAnalysisService analysis;
    private final String fallbackLanguage;

    /**
     * @param analysis         the local text analysis, or {@code null} for spelling only
     * @param fallbackLanguage the language when detection gives none ({@code de}, {@code en})
     */
    public ModelLinguisticAnalyzer(TextAnalysisService analysis, String fallbackLanguage) {
        this.analysis = analysis;
        this.fallbackLanguage = fallbackLanguage == null ? "" : fallbackLanguage;
    }

    @Override
    public LinguisticFrame analyze(String text, MessageOrigin origin) {
        if (analysis == null) {
            return analyzeWithoutModels(text, origin);
        }
        String value = text == null ? "" : text.trim();
        analysis.acquire(this);
        try {
            String detected = analysis.detectLanguage(value);
            String language = detected == null || detected.isEmpty() ? fallbackLanguage : detected;
            List<String> sentences = analysis.sentences(value, language);
            List<String> tokens = analysis.tokens(value, language);
            if (tokens.isEmpty()) {
                tokens = tokens(value);
            }
            return describe(origin, language, sentences == null ? sentenceCount(value) : sentences.size(), tokens,
                    analysis.tag(tokens, language));
        } catch (RuntimeException unavailable) {
            return analyzeWithoutModels(text, origin);
        } finally {
            analysis.release(this);
        }
    }

    @Override
    public LinguisticFrame analyzeWithoutModels(String text, MessageOrigin origin) {
        String value = text == null ? "" : text.trim();
        return describe(origin, fallbackLanguage, sentenceCount(value), tokens(value),
                Collections.<TaggedToken>emptyList());
    }

    private static LinguisticFrame describe(MessageOrigin origin, String language, int sentences, List<String> tokens,
                                            List<TaggedToken> tagged) {
        LinguisticFrame.Builder frame = LinguisticFrame.builder(origin).language(language).sentenceCount(sentences);
        boolean shouting = shouting(tokens);
        for (String token : tokens) {
            if (TermShape.isAcronymOrIdentifier(token, shouting)) {
                frame.acronymCandidate(token);
            }
        }
        boolean question = tokens.contains("?");
        if (tagged.isEmpty() || tagged.size() != tokens.size()) {
            return frame.sentenceType(question ? SentenceType.QUESTION : SentenceType.UNKNOWN).build();
        }
        List<LinguisticToken> described = new ArrayList<LinguisticToken>(tagged.size());
        for (TaggedToken token : tagged) {
            described.add(new LinguisticToken(token.text(), token.tag(), token.probability()));
        }
        frame.tokens(described);
        boolean interrogative = false;
        boolean verb = false;
        boolean finite = false;
        boolean imperative = false;
        boolean content = false;
        int words = 0;
        List<String> uncertain = new ArrayList<String>();
        for (TaggedToken token : tagged) {
            WordClass wordClass = token.wordClass();
            if (wordClass == WordClass.PUNCTUATION) {
                continue;
            }
            words++;
            interrogative |= wordClass == WordClass.INTERROGATIVE;
            verb |= wordClass.isVerb();
            finite |= wordClass == WordClass.VERB_FINITE;
            imperative |= wordClass == WordClass.VERB_IMPERATIVE;
            content |= wordClass == WordClass.NOUN || wordClass == WordClass.PROPER_NOUN
                    || wordClass == WordClass.FOREIGN;
            if (token.probability() >= 0 && token.probability() < LinguisticToken.UNCERTAIN_BELOW) {
                uncertain.add(token.text());
                frame.uncertainToken(token.text());
            }
        }
        int opening = firstWord(tagged);
        boolean verbFirst = opening >= 0 && tagged.get(opening).wordClass().isVerb();
        imperative |= verbFirst && !question;
        frame.interrogative(interrogative).verb(verb).finiteVerb(finite).imperativePossible(imperative);
        if (!uncertain.isEmpty()) {
            frame.diagnostic(Diagnostic.LOW_CONFIDENCE_TOKEN);
        }
        if (words > 0 && uncertain.size() * 2 >= words) {
            frame.diagnostic(Diagnostic.LOW_PARSE_CONFIDENCE);
        }
        boolean noPredicate = words >= 3 && !verb;
        if (noPredicate) {
            frame.diagnostic(Diagnostic.NO_PLAUSIBLE_PREDICATE);
        }
        if (origin == MessageOrigin.SPEECH_TRANSCRIPT) {
            boolean shoutingText = shouting;
            boolean suspected = false;
            if (noPredicate && opening >= 0 && couldBeMisheard(tagged.get(opening), shoutingText)) {
                frame.suspectedToken(tagged.get(opening).text());
                suspected = true;
            }
            for (TaggedToken token : tagged) {
                if (uncertain.contains(token.text()) && couldBeMisheard(token, shoutingText)) {
                    frame.suspectedToken(token.text());
                    suspected = true;
                }
            }
            if (suspected) {
                frame.diagnostic(Diagnostic.POSSIBLE_ASR_ERROR);
            }
        }
        boolean greets = opening < 0 || firstIsInterjection(tagged);
        SentenceType type;
        if (greets && !content && frame.build().acronymCandidates().isEmpty()) {
            type = SentenceType.GREETING;
        } else if (question || interrogative) {
            type = SentenceType.QUESTION;
        } else if (imperative) {
            type = SentenceType.REQUEST;
        } else if (finite) {
            type = SentenceType.ASSERTION;
        } else {
            type = SentenceType.UNKNOWN;
        }
        return frame.sentenceType(type).build();
    }

    /** A content word (not an acronym) where a recognizer may have heard a similar-sounding word. */
    private static boolean couldBeMisheard(TaggedToken token, boolean shouting) {
        WordClass wordClass = token.wordClass();
        boolean content = wordClass == WordClass.NOUN || wordClass == WordClass.PROPER_NOUN
                || wordClass == WordClass.FOREIGN || wordClass == WordClass.OTHER;
        return content && TermShape.letters(token.text()) > 1
                && !TermShape.isAcronymOrIdentifier(token.text(), shouting);
    }

    /** Index of the first word that is neither punctuation nor interjection, or -1. */
    private static int firstWord(List<TaggedToken> tagged) {
        for (int i = 0; i < tagged.size(); i++) {
            WordClass wordClass = tagged.get(i).wordClass();
            if (wordClass != WordClass.PUNCTUATION && wordClass != WordClass.INTERJECTION) {
                return i;
            }
        }
        return -1;
    }

    private static boolean firstIsInterjection(List<TaggedToken> tagged) {
        for (TaggedToken token : tagged) {
            if (token.wordClass() != WordClass.PUNCTUATION) {
                return token.wordClass() == WordClass.INTERJECTION;
            }
        }
        return false;
    }

    /** Whether most words are written in capitals, so capitals say nothing about acronyms. */
    private static boolean shouting(List<String> tokens) {
        int words = 0;
        int capitals = 0;
        for (String token : tokens) {
            if (TermShape.letters(token) < 2) {
                continue;
            }
            words++;
            if (token.equals(token.toUpperCase(Locale.ROOT))) {
                capitals++;
            }
        }
        return words >= 2 && capitals * 2 >= words;
    }

    private static int sentenceCount(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        return text.split("(?<=[.!?])\\s+").length;
    }

    /** Split at white space; leading and trailing punctuation become tokens of their own, inner dots and dashes stay. */
    static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return tokens;
        }
        for (String part : text.trim().split("\\s+")) {
            int start = 0;
            int end = part.length();
            while (start < end && LEADING.indexOf(part.charAt(start)) >= 0) {
                tokens.add(String.valueOf(part.charAt(start)));
                start++;
            }
            List<String> trailing = new ArrayList<String>();
            while (end > start && TRAILING.indexOf(part.charAt(end - 1)) >= 0) {
                trailing.add(0, String.valueOf(part.charAt(end - 1)));
                end--;
            }
            if (end > start) {
                tokens.add(part.substring(start, end));
            }
            tokens.addAll(trailing);
        }
        return tokens;
    }
}
