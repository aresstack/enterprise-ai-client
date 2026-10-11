package com.aresstack.enterpriseai.proasteion.exedra.speech;

import com.aresstack.enterpriseai.proasteion.exedra.config.ModelsConfig;
import com.aresstack.enterpriseai.astu.acropolis.speech.ReadAloudService;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechSynthesisRegistry;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService;
import com.aresstack.enterpriseai.proasteion.exedra.config.AppPaths;

/**
 * Entscheidet, ob und womit vorgelesen wird: das Modell kommt aus der Katalog-Kategorie TTS ({@code model.tts}),
 * gesprochen wird über den Port, den die {@link SpeechSynthesisRegistry} für den Katalog dieser Auswahl liefert;
 * welcher Anbieter dahinter steht, weiß diese Klasse nicht. Fehlt etwas, bleibt die Sprachausgabe ruhig aus
 * (deaktivierter Knopf mit Grund), der Chat läuft unverändert.
 */
public final class SpeechOutput {

    static final String NO_MODEL = "Sprachausgabe aus: kein TTS-Modell gewählt (Einstellungen → Modelle)";
    static final String NO_PORT = "Sprachausgabe aus: die Quelle des gewählten TTS-Modells ist nicht verfügbar "
            + "(lokale Stimme installiert? Einstellungen → Modelle → Lokale Modelle)";

    private SpeechOutput() {
    }

    /**
     * Die Textanalyse des Vorlesens wie askai arch: OpenNLP-Modelle aus {@code <Anwendungsverzeichnis>/models/nlp},
     * geladen beim ersten Vorlesen, entladen eine Minute nach dem letzten.
     */
    public static TextAnalysisService textAnalysis() {
        return new TextAnalysisService(nlpModels().loader(), TextAnalysisService.DEFAULT_STOP_GRACE_MILLIS);
    }

    /** Die OpenNLP-Modelle des Vorlesens im Anwendungsverzeichnis. */
    public static NlpModels nlpModels() {
        return NlpModels.standard(AppPaths.appDirectory().resolve("models").resolve("nlp"));
    }

    /**
     * @param models   Modellauswahl aus der Konfiguration
     * @param speech   die Sprachausgabe je Modellkatalog
     * @param analysis Satz- und Spracherkennung (OpenNLP) oder {@code null}
     */
    public static ReadAloudBinding readAloud(ModelsConfig models, SpeechSynthesisRegistry speech,
                                             TextAnalysisService analysis) {
        ModelReference selected = models.selections().get(ModelCategory.TTS);
        if (selected == null) {
            return ReadAloudBinding.unavailable(NO_MODEL);
        }
        if (!speech.supports(selected.catalogId())) {
            return ReadAloudBinding.unavailable(NO_PORT);
        }
        ReadAloudService service = new ReadAloudService(speech.require(selected.catalogId()), selected.modelId(),
                new JavaSoundAudioPlayback(), models.readAloudChunking(), models.synthesisWorkers(),
                analysis);
        return ReadAloudBinding.available(service, "Stimme: " + selected.modelId() + " (" + selected.catalogId()
                + ")", models.readAloudAutoStart());
    }
}
