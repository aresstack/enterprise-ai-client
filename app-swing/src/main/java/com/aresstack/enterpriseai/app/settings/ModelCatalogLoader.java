package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;

/**
 * Die Modellabfrage des Einstellungen-Dialogs über einer fertig geladenen Konfiguration (Entwurf). Produktiv
 * {@code app.composition.ModelCatalogs}; {@link #refresh} blockiert und läuft deshalb auf dem Arbeits-Executor.
 */
public interface ModelCatalogLoader {

    /** Der zuletzt bekannte Stand (Zwischenspeicher); nie {@code null}. */
    ModelCatalogSnapshot cached();

    /** Fragt alle Modellquellen der Konfiguration ab und speichert den Stand; nie {@code null}. */
    ModelCatalogSnapshot refresh(AppConfig config);
}
