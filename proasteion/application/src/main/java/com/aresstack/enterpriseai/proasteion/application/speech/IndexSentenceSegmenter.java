package com.aresstack.enterpriseai.proasteion.application.speech;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService;

import java.util.List;

/**
 * Satzgrenzen der Passagenbildung beim Indexieren aus dem OpenNLP-Satzmodell der Sprache (askai arch
 * {@code OpenNlpSentenceSegmenter}): dieselben Modelle wie beim Vorlesen ({@link NlpModels}), aber eine eigene
 * Textanalyse, die beim ersten Indexieren lädt und geladen bleibt. Ohne installiertes Satzmodell der Sprache gilt der
 * regelbasierte Splitter; {@link #id()} sagt, welches Verfahren gilt (geht in den Fingerabdruck des Index ein).
 * Die Sprache ist die eingestellte Sprache der Wissensbasis, keine Spracherkennung je Satz.
 */
public final class IndexSentenceSegmenter implements SentenceSegmenter {

    private final NlpModels models;
    private final TextAnalysisService analysis;
    private final String language;
    private final SentenceSegmenter fallback = SentenceSegmenter.splitter();
    private boolean acquired;

    public IndexSentenceSegmenter(NlpModels models, TextAnalysisService analysis, String language) {
        if (models == null || analysis == null || language == null) {
            throw new IllegalArgumentException("models, analysis and language must not be null");
        }
        this.models = models;
        this.analysis = analysis;
        this.language = language;
    }

    @Override
    public List<String> sentences(String paragraph) {
        if (!models.hasSentenceModel(language)) {
            return fallback.sentences(paragraph);
        }
        synchronized (this) {
            if (!acquired) {
                acquired = true;
                analysis.acquire(this); // bleibt geladen; der Index-Thread ruft ohnehin nacheinander
            }
        }
        List<String> sentences = analysis.sentences(paragraph, language);
        return sentences == null || sentences.isEmpty() ? fallback.sentences(paragraph) : sentences;
    }

    @Override
    public String id() {
        return models.hasSentenceModel(language) ? "opennlp-" + language : fallback.id();
    }
}
