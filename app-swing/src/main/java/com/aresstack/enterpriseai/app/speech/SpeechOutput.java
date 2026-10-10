package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.application.speech.ReadAloudService;
import com.aresstack.enterpriseai.application.speech.SpeechSynthesisRegistry;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;

/**
 * Entscheidet, ob und womit vorgelesen wird: das Modell kommt aus der Katalog-Kategorie TTS ({@code model.tts}),
 * gesprochen wird über den Port, den die {@link SpeechSynthesisRegistry} für den Katalog dieser Auswahl liefert;
 * welcher Anbieter dahinter steht, weiß diese Klasse nicht. Fehlt etwas, bleibt die Sprachausgabe ruhig aus
 * (deaktivierter Knopf mit Grund), der Chat läuft unverändert.
 */
public final class SpeechOutput {

    static final String NO_MODEL = "Sprachausgabe aus: kein TTS-Modell gewählt (Einstellungen → Modelle)";
    static final String NO_PORT = "Sprachausgabe aus: die Quelle des gewählten TTS-Modells ist nicht verfügbar "
            + "(lokale Modelle brauchen Java 21 und das Sidecar-Jar, Einstellungen → Lokale Modelle)";

    private SpeechOutput() {
    }

    /**
     * @param models Modellauswahl aus der Konfiguration
     * @param speech die Sprachausgabe je Modellkatalog
     */
    public static ReadAloudBinding readAloud(ModelsConfig models, SpeechSynthesisRegistry speech) {
        ModelReference selected = models.selections().get(ModelCategory.TTS);
        if (selected == null) {
            return ReadAloudBinding.unavailable(NO_MODEL);
        }
        if (!speech.supports(selected.catalogId())) {
            return ReadAloudBinding.unavailable(NO_PORT);
        }
        ReadAloudService service = new ReadAloudService(speech.require(selected.catalogId()), selected.modelId(),
                new JavaSoundAudioPlayback());
        return ReadAloudBinding.available(service, "Stimme: " + selected.modelId() + " (" + selected.catalogId()
                + ")", models.readAloudAutoStart());
    }
}
