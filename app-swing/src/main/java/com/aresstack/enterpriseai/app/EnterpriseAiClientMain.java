package com.aresstack.enterpriseai.app;

import com.aresstack.enterpriseai.app.composition.AdapterAssembly;
import com.aresstack.enterpriseai.app.composition.ApplicationPorts;
import com.aresstack.enterpriseai.app.composition.CompositionRoot;
import com.aresstack.enterpriseai.app.composition.ModelCatalogs;
import com.aresstack.enterpriseai.app.speech.SwitchableReadAloud;
import com.aresstack.enterpriseai.app.speech.SpeechOutput;
import com.aresstack.enterpriseai.app.composition.SettingsAssembly;
import com.aresstack.enterpriseai.app.composition.ShellAssembly;
import com.aresstack.enterpriseai.app.composition.StartupNotices;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.config.ProxyAuthMode;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourcesController;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.security.ProxyAuthenticator;
import com.aresstack.enterpriseai.app.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.app.settings.ConfigurationFile;
import com.aresstack.enterpriseai.app.settings.FileIndexActions;
import com.aresstack.enterpriseai.app.settings.FileSourceActions;
import com.aresstack.enterpriseai.app.settings.ConfigurationStartup;
import com.aresstack.enterpriseai.app.settings.SettingsMapper;
import com.aresstack.enterpriseai.app.ui.settings.IndexDialog;
import com.aresstack.enterpriseai.app.ui.settings.IndexForm;
import com.aresstack.enterpriseai.app.ui.settings.IndexPanel;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialog;
import com.aresstack.enterpriseai.app.ui.settings.SourceActions;
import com.aresstack.enterpriseai.app.ui.settings.SourceDialog;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.app.ui.settings.SettingsPanel;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Einstiegspunkt der Desktop-Anwendung und Composition Root (AP23). Ablauf: Protokolldatei anhängen,
 * Konfiguration beschaffen (fehlt sie oder lädt sie nicht, öffnet sich mit Oberfläche der Einstellungen-Dialog;
 * ohne Display wird die Beispieldatei angelegt und erklärt), Netzschicht bauen (Proxy-Route, TLS-Vertrauen und
 * User-Agent als {@link NetworkServices}, ohne etwas aufzulösen; die erste Anfrage löst auf), Adapter bauen,
 * Graphen komponieren, Shutdown-Hook registrieren, Fenster zeigen, Hintergrund-Indexierung starten. Prozessweit
 * gesetzt werden nur {@code java.net.preferIPv6Addresses} (Option) und bei Proxy-Anmeldung BASIC der
 * {@link ProxyAuthenticator}. Schließen des Fensters fährt geordnet herunter und beendet die JVM; der
 * Shutdown-Hook deckt hartes Beenden ab (beide idempotent).
 *
 * <p>Start: {@code ./gradlew :app-swing:run}; Konfigurationsdatei per {@code -Denterpriseai.config=<Pfad>},
 * Benutzerverzeichnis per {@code -Denterpriseai.home=<Pfad>} (siehe {@link AppPaths}). Protokoll:
 * {@code <Benutzerverzeichnis>/logs/enterprise-ai-client.0.log} ({@link AppLogFile}).
 */
public final class EnterpriseAiClientMain {

    private static final Logger LOG = Logger.getLogger(EnterpriseAiClientMain.class.getName());

    static final int EXIT_CONFIG = 2;

    private EnterpriseAiClientMain() {
    }

