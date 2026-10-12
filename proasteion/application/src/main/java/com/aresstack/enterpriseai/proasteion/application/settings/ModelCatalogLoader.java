package com.aresstack.enterpriseai.proasteion.application.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechSynthesisRegistry;

/**
 * Die Modellabfrage des Einstellungen-Dialogs über einer fertig geladenen Konfiguration (Entwurf). Produktiv
 * {@code app.composition.ModelCatalogs}; {@link #refresh} blockiert und läuft deshalb auf dem Arbeits-Executor.
 */
public interface ModelCatalogLoader {

    /** Der zuletzt bekannte Stand (Zwischenspeicher); nie {@code null}. */
    ModelCatalogSnapshot cached();

    /** Fragt alle Modellquellen der Konfiguration ab und speichert den Stand; nie {@code null}. */
    ModelCatalogSnapshot refresh(AppConfig config);

    /**
     * Die Sprachausgabe des Entwurfs für „Stimme testen“ (blockiert beim Sprechen, nie auf dem EDT);
     * {@code null}: in dieser Umgebung nicht verfügbar.
     */
    default SpeechSynthesisRegistry speechPreview(AppConfig config) {
        return null;
    }
}
