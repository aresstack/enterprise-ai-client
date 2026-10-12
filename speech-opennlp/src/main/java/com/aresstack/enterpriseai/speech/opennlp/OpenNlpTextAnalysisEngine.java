package com.aresstack.enterpriseai.speech.opennlp;

import com.aresstack.enterpriseai.speech.api.TextAnalysisEngine;
import com.aresstack.enterpriseai.speech.api.TaggedToken;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Die OpenNLP-Engine aus askai-java8 arch ({@code OpenNlpAnalysisEngineLoader}): lädt, was installiert ist, und
 * degradiert ehrlich. Ohne Spracherkennungsmodell keine Spracherkennung; ohne Satzmodell einer Sprache ein einfacher
 * Schnitt an Satzzeichen. Erkannte Sprachen sind auf das Vorlese-Paar de/en beschränkt, alles andere gilt als
 * unbekannt. Sätze unter 12 Zeichen bleiben in der Rückfallsprache (zu kurz für eine verlässliche Erkennung).
 */
public final class OpenNlpTextAnalysisEngine implements TextAnalysisEngine {

    /** Kürzere Sätze werden nicht erkannt (wie arch). */
    static final int MIN_DETECTION_CHARS = 12;

    private static final Pattern BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    private final LanguageDetectorME detector;
    private final Map<String, SentenceDetectorME> sentenceByLanguage;
    private final Map<String, TokenizerME> tokenizerByLanguage;
    private final Map<String, POSTaggerME> taggerByLanguage;
    private final List<String> problems;

    private OpenNlpTextAnalysisEngine(LanguageDetectorME detector, Map<String, SentenceDetectorME> sentenceByLanguage,
                                      Map<String, TokenizerME> tokenizerByLanguage,
                                      Map<String, POSTaggerME> taggerByLanguage, List<String> problems) {
        this.detector = detector;
        this.sentenceByLanguage = sentenceByLanguage;
        this.tokenizerByLanguage = tokenizerByLanguage;
        this.taggerByLanguage = taggerByLanguage;
        this.problems = Collections.unmodifiableList(problems);
    }

    /**
     * Lädt die Modelle; ein unlesbares Modell fehlt dann einfach (steht in {@link #problems()}).
     *
     * @param languageModel  {@code langdetect-183.bin} oder {@code null}
     * @param sentenceModels Satzmodell je Sprache ({@code de}, {@code en})
     */
    public static OpenNlpTextAnalysisEngine load(Path languageModel, Map<String, Path> sentenceModels) {
        return load(languageModel, sentenceModels, Collections.<String, Path>emptyMap(),
                Collections.<String, Path>emptyMap());
    }

    /**
     * Load the models; an unreadable model is simply missing (listed in {@link #problems()}).
     *
     * @param languageModel  {@code langdetect-183.bin} or {@code null}
     * @param sentenceModels sentence model per language ({@code de}, {@code en})
     * @param tokenizerModels tokenizer model per language ({@code de}, {@code en})
     * @param posModels       part-of-speech model per language ({@code de}, {@code en})
     */
    public static OpenNlpTextAnalysisEngine load(Path languageModel, Map<String, Path> sentenceModels,
                                                 Map<String, Path> tokenizerModels, Map<String, Path> posModels) {
        List<String> problems = new ArrayList<String>();
        LanguageDetectorME detector = null;
        if (languageModel != null) {
            try (InputStream in = Files.newInputStream(languageModel)) {
                detector = new LanguageDetectorME(new LanguageDetectorModel(in));
            } catch (IOException | RuntimeException unloadable) {
                problems.add(languageModel.getFileName() + ": " + unloadable.getMessage());
            }
        }
        Map<String, SentenceDetectorME> sentences = new HashMap<String, SentenceDetectorME>();
        if (sentenceModels != null) {
            for (Map.Entry<String, Path> model : sentenceModels.entrySet()) {
                try (InputStream in = Files.newInputStream(model.getValue())) {
                    sentences.put(model.getKey(), new SentenceDetectorME(new SentenceModel(in)));
                } catch (IOException | RuntimeException unloadable) {
                    problems.add(model.getValue().getFileName() + ": " + unloadable.getMessage());
                }
            }
        }
        Map<String, TokenizerME> tokenizers = new HashMap<String, TokenizerME>();
        if (tokenizerModels != null) {
            for (Map.Entry<String, Path> model : tokenizerModels.entrySet()) {
                try (InputStream in = Files.newInputStream(model.getValue())) {
                    tokenizers.put(model.getKey(), new TokenizerME(new TokenizerModel(in)));
                } catch (IOException | RuntimeException unloadable) {
                    problems.add(model.getValue().getFileName() + ": " + unloadable.getMessage());
                }
            }
        }
        Map<String, POSTaggerME> taggers = new HashMap<String, POSTaggerME>();
        if (posModels != null) {
            for (Map.Entry<String, Path> model : posModels.entrySet()) {
                try (InputStream in = Files.newInputStream(model.getValue())) {
                    taggers.put(model.getKey(), new POSTaggerME(new POSModel(in)));
                } catch (IOException | RuntimeException unloadable) {
                    problems.add(model.getValue().getFileName() + ": " + unloadable.getMessage());
                }
            }
        }
        return new OpenNlpTextAnalysisEngine(detector, sentences, tokenizers, taggers, problems);
    }

