package com.aresstack.enterpriseai.astu.acropolis.speech;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.Diagnostic;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.SentenceType;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TransientTurnContextFactory;
import com.aresstack.enterpriseai.speech.api.TaggedToken;
import com.aresstack.enterpriseai.speech.api.TextAnalysisEngine;
import com.aresstack.enterpriseai.speech.api.TextAnalysisEngineLoader;
import com.aresstack.enterpriseai.speech.api.WordClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The regression messages and the frame the model receives for them. The part-of-speech model is replaced by fixed
 * STTS tags, so these tests pin the analysis and its rendering; what the chat model makes of the frame (answer
 * directly, built-in help, knowledge search) is not tested here and was not checked against the real model.
 * Acronyms in these tests are invented.
 */
public class LinguisticAnalysisRegressionTest {

    private final Map<String, String> tags = new HashMap<String, String>();
    private final Map<String, Double> probabilities = new HashMap<String, Double>();
    private final LinguisticAnalyzer analyzer = new ModelLinguisticAnalyzer(
            new TextAnalysisService(new FixedTagger(tags, probabilities), 0), "de");
    private final TransientTurnContextFactory context = new TransientTurnContextFactory();

    public LinguisticAnalysisRegressionTest() {
        tag("ITJ", "hallo");
        tag("PWS", "was");
        tag("PWAV", "wie");
        tag("PWAT", "welche");
        tag("VMFIN", "kannst", "kann");
        tag("VAFIN", "ist");
        tag("VVFIN", "geht's", "verwendet");
        tag("VVINF", "tun");
        tag("VVIMP", "erklär", "suche");
        tag("PPER", "du", "mir", "mich", "dir", "es");
        tag("NN", "programm", "tool", "informationen", "schnittstelle", "aklima");
        tag("NE", "qzx", "http");
        tag("$.", "?", ".");
        tag("$,", ",");
        probabilities.put("aklima", 0.41);
    }

    @Test
    public void greetingWithCapabilityQuestion() {
        LinguisticFrame frame = analyze("Hallo, was kannst du denn so alles für mich tun?", MessageOrigin.TYPED);
        assertEquals(SentenceType.GREETING, frame.sentenceType());
        assertTrue(frame.acronymCandidates().isEmpty());
        assertTrue(frame.diagnostics().isEmpty());
    }

    @Test
    public void greetingAskingHowItGoes() {
        assertEquals(SentenceType.GREETING, analyze("Hallo, wie geht's dir?", MessageOrigin.TYPED).sentenceType());
    }

    @Test
    public void dictationWithMisheardVerb() {
        LinguisticFrame frame = analyze("Aklima bitte erstmal das Programm", MessageOrigin.SPEECH_TRANSCRIPT);
        assertEquals(Collections.singletonList("Aklima"), frame.suspectedTokens());
        assertEquals(EnumSet.of(Diagnostic.POSSIBLE_ASR_ERROR, Diagnostic.NO_PLAUSIBLE_PREDICATE,
                Diagnostic.LOW_CONFIDENCE_TOKEN), frame.diagnostics());
        assertFalse(frame.hasVerb());
        String block = context.create(frame);
        assertTrue(block, block.contains("source: SPEECH"));
        assertTrue(block, block.contains("tokens: Aklima/NN(0.41) bitte/"));
        assertTrue(block, block.contains("suspected-tokens: Aklima"));
        assertTrue(block, block.contains("Prüfe zuerst eine plausible Fehlerkennung"));
    }

    @Test
    public void theSameWordsTypedAreNoRecognitionError() {
        LinguisticFrame frame = analyze("Aklima bitte erstmal das Programm", MessageOrigin.TYPED);
        assertTrue(frame.suspectedTokens().isEmpty());
        assertFalse(frame.diagnostics().contains(Diagnostic.POSSIBLE_ASR_ERROR));
        assertTrue(frame.diagnostics().contains(Diagnostic.NO_PLAUSIBLE_PREDICATE));
    }

    @Test
    public void requestToExplainTheProgram() {
        LinguisticFrame frame = analyze("Erklär mir erstmal das Programm.", MessageOrigin.TYPED);
        assertEquals(SentenceType.REQUEST, frame.sentenceType());
        assertTrue(frame.isImperativePossible());
    }

    @Test
    public void questionAboutTheProgram() {
        LinguisticFrame frame = analyze("Was kann dieses Programm?", MessageOrigin.TYPED);
        assertEquals(SentenceType.QUESTION, frame.sentenceType());
        assertTrue(frame.acronymCandidates().isEmpty());
    }

    @Test
    public void questionAboutAnAcronym() {
        LinguisticFrame frame = analyze("Was ist QZX?", MessageOrigin.TYPED);
        assertEquals(SentenceType.QUESTION, frame.sentenceType());
        assertEquals(Collections.singletonList("QZX"), frame.acronymCandidates());
        assertTrue(context.create(frame).contains("acronym-candidates: QZX"));
    }

    @Test
    public void statementAboutAnAcronymIsNoQuestion() {
        LinguisticFrame frame = analyze("QZX ist ein super Tool.", MessageOrigin.TYPED);
        assertEquals(SentenceType.ASSERTION, frame.sentenceType());
        assertEquals(Collections.singletonList("QZX"), frame.acronymCandidates());
    }

    /** Same form as the invented acronym: whether the term is resolved is the model's call, not the client's. */
    @Test
    public void questionAboutAWellKnownAcronymHasTheSameForm() {
        LinguisticFrame frame = analyze("Was ist HTTP?", MessageOrigin.TYPED);
        assertEquals(SentenceType.QUESTION, frame.sentenceType());
        assertEquals(Collections.singletonList("HTTP"), frame.acronymCandidates());
        assertFalse(context.create(frame).contains("SEARCH"));
    }

