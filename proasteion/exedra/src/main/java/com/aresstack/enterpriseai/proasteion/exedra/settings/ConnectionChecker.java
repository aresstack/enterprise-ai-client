package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;

import java.util.function.Consumer;

/**
 * Der Verbindungstest des Einstellungen-Dialogs über einer fertig geladenen Konfiguration. Produktiv
 * {@code app.composition.ServiceConnectionChecker} (KeePass für den API-Key plus {@link ConnectionProbe});
 * Tests setzen eine Attrappe ein. Blockiert und wird deshalb von {@link FileSettingsActions} auf dem
 * Arbeits-Executor gerufen; {@code onStep} kommt auf demselben Thread.
 */
public interface ConnectionChecker {

    /** @return {@code true}, wenn kein Schritt fehlgeschlagen ist */
    boolean check(AppConfig config, Consumer<ConnectionCheckStep> onStep);

    /**
     * Der Embedding-Test des Einrichtungsassistenten: ein echter kleiner Aufruf mit {@code embedding.model}; die
     * Dimension ist die Länge des zurückgegebenen Vektors ({@code embedding.dimension} der Konfiguration zählt nicht).
     * Blockiert; wirft mit der Ursache, wenn der Aufruf scheitert.
     */
    default int embeddingDimension(AppConfig config) throws Exception {
        throw new UnsupportedOperationException("Embedding-Test in dieser Umgebung nicht verfügbar");
    }
}
