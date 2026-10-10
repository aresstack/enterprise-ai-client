package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.application.speech.ReadAloudService;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarModelCatalogAdapter;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;

import java.util.List;

/**
 * Entscheidet, ob und womit vorgelesen wird: das Modell kommt aus der Katalog-Kategorie TTS ({@code model.tts}),
 * gesprochen wird über den {@link SpeechSynthesisPort} desselben Katalogs, lokal (optionaler Java-21-Sidecar) oder
 * Enterprise-API ({@code /audio/speech}, UNVERIFIED) gleichwertig. Fehlt etwas, bleibt die Sprachausgabe ruhig aus
 * (deaktivierter Knopf mit Grund), der Chat läuft unverändert.
 */
public final class SpeechOutput {

    static final String NO_MODEL = "Sprachausgabe aus: kein TTS-Modell gewählt (Einstellungen → Modelle)";
    static final String NO_SIDECAR = "Sprachausgabe aus: Java 21 und Sidecar-Jar fehlen (Einstellungen → Lokale Modelle)";
    static final String NO_PORT = "Sprachausgabe aus: die Quelle des gewählten TTS-Modells kann nicht sprechen";

    private SpeechOutput() {
    }

    /**
     * @param models Modellauswahl aus der Konfiguration
     * @param ports  die Sprachausgabe je Modellquelle ({@link SpeechSynthesisPort#catalogId()})
     */
    public static ReadAloudBinding readAloud(ModelsConfig models, List<SpeechSynthesisPort> ports) {
        ModelReference selected = models.selections().get(ModelCategory.TTS);
        if (selected == null) {
            return ReadAloudBinding.unavailable(NO_MODEL);
        }
        SpeechSynthesisPort port = null;
        for (SpeechSynthesisPort candidate : ports) {
            if (candidate.catalogId().equals(selected.catalogId())) {
                port = candidate;
                break;
            }
        }
        if (port == null) {
            return ReadAloudBinding.unavailable(LocalSidecarModelCatalogAdapter.CATALOG_ID
                    .equals(selected.catalogId()) ? NO_SIDECAR : NO_PORT);
        }
        ReadAloudService service = new ReadAloudService(port, selected.modelId(), new JavaSoundAudioPlayback());
        boolean local = LocalSidecarModelCatalogAdapter.CATALOG_ID.equals(selected.catalogId());
        return ReadAloudBinding.available(service, "Stimme: " + selected.modelId()
                + (local ? " (lokal)" : " (Enterprise-API)"), models.readAloudAutoStart());
    }
}
