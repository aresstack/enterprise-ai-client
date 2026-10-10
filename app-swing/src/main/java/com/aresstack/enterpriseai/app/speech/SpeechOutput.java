package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.application.speech.ReadAloudService;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarConfig;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarModelCatalogAdapter;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;

import java.util.function.Function;

/**
 * Entscheidet, ob und womit vorgelesen wird: das Modell kommt aus der Katalog-Kategorie TTS ({@code model.tts}),
 * gesprochen wird heute nur über den optionalen lokalen Java-21-Sidecar. Die Enterprise-API ({@code /audio/speech})
 * ist bewusst noch nicht angebunden. Fehlt etwas, bleibt die Sprachausgabe ruhig aus (deaktivierter Knopf mit Grund),
 * der Chat läuft unverändert.
 */
public final class SpeechOutput {

    static final String NO_MODEL = "Sprachausgabe aus: kein TTS-Modell gewählt (Einstellungen → Modelle)";
    static final String REMOTE_NOT_CONNECTED = "Sprachausgabe aus: das gewählte TTS-Modell der Enterprise-API ist noch "
            + "nicht angebunden; ein lokales TTS-Modell wählen (Einstellungen → Modelle)";
    static final String NO_SIDECAR = "Sprachausgabe aus: Java 21 und Sidecar-Jar fehlen (Einstellungen → Lokale Modelle)";

    private SpeechOutput() {
    }

    /**
     * @param models       Modellauswahl und lokaler Sidecar aus der Konfiguration
     * @param localSpeech  liefert die Sprachausgabe des lokalen Sidecars für seine Pfade (geteilter Prozess)
     */
    public static ReadAloudBinding readAloud(ModelsConfig models,
                                             Function<LocalSidecarConfig, SpeechSynthesisPort> localSpeech) {
        ModelReference selected = models.selections().get(ModelCategory.TTS);
        if (selected == null) {
            return ReadAloudBinding.unavailable(NO_MODEL);
        }
        if (!LocalSidecarModelCatalogAdapter.CATALOG_ID.equals(selected.catalogId())) {
            return ReadAloudBinding.unavailable(REMOTE_NOT_CONNECTED);
        }
        if (models.localSidecar() == null) {
            return ReadAloudBinding.unavailable(NO_SIDECAR);
        }
        SpeechSynthesisPort port = localSpeech.apply(models.localSidecar());
        ReadAloudService service = new ReadAloudService(port, selected.modelId(), new JavaSoundAudioPlayback());
        return ReadAloudBinding.available(service, "Stimme: " + selected.modelId() + " (lokal)",
                models.readAloudAutoStart());
    }
}
