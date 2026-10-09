package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.net.TrustPolicy;
import com.aresstack.enterpriseai.app.settings.ConfigurationCheck;
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
 * Daemon-Thread für die Probe, Ergebnisse auf dem EDT, dazu die Prüfung, die der Start über den Loader hinaus
 * macht ({@link #configurationCheck()}). Wird vor der eigentlichen Komposition gebraucht (Erststart
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
        return new FileSettingsActions(file, new KeePassSecretChecker(), configurationCheck(), worker, ui);
    }

    /**
     * Was der Start nach dem Laden zusätzlich prüft: die TLS-Vertrauensregel liest
     * {@code network.tls.caCertificatesFile}; eine fehlende oder leere Datei soll im Dialog auffallen, nicht erst
     * als Fehlerdialog beim nächsten Start. Baut die Regel nur, installiert sie nicht.
     */
    public static ConfigurationCheck configurationCheck() {
        return new ConfigurationCheck() {
            @Override
            public void verify(AppConfig config) {
                TrustPolicy.from(config.network());
            }
        };
    }
}
