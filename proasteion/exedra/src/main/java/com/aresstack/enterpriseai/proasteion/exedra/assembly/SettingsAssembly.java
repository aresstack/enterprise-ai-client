package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.composition.LocalVoices;
import com.aresstack.enterpriseai.proasteion.application.composition.ModelCatalogs;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.net.HttpRoutes;
import com.aresstack.enterpriseai.proasteion.application.net.TrustPolicy;
import com.aresstack.enterpriseai.proasteion.application.security.ManualCredentials;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.exedra.settings.FileSettingsActions;
import com.aresstack.enterpriseai.proasteion.application.settings.ModelCatalogLoader;
import com.aresstack.enterpriseai.proasteion.exedra.speech.SpeechOutput;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;

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
        return create(file, models, new LocalVoices(new com.aresstack.enterpriseai.proasteion.exedra.security.SwingUserPrompts()));
    }

    /** Wie {@link #create(ConfigurationFile, ModelCatalogLoader)} mit der Stimmenversorgung des Starts. */
    public static SettingsDialogActions create(ConfigurationFile file, final ModelCatalogLoader models,
                                               LocalVoices voices) {
        return create(file, models, voices, new ManualCredentials());
    }

    /** Wie oben mit der Sitzung der manuellen Zugangsdaten, die Proben und Einrichtungsassistent teilen. */
    public static SettingsDialogActions create(ConfigurationFile file, final ModelCatalogLoader models,
                                               LocalVoices voices, ManualCredentials manual) {
        ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "enterprise-ai-settings-check");
                t.setDaemon(true);
                return t;
            }
        });
        return create(file, models, voices, manual, worker, new Executor() {
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
        return create(file, models, new LocalVoices(new com.aresstack.enterpriseai.proasteion.exedra.security.SwingUserPrompts()),
                new ManualCredentials(), worker, ui);
    }

    private static SettingsDialogActions create(ConfigurationFile file, ModelCatalogLoader models, LocalVoices voices,
                                                ManualCredentials manual, Executor worker, Executor ui) {
        return new FileSettingsActions(file, new KeePassSecretChecker(manual),
                new ServiceConnectionChecker((ModelCatalogs) null, manual),
                configurationCheck(), models, voices, SpeechOutput.nlpModels(), worker, ui);
    }

    /**
     * Die Seite des Einrichtungsassistenten über denselben Einstellungen-Aktionen: gleiche Datei, gleiche
     * KeePass-Probe mit Swing-Pairing, gleicher Verbindungstest; eigener Daemon-Thread, Ergebnisse auf dem EDT.
     */
    public static com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupActions setup(
            SettingsDialogActions settings, com.aresstack.enterpriseai.ui.comic.theme.ComicPalette palette) {
        return setup(settings, palette, null);
    }

    /**
     * Wie {@link #setup(SettingsDialogActions, com.aresstack.enterpriseai.ui.comic.theme.ComicPalette)}; mit
     * {@code catalogs} sprechen Merlin und der Chat-Assistent bei der Übergabe am Ende der ersten Einrichtung.
     */
    public static com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupActions setup(
            SettingsDialogActions settings, com.aresstack.enterpriseai.ui.comic.theme.ComicPalette palette,
            ModelCatalogs catalogs) {
        return setup(settings, palette, catalogs, new ManualCredentials());
    }

    /** Wie oben mit der Sitzung der manuellen Zugangsdaten der Anwendung (Eingaben gelten auch im Chat). */
    public static com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupActions setup(
            SettingsDialogActions settings, com.aresstack.enterpriseai.ui.comic.theme.ComicPalette palette,
            ModelCatalogs catalogs, ManualCredentials manual) {
        if (!(settings instanceof FileSettingsActions)) {
            throw new IllegalArgumentException("settings must be the file-backed settings actions");
        }
        ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "enterprise-ai-setup");
                t.setDaemon(true);
                return t;
            }
        });
        return new com.aresstack.enterpriseai.proasteion.exedra.settings.FileSetupActions(
                (FileSettingsActions) settings, new KeePassSecretChecker(manual),
                new ServiceConnectionChecker(catalogs, manual),
                configurationCheck(), worker, new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        SwingUtilities.invokeLater(command);
                    }
                }, palette, manual);
    }

    /**
     * Die Modellquellen der Anwendung mit Zwischenspeicher im Anwendungsverzeichnis; Abfragen aus dem Dialog holen
     * den API-Key wie der Verbindungstest (KeePass, Pairing-Dialog bei Bedarf).
     */
    public static ModelCatalogs modelCatalogs(java.nio.file.Path cacheFile) {
        return modelCatalogs(cacheFile, new ManualCredentials());
    }

    /** Wie {@link #modelCatalogs(java.nio.file.Path)} mit der Sitzung der manuellen Zugangsdaten der Anwendung. */
    public static ModelCatalogs modelCatalogs(java.nio.file.Path cacheFile, ManualCredentials manual) {
        final KeePassSecretChecker.PairingCallbackFactory pairing = KeePassSecretChecker.swingPairing();
        final com.aresstack.enterpriseai.proasteion.exedra.security.DialogCredentialPrompt credentials =
                new com.aresstack.enterpriseai.proasteion.exedra.security.DialogCredentialPrompt(manual);
        return new ModelCatalogs(new com.aresstack.enterpriseai.proasteion.application.settings.ModelCatalogCache(cacheFile),
                new ModelCatalogs.TokenLookup() {
                    @Override
                    public String token(AppConfig config) throws java.io.IOException {
                        return ServiceConnectionChecker.chatToken(config, pairing, credentials);
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