    /** Modelle, die nicht geladen werden konnten (Dateiname und Grund). */
    public List<String> problems() {
        return problems;
    }

    @Override
    public boolean detectsLanguages() {
        return detector != null;
    }

    @Override
    public synchronized String detectLanguage(String sentence) {
        if (detector == null || sentence == null || sentence.trim().length() < MIN_DETECTION_CHARS) {
            return "";
        }
        String iso3 = detector.predictLanguage(sentence).getLang();
        if ("deu".equals(iso3)) {
            return "de";
        }
        if ("eng".equals(iso3)) {
            return "en";
        }
        return "";
    }

    @Override
    public synchronized List<String> sentences(String paragraph, String languageCode) {
        List<String> sentences = new ArrayList<String>();
        if (paragraph == null || paragraph.trim().isEmpty()) {
            return sentences;
        }
        // SentenceDetectorME ist nicht threadsicher; die Analyse ist billig genug, um sie zu serialisieren.
        SentenceDetectorME splitter = sentenceByLanguage.get(languageCode);
        String[] parts = splitter != null ? splitter.sentDetect(paragraph) : BOUNDARY.split(paragraph);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    @Override
    public synchronized boolean tokenizes(String languageCode) {
        return tokenizerByLanguage.containsKey(languageCode);
    }

    @Override
    public synchronized List<String> tokens(String sentence, String languageCode) {
        TokenizerME tokenizer = tokenizerByLanguage.get(languageCode);
        if (tokenizer == null || sentence == null || sentence.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.asList(tokenizer.tokenize(sentence));
    }

    @Override
    public synchronized boolean tagsWordClasses(String languageCode) {
        return taggerByLanguage.containsKey(languageCode);
    }

    @Override
    public synchronized List<TaggedToken> tag(List<String> tokens, String languageCode) {
        POSTaggerME tagger = taggerByLanguage.get(languageCode);
        if (tagger == null || tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        // POSTaggerME is not thread-safe either; tagging one message is cheap enough to serialize.
        String[] words = tokens.toArray(new String[0]);
        String[] tags = tagger.tag(words);
        double[] probabilities = tagger.probs();
        List<TaggedToken> tagged = new ArrayList<TaggedToken>(tags.length);
        for (int i = 0; i < tags.length; i++) {
            double probability = probabilities != null && i < probabilities.length ? probabilities[i] : -1;
            tagged.add(new TaggedToken(words[i], tags[i], PosTags.wordClass(tags[i]), probability));
        }
        return tagged;
    }

    @Override
    public synchronized void close() {
        sentenceByLanguage.clear();
        tokenizerByLanguage.clear();
        taggerByLanguage.clear();
    }

    @Override
    public String toString() {
        return "OpenNlpTextAnalysisEngine[languages=" + detectsLanguages() + ", sentences="
                + sentenceByLanguage.keySet() + ", tokens=" + tokenizerByLanguage.keySet() + ", wordClasses=" + taggerByLanguage.keySet() + "]";
    }
}
