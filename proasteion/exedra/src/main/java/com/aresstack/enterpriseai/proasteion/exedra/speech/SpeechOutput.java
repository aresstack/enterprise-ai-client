package com.aresstack.enterpriseai.proasteion.exedra.speech;

import com.aresstack.enterpriseai.proasteion.application.speech.NlpModels;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.proasteion.application.config.TtsVoiceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.speech.ReadAloudService;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechSynthesisRegistry;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechChunking;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService;

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

    static final String NO_MERLIN_VOICE = "Merlin spricht nicht: keine Stimme gewählt (setup.merlin.ttsModel)";
    static final String NO_MERLIN_PORT = "Merlin spricht nicht: die Quelle seiner Stimme ist nicht verfügbar";

    private SpeechOutput() {
    }

    /**
     * Die Textanalyse des Vorlesens wie askai arch: OpenNLP-Modelle aus {@code <Anwendungsverzeichnis>/models/nlp},
     * geladen beim ersten Vorlesen, entladen eine Minute nach dem letzten.
     */
    public static TextAnalysisService textAnalysis() {
        return NlpModels.textAnalysis();
    }

    /** Die OpenNLP-Modelle des Vorlesens im Anwendungsverzeichnis. */
    public static NlpModels nlpModels() {
        return NlpModels.inApplicationDirectory();
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
        // Feste Stimme je Modell: ohne sie wählen Modelle mit mehreren Sprechern je Absatz selbst.
        String voice = models.ttsVoice(selected, TtsVoiceCatalog.bundled());
        ReadAloudService service = new ReadAloudService(speech.require(selected.catalogId()), selected.modelId(),
                voice, new JavaSoundAudioPlayback(), models.readAloudChunking(), models.synthesisWorkers(),
                models.synthesisAttempts(), analysis);
        return ReadAloudBinding.available(service, "Stimme: " + selected.modelId()
                + (voice == null ? "" : " / " + voice) + " (" + selected.catalogId() + ")",
                models.readAloudAutoStart(), "catalog=" + selected.catalogId());
    }

    /**
     * Merlins eigene Stimme im Einrichtungsassistenten ({@code setup.merlin.ttsModel}), unabhängig vom TTS-Modell des
     * Chats. Ist sie nicht gewählt oder ihre Quelle nicht verfügbar, gibt es bewusst keinen Rückfall auf
     * {@code model.tts}: Merlin zeigt seine Sätze dann nur als Sprechblase. Seine kurzen Sätze laufen ohne Textanalyse
     * Satz für Satz: kein Laden der Sprachmodelle vor dem ersten Satz, der erste Satz erklingt, sobald er fertig ist.
     */
    public static ReadAloudBinding merlinVoice(ModelsConfig models, SpeechSynthesisRegistry speech) {
        ModelReference voice = models.merlinVoice();
        if (voice == null) {
            return ReadAloudBinding.unavailable(NO_MERLIN_VOICE);
        }
        if (!speech.supports(voice.catalogId())) {
            return ReadAloudBinding.unavailable(NO_MERLIN_PORT);
        }
        // Fester Sprecher (setup.merlin.ttsVoice), sonst die männliche Stimme seines Modells; nie speech.voice des Chats.
        TtsVoiceCatalog catalog = TtsVoiceCatalog.bundled();
        String speaker = models.merlinSpeaker().isEmpty() && !ModelsConfig.LOCAL_CATALOG_ID.equals(voice.catalogId())
                ? catalog.voice(voice.modelId(), TtsVoiceCatalog.Gender.MALE)
                : ModelsConfig.resolveTtsVoice(voice, models.merlinSpeaker(), null, catalog);
        ReadAloudService service = new ReadAloudService(speech.require(voice.catalogId()), voice.modelId(),
                speaker, new JavaSoundAudioPlayback(), SpeechChunking.SENTENCES, models.synthesisWorkers(),
                models.synthesisAttempts(), null);
        return ReadAloudBinding.available(service, "Merlins Stimme: " + voice.modelId()
                + (speaker == null ? "" : " / " + speaker) + " (" + voice.catalogId() + ")", false,
                "catalog=" + voice.catalogId());
    }
}
