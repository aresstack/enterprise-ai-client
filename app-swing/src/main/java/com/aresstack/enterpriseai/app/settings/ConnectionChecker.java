package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;

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
}
