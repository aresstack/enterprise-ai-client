package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.app.ui.settings.ModelChoice;

import java.util.List;
import java.util.function.BiConsumer;
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
     * Wie {@link #check(AppConfig, Consumer)}, meldet zusätzlich die gelieferten Chat- und Embedding-Modelle
     * (höchstens einmal, auf demselben Thread). Ohne Überschreiben kommt keine Modellliste.
     */
    default boolean check(AppConfig config, Consumer<ConnectionCheckStep> onStep,
                          BiConsumer<List<ModelChoice>, List<ModelChoice>> onModels) {
        return check(config, onStep);
    }
}
