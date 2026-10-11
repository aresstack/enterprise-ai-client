package com.aresstack.enterpriseai.speech.opennlp;

import com.aresstack.enterpriseai.speech.api.TextAnalysisEngine;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

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
    private final List<String> problems;

    private OpenNlpTextAnalysisEngine(LanguageDetectorME detector, Map<String, SentenceDetectorME> sentenceByLanguage,
                                      List<String> problems) {
        this.detector = detector;
        this.sentenceByLanguage = sentenceByLanguage;
        this.problems = Collections.unmodifiableList(problems);
    }

    /**
     * Lädt die Modelle; ein unlesbares Modell fehlt dann einfach (steht in {@link #problems()}).
     *
     * @param languageModel  {@code langdetect-183.bin} oder {@code null}
     * @param sentenceModels Satzmodell je Sprache ({@code de}, {@code en})
     */
    public static OpenNlpTextAnalysisEngine load(Path languageModel, Map<String, Path> sentenceModels) {
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
        return new OpenNlpTextAnalysisEngine(detector, sentences, problems);
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
    public synchronized void close() {
        sentenceByLanguage.clear();
    }

    @Override
    public String toString() {
        return "OpenNlpTextAnalysisEngine[languages=" + detectsLanguages() + ", sentences="
                + sentenceByLanguage.keySet() + "]";
    }
}
