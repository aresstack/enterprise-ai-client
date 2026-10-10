package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.net.HttpRoutes;
import com.aresstack.enterpriseai.app.net.TrustPolicy;
import com.aresstack.enterpriseai.app.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.app.settings.ConfigurationFile;
import com.aresstack.enterpriseai.app.settings.FileSettingsActions;
import com.aresstack.enterpriseai.app.settings.ModelCatalogLoader;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialogActions;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Verdrahtet den Einstellungen-Dialog: Konfigurationsdatei, KeePass-Probe und Verbindungstest mit
 * Swing-Pairing-Dialog, ein Daemon-Thread für beide, Ergebnisse auf dem EDT, dazu die Prüfung, die der Start über
 * den Loader hinaus macht ({@link #configurationCheck()}). Wird vor der eigentlichen Komposition gebraucht (Erststart
 * ohne Konfiguration) und später für den Knopf „Einstellungen“ in der Kopfzeile.
 */
public final class SettingsAssembly {

    private SettingsAssembly() {
    }

    /** Produktiv: Datei, Swing-Pairing, KeePass-Probe und Verbindungstest auf einem eigenen Daemon-Thread, EDT. */
    public static SettingsDialogActions create(ConfigurationFile file) {
        return create(file, (ModelCatalogLoader) null);
    }

    /** Wie {@link #create(ConfigurationFile)}, dazu die Modellabfrage des Reiters „Modelle“ ({@code null}: keine). */
    public static SettingsDialogActions create(ConfigurationFile file, final ModelCatalogLoader models) {
        ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "enterprise-ai-settings-check");
                t.setDaemon(true);
                return t;
            }
        });
        return create(file, models, worker, new Executor() {
            @Override
            public void execute(Runnable command) {
                SwingUtilities.invokeLater(command);
            }
        });
    }

    public static SettingsDialogActions create(ConfigurationFile file, Executor worker, Executor ui) {
        return create(file, null, worker, ui);
    }

    public static SettingsDialogActions create(ConfigurationFile file, ModelCatalogLoader models, Executor worker,
                                               Executor ui) {
        return new FileSettingsActions(file, new KeePassSecretChecker(), new ServiceConnectionChecker(),
                configurationCheck(), models, JavaRuntimes.selectionService(file), new LocalVoices(), worker, ui);
    }

    /**
     * Die Modellquellen der Anwendung mit Zwischenspeicher im Anwendungsverzeichnis; Abfragen aus dem Dialog holen
     * den API-Key wie der Verbindungstest (KeePass, Pairing-Dialog bei Bedarf).
     */
    public static ModelCatalogs modelCatalogs(java.nio.file.Path cacheFile) {
        final KeePassSecretChecker.PairingCallbackFactory pairing = new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback forAddress(String address) {
                return new com.aresstack.enterpriseai.app.security.SwingPairingCallback(address);
            }
        };
        return new ModelCatalogs(new com.aresstack.enterpriseai.app.settings.ModelCatalogCache(cacheFile),
                new ModelCatalogs.TokenLookup() {
                    @Override
                    public String token(AppConfig config) throws java.io.IOException {
                        return ServiceConnectionChecker.chatToken(config, pairing);
                    }
                });
    }

    /**
     * Was der Start nach dem Laden zusätzlich prüft: die CA-Datei der TLS-Vertrauensregel
     * ({@code network.tls.caCertificatesFile}) und die Prüfung der Proxy-Bibliothek ({@code validate()}: fehlender
     * Host bei MANUAL_PROXY, ungültige PAC-Adresse, nicht umgesetzter Modus). Beides soll im Dialog auffallen,
     * nicht erst beim nächsten Start. Lädt keine Windows-Speicher und löst nichts auf (läuft auf dem EDT).
     */
    public static ConfigurationCheck configurationCheck() {
        return new ConfigurationCheck() {
            @Override
            public void verify(AppConfig config) {
                TrustPolicy.verify(config.network());
                java.util.List<String> problems = HttpRoutes.validationProblems(config.network());
                if (!problems.isEmpty()) {
                    java.util.List<String> described = new java.util.ArrayList<String>();
                    for (String problem : problems) {
                        described.add("network.proxy.mode: " + problem);
                    }
                    throw new AppConfigException(described);
                }
            }
        };
    }
}