    public static void main(String[] args) {
        final AppLogFile.Installation logFile = AppLogFile.install();
        LOG.info("Enterprise AI Client startet: Java " + System.getProperty("java.version") + " ("
                + System.getProperty("java.vendor") + "), " + System.getProperty("os.name") + " "
                + System.getProperty("os.version"));
        final ConfigurationFile file = new ConfigurationFile(AppPaths.configFile());
        final boolean headless = GraphicsEnvironment.isHeadless();
        final ModelCatalogs modelCatalogs = SettingsAssembly.modelCatalogs(
                AppPaths.appDirectory().resolve(AppPaths.MODEL_CATALOG_FILE_NAME));
        final SettingsDialogActions settingsActions = headless ? null : SettingsAssembly.create(file, modelCatalogs);
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file,
                headless ? null : new SwingSettingsUi(settingsActions), SettingsAssembly.configurationCheck());
        if (!outcome.isStarted()) {
            if (outcome.isCancelled()) {
                LOG.info(outcome.message());
            } else {
                LOG.severe(outcome.message());
                showError("Konfiguration", outcome.message());
            }
            AppLogFile.uninstall(logFile);
            System.exit(EXIT_CONFIG);
            return;
        }
        AppConfig config = outcome.config();
        LOG.info("Konfiguration aus " + file.path() + ": " + config);
        NetworkConfig networkConfig = config.network();
        if (networkConfig.preferIpv6() && System.getProperty("java.net.preferIPv6Addresses") == null) {
            // Wie askai-java8: nur wirksam, bevor die erste Namensauflösung läuft.
            System.setProperty("java.net.preferIPv6Addresses", "true");
        }
        // Nichts wird prozessweit installiert: Route und Vertrauensregel bekommen die Adapter je Verbindung; die
        // erste Anfrage löst die Route auf und baut die Regel (PowerShell-Export unter Windows), nicht der Start.
        final NetworkServices network = NetworkServices.from(networkConfig);
        LOG.info("Proxy-Regel: " + network.routes().describe());
        LOG.info("TLS-Vertrauensquellen (werden bei der ersten HTTPS-Verbindung geladen): "
                + networkConfig.describeTrust());
        LOG.info("User-Agent: " + network.userAgent());
        final ApplicationPorts ports;
        final CompositionRoot root;
        try {
            ports = AdapterAssembly.createWithLocalModels(config, network, new SwingPairingCallback(
                    config.keePass().rpc().host() + ":" + config.keePass().rpc().port()), modelCatalogs);
            if (networkConfig.proxyAuthMode() == ProxyAuthMode.BASIC && networkConfig.proxyCredentialRef() != null) {
                ProxyAuthenticator.install(ports.secrets(), networkConfig.proxyCredentialRef());
            }
            root = CompositionRoot.compose(config, ports, SwingUtilities::invokeLater, System::currentTimeMillis,
                    null);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Anwendung konnte nicht zusammengesetzt werden", e);
            showError("Start fehlgeschlagen", "Die Anwendung konnte nicht gestartet werden: "
                    + e.getClass().getSimpleName() + ". " + logHint(logFile));
            AppLogFile.uninstall(logFile);
            System.exit(1);
            return;
        }
        // Sprachausgabe: Modell aus der Kategorie TTS, gesprochen über die Quelle dieses Modells (Enterprise-API oder
        // lokaler Sidecar, derselbe Prozess wie der Katalog); ohne TTS-Modell oder ohne Java 21 für ein lokales
        // bleibt sie aus und der Play/Pause-Orb nennt den Grund.
        final SwitchableReadAloud readAloud = new SwitchableReadAloud(SpeechOutput.readAloud(config.models(),
                modelCatalogs.speech(config, config.models(), network, chatToken(config, ports))));
        LOG.info("Sprachausgabe: " + readAloud.currentDescription());
        root.shutdown().then("read-aloud", new Runnable() {
            @Override
            public void run() {
                readAloud.close();
            }
        });
        root.shutdown().then("model-sidecar", new Runnable() {
            @Override
            public void run() {
                modelCatalogs.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(root.shutdown().asShutdownHook());
        refreshModelsInBackground(modelCatalogs, config, network, ports);

        final AppConfig started = config;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ComicPalette palette = ComicPalette.defaultPalette();
                BubblePalette bubbles = BubblePalette.windowsPhoneInspired();
                ShellAssembly.ShellView view = ShellAssembly.createShell(root, palette, bubbles);
                final JFrame frame = ShellAssembly.createFrame(started.windowTitle(), view, palette);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        shutdownAndExit(root);
                    }
                });
                view.setSettingsAction(settingsAction(frame, file, settingsActions, palette, new Runnable() {
                    @Override
                    public void run() {
                        rebindSpeech(readAloud, file, started, modelCatalogs, network, ports);
                    }
                }));
                view.setChatModels(modelCatalogs::cached, modelId -> {
                    try {
                        file.update(Collections.singletonMap(ModelsConfig.keyOf(ModelCategory.CHAT), modelId), null, null);
                    } catch (IOException | RuntimeException e) {
                        LOG.log(Level.WARNING, "Chat-Modell " + modelId + " nicht gespeichert; gilt bis zum Beenden", e);
                    }
                }, root.workExecutor());
                view.workspace().chatShell().transcript().setReadAloud(readAloud);
                attachSourceEditing(view, frame, file, palette);
                frame.setVisible(true);
                root.startBackgroundWork();
                List<String> notices = new ArrayList<String>();
                if (!logFile.isActive()) {
                    // Ohne Protokolldatei stünden Fehlerdetails nur in der Sprechblase; das muss der Benutzer sofort
                    // sehen, sonst sucht er später ein Protokoll, das es nicht gibt.
                    notices.add(logFile.problem() + " Fehlerdetails stehen damit nur in der Sprechblase. "
                            + "Schreibrechte prüfen oder mit -D" + AppPaths.HOME_PROPERTY
                            + " ein beschreibbares Anwendungsverzeichnis wählen.");
                }
                notices.addAll(StartupNotices.of(started));
                notices.addAll(root.ports().sourceWarnings());
                if (!notices.isEmpty()) {
                    showNotices(frame, notices);
                }
            }
        });
    }

    /**
     * Das Zahnrad im Drawer: öffnet den Dialog mit den Werten der Datei. Gespeichert wird in die Datei; der
     * laufende Graph ist mit der alten Konfiguration gebaut, deshalb gelten Änderungen beim nächsten Start
     * (Angebot, jetzt zu beenden). Ausnahme wie die Chat-Auswahl: TTS-Modell und Vorlesen gelten sofort
     * ({@code afterSave}).
     */
    private static Runnable settingsAction(final JFrame frame, final ConfigurationFile file,
                                           final SettingsDialogActions actions, final ComicPalette palette,
                                           final Runnable afterSave) {
        return new Runnable() {
            @Override
            public void run() {
                SettingsForm current;
                try {
                    current = SettingsMapper.fromProperties(file.read());
                } catch (IOException io) {
                    LOG.log(Level.WARNING, "Konfigurationsdatei nicht lesbar", io);
                    showError("Einstellungen", "Die Konfiguration unter\n" + file.path()
                            + "\nist nicht lesbar (" + io.getClass().getSimpleName() + ").");
                    return;
                }
                SettingsForm saved = SettingsDialog.show(frame, current, Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                if (saved == null) {
                    return;
                }
                LOG.info("Einstellungen gespeichert; sie gelten beim nächsten Start, die Sprachausgabe sofort");
                afterSave.run();
                offerRestart(frame, "Einstellungen gespeichert",
                        "Die Einstellungen sind gespeichert. Die Sprachausgabe gilt sofort, alles andere beim "
                                + "nächsten Start der Anwendung.");
            }
        };
    }

    /**
     * Fragt die Modellquellen (KIPITZ {@code GET /models}, optional den lokalen Sidecar) einmal im Hintergrund ab,
     * damit der Reiter „Modelle“ aktuelle Listen hat; blockiert weder Start noch EDT. Fehler landen nur im Protokoll.
     */
    /**
     * Liest die gespeicherte Datei neu und bindet die Sprachausgabe an die neue TTS-Auswahl (auf dem EDT). Nur die
     * Modellwerte sind neu; Endpunkt, Netz und Token bleiben die des laufenden Graphen (gelten beim nächsten Start).
     */
    private static void rebindSpeech(SwitchableReadAloud readAloud, ConfigurationFile file, AppConfig running,
                                     ModelCatalogs catalogs, NetworkServices network, ApplicationPorts ports) {
        try {
            ModelsConfig saved = AppConfigLoader.load(file.path()).models();
            readAloud.replace(SpeechOutput.readAloud(saved,
                    catalogs.speech(running, saved, network, chatToken(running, ports))));
            LOG.info("Sprachausgabe: " + readAloud.currentDescription());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Sprachausgabe nicht neu gebunden; sie gilt beim nächsten Start", e);
        }
    }

    /** Das Bearer-Token des Chats, je Anfrage aus dem Secret-Port geholt. */
    private static java.util.function.Supplier<String> chatToken(final AppConfig config,
                                                                 final ApplicationPorts ports) {
        return new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return config.chat().apiKeyRef() == null ? null
                        : new SecretBackedTokenSource(ports.secrets(), config.chat().apiKeyRef()).token();
            }
        };
    }

    private static void refreshModelsInBackground(final ModelCatalogs catalogs, final AppConfig config,
                                                  final NetworkServices network, final ApplicationPorts ports) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    catalogs.refresh(config, network, chatToken(config, ports));
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Modellkatalog konnte nicht abgefragt werden", e);
                }
            }
        }, "enterprise-ai-model-catalog");
        thread.setDaemon(true);
        thread.start();
    }

    /** Nach dem Speichern: „Jetzt beenden“ schließt das Fenster, denn der laufende Graph hat den alten Stand. */
    private static void offerRestart(JFrame frame, String title, String message) {
        Object[] options = {"Jetzt beenden", "Weiter"};
        int choice = JOptionPane.showOptionDialog(frame, message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
        if (choice == 0) {
            frame.dispose();
        }
    }

    /**
     * Der Drawer-Reiter „Wissensquellen“ bearbeitet die Quellen der Datei über den Quellen-Dialog und mit
     * „Index …“ Indexverzeichnis und Indexierung beim Start über den Index-Dialog.
     */
    private static void attachSourceEditing(ShellAssembly.ShellView view, final JFrame frame,
                                            ConfigurationFile file, final ComicPalette palette) {
        view.knowledgeSources().setEditing(new FileSourceActions(file),
                new KnowledgeSourcesController.SourceEditorLauncher() {
                    @Override
                    public SourceDefinition edit(SourceDefinition initial, String originalId,
                                                 List<KnowledgeSourceType> types, SourceActions actions) {
                        return SourceDialog.show(frame, initial, originalId, types, actions, palette);
                    }

                    @Override
                    public boolean confirmRemove(String id, String typeName) {
                        return SourceDialog.confirmRemove(frame, id, typeName, palette);
                    }
                });
        view.workspace().knowledgeSources().setIndexSettingsAction(indexSettingsAction(frame, file, palette));
    }

    /**
     * „Index …“ im Drawer-Reiter „Wissensquellen“: öffnet den Index-Dialog mit den Werten der Datei. Gespeichert
     * wird in die Datei; der laufende Graph arbeitet mit dem alten Index, deshalb gelten Änderungen beim nächsten
     * Start (Angebot, jetzt zu beenden, wie nach dem Einstellungen-Dialog).
     */
    private static Runnable indexSettingsAction(final JFrame frame, final ConfigurationFile file,
                                                final ComicPalette palette) {
        final FileIndexActions actions = new FileIndexActions(file);
        return () -> {
            IndexForm current;
            try {
                current = actions.current();
            } catch (IOException io) {
                LOG.log(Level.WARNING, "Konfigurationsdatei nicht lesbar", io);
                showError(IndexPanel.TITLE, "Die Konfiguration unter\n" + file.path()
                        + "\nist nicht lesbar (" + io.getClass().getSimpleName() + ").");
                return;
            }
            if (IndexDialog.show(frame, current, actions, palette) == null) {
                return;
            }
            LOG.info("Index-Einstellungen gespeichert; sie gelten beim nächsten Start");
            offerRestart(frame, "Index-Einstellungen gespeichert",
                    "Die Index-Einstellungen sind gespeichert. Sie gelten beim nächsten Start der Anwendung.");
        };
    }

    private static void shutdownAndExit(final CompositionRoot root) {
        Thread exit = new Thread(new Runnable() {
            @Override
            public void run() {
                root.shutdown().run();
                System.exit(0);
            }
        }, "enterprise-ai-exit");
        exit.setDaemon(false);
        exit.start();
    }

    /** Wo Details stehen: im Protokoll, oder warum es keines gibt. */
    private static String logHint(AppLogFile.Installation logFile) {
        return logFile.isActive()
                ? "Details im Protokoll unter " + AppLogFile.directory() + "."
                : "Es gibt kein Protokoll (" + logFile.problem() + ").";
    }

    private static void showError(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Fehlerdialog nicht anzeigbar", e);
        }
    }

    private static void showNotices(JFrame owner, List<String> notices) {
        StringBuilder sb = new StringBuilder();
        for (String notice : notices) {
            sb.append("• ").append(notice).append("\n\n");
        }
        for (String notice : notices) {
            LOG.warning(notice);
        }
        try {
            JOptionPane.showMessageDialog(owner, sb.toString().trim(), "Hinweise zur Konfiguration",
                    JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Hinweisdialog nicht anzeigbar", e);
        }
    }

    /** Zeigt den Einstellungen-Dialog vom Hauptthread aus modal auf dem EDT (Erststart, fehlerhafte Datei). */
    static final class SwingSettingsUi implements ConfigurationStartup.SettingsUi {

        private final SettingsDialogActions actions;

        SwingSettingsUi(SettingsDialogActions actions) {
            this.actions = actions;
        }

        @Override
        public SettingsForm edit(final SettingsForm initial, final List<String> problems, final boolean firstStart) {
            final AtomicReference<SettingsForm> result = new AtomicReference<SettingsForm>();
            Runnable show = new Runnable() {
                @Override
                public void run() {
                    result.set(SettingsDialog.show(null, initial, problems,
                            firstStart ? SettingsPanel.Mode.FIRST_START : SettingsPanel.Mode.EDIT, actions,
                            ComicPalette.defaultPalette()));
                }
            };
            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    show.run();
                } else {
                    SwingUtilities.invokeAndWait(show);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (InvocationTargetException e) {
                LOG.log(Level.SEVERE, "Einstellungen-Dialog nicht anzeigbar", e.getCause());
                return null;
            }
            return result.get();
        }
    }
}
