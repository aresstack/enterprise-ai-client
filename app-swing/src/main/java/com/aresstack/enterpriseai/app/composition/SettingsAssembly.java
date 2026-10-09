package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.settings.ConfigurationFile;
import com.aresstack.enterpriseai.app.settings.FileSettingsActions;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialogActions;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Verdrahtet den Einstellungen-Dialog: Konfigurationsdatei, KeePass-Probe mit Swing-Pairing-Dialog, ein
 * Daemon-Thread für die Probe, Ergebnisse auf dem EDT. Wird vor der eigentlichen Komposition gebraucht (Erststart
 * ohne Konfiguration) und später für den Knopf „Einstellungen“ in der Kopfzeile.
 */
public final class SettingsAssembly {

    private SettingsAssembly() {
    }

    /** Produktiv: Datei, Swing-Pairing, eigener Daemon-Thread, EDT. */
    public static SettingsDialogActions create(ConfigurationFile file) {
        ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "enterprise-ai-settings-check");
                t.setDaemon(true);
                return t;
            }
        });
        return create(file, worker, new Executor() {
            @Override
            public void execute(Runnable command) {
                SwingUtilities.invokeLater(command);
            }
        });
    }

    public static SettingsDialogActions create(ConfigurationFile file, Executor worker, Executor ui) {
        return new FileSettingsActions(file, new KeePassSecretChecker(), worker, ui);
    }
}