    @Test
    public void explicitSearchRequest() {
        LinguisticFrame frame = analyze("Suche nach Informationen über QZX.", MessageOrigin.TYPED);
        assertEquals(SentenceType.REQUEST, frame.sentenceType());
        assertEquals(Collections.singletonList("QZX"), frame.acronymCandidates());
    }

    /** A follow-up is analyzed alone; resolving "es" from the conversation is the model's part. */
    @Test
    public void followUpIsAnalyzedWithoutTheEarlierTurn() {
        analyze("Was ist QZX?", MessageOrigin.TYPED);
        LinguisticFrame frame = analyze("Welche Schnittstelle verwendet es?", MessageOrigin.TYPED);
        assertEquals(SentenceType.QUESTION, frame.sentenceType());
        assertTrue(frame.acronymCandidates().isEmpty());
        assertFalse(context.create(frame).contains("QZX"));
    }

    @Test
    public void shoutedComplaintMarksNoAcronyms() {
        assertTrue(analyze("DU HAST EIN MIESES TOOL!", MessageOrigin.TYPED).acronymCandidates().isEmpty());
    }

    @Test
    public void withoutModelsOnlySpellingAndQuestionMarkAreKnown() {
        ModelLinguisticAnalyzer spelling = new ModelLinguisticAnalyzer(null, "de");
        LinguisticFrame frame = spelling.analyze("Was ist QZX?", MessageOrigin.TYPED);
        assertFalse(frame.isTagged());
        assertEquals(SentenceType.QUESTION, frame.sentenceType());
        assertEquals(Collections.singletonList("QZX"), frame.acronymCandidates());
        LinguisticFrame dictated = spelling.analyze("Aklima bitte erstmal das Programm",
                MessageOrigin.SPEECH_TRANSCRIPT);
        assertTrue(dictated.diagnostics().isEmpty());
        String block = context.create(dictated);
        assertTrue(block, block.contains("source: SPEECH"));
        assertFalse(block, block.contains("tokens:"));
    }

    @Test
    public void blockIsMarkedAsWrittenByTheClient() {
        String block = context.create(analyze("Was ist QZX?", MessageOrigin.TYPED));
        assertTrue(block, block.startsWith("<client-turn-analysis>\nVom Client automatisch erzeugte Analyse"));
        assertTrue(block, block.endsWith("</client-turn-analysis>"));
        assertTrue(block, block.contains("source: TEXT"));
        assertTrue(block, block.contains("tokens: Was/PWS ist/VAFIN QZX/NE ?/$."));
    }

    @Test
    public void tokensKeepIdentifiersAndSplitOffPunctuation() {
        assertEquals(Arrays.asList("„", "Was", "ist", "XB-17.4.2", "?", "“"),
                ModelLinguisticAnalyzer.tokens("„Was ist XB-17.4.2?“"));
    }

    private LinguisticFrame analyze(String text, MessageOrigin origin) {
        return analyzer.analyze(text, origin);
    }

    private void tag(String tag, String... words) {
        for (String word : words) {
            tags.put(word, tag);
        }
    }

    /** Stand-in for the OpenNLP models: fixed STTS tags per lower-case token (ADV otherwise), no tokenizer model. */
    private static final class FixedTagger implements TextAnalysisEngineLoader {
        private final Map<String, String> tags;
        private final Map<String, Double> probabilities;

        FixedTagger(Map<String, String> tags, Map<String, Double> probabilities) {
            this.tags = tags;
            this.probabilities = probabilities;
        }

        @Override
        public TextAnalysisEngine load() {
            return new TextAnalysisEngine() {
                @Override
                public boolean detectsLanguages() {
                    return false;
                }

                @Override
                public String detectLanguage(String sentence) {
                    return "";
                }

                @Override
                public List<String> sentences(String paragraph, String languageCode) {
                    return Collections.singletonList(paragraph);
                }

                @Override
                public boolean tagsWordClasses(String languageCode) {
                    return "de".equals(languageCode);
                }

                @Override
                public List<TaggedToken> tag(List<String> tokens, String languageCode) {
                    List<TaggedToken> tagged = new ArrayList<TaggedToken>();
                    for (String token : tokens) {
                        String key = token.toLowerCase(Locale.ROOT);
                        String tag = tags.containsKey(key) ? tags.get(key) : "ADV";
                        Double probability = probabilities.get(key);
                        tagged.add(new TaggedToken(token, tag, wordClass(tag),
                                probability == null ? 0.9 : probability));
                    }
                    return tagged;
                }

                @Override
                public void close() {
                }
            };
        }

        private static WordClass wordClass(String tag) {
            if (tag.startsWith("$")) {
                return WordClass.PUNCTUATION;
            }
            switch (tag) {
                case "ITJ":
                    return WordClass.INTERJECTION;
                case "PWS":
                case "PWAV":
                case "PWAT":
                    return WordClass.INTERROGATIVE;
                case "VMFIN":
                case "VAFIN":
                case "VVFIN":
                    return WordClass.VERB_FINITE;
                case "VVINF":
                    return WordClass.VERB_NONFINITE;
                case "VVIMP":
                    return WordClass.VERB_IMPERATIVE;
                case "PPER":
                    return WordClass.PERSONAL_PRONOUN;
                case "NN":
                    return WordClass.NOUN;
                case "NE":
                    return WordClass.PROPER_NOUN;
                default:
                    return WordClass.OTHER;
            }
        }
    }
}
