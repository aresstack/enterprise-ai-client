package com.aresstack.enterpriseai.app;

import com.aresstack.enterpriseai.app.composition.AdapterAssembly;
import com.aresstack.enterpriseai.app.composition.ApplicationPorts;
import com.aresstack.enterpriseai.app.composition.CompositionRoot;
import com.aresstack.enterpriseai.app.composition.ModelCatalogs;
import com.aresstack.enterpriseai.app.speech.Dictation;
import com.aresstack.enterpriseai.app.speech.SwitchableReadAloud;
import com.aresstack.enterpriseai.app.speech.SpeechOutput;
import com.aresstack.enterpriseai.application.speech.TextAnalysisService;
import com.aresstack.enterpriseai.app.composition.LocalVoices;
import com.aresstack.enterpriseai.app.composition.SettingsAssembly;
import com.aresstack.enterpriseai.app.composition.ShellAssembly;
import com.aresstack.enterpriseai.app.composition.StartupNotices;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.config.ProxyAuthMode;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourcesController;
import com.aresstack.enterpriseai.app.net.LiveNetwork;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.security.LiveSecretProvider;
import com.aresstack.enterpriseai.app.security.ProxyAuthenticator;
import com.aresstack.enterpriseai.app.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.app.settings.ConfigurationFile;
import com.aresstack.enterpriseai.app.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.app.state.ApplicationStateService;
import com.aresstack.enterpriseai.app.settings.FileIndexActions;
import com.aresstack.enterpriseai.app.settings.FileSourceActions;
import com.aresstack.enterpriseai.app.settings.ConfigurationStartup;
import com.aresstack.enterpriseai.app.settings.SettingsMapper;
import com.aresstack.enterpriseai.app.settings.SettingsService;
import com.aresstack.enterpriseai.app.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.app.ui.settings.IndexDialog;
import com.aresstack.enterpriseai.app.ui.settings.IndexForm;
import com.aresstack.enterpriseai.app.ui.settings.IndexPanel;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialog;
import com.aresstack.enterpriseai.app.ui.settings.SourceActions;
import com.aresstack.enterpriseai.app.ui.settings.SourceDialog;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretProvider;
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
import java.util.Properties;
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
        final LocalVoices localVoices = new LocalVoices();
        final SettingsDialogActions settingsActions = headless ? null
                : SettingsAssembly.create(file, modelCatalogs, localVoices);
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
        // Netzschicht und Security-Port übernehmen spätere Einstellungen ohne Neustart (siehe applySettings).
        final LiveNetwork network = new LiveNetwork(NetworkServices.from(networkConfig));
        LOG.info("Proxy-Regel: " + network.current().routes().describe());
        LOG.info("TLS-Vertrauensquellen (werden bei der ersten HTTPS-Verbindung geladen): "
                + networkConfig.describeTrust());
        LOG.info("User-Agent: " + network.current().userAgent());
        ProxyAuthenticator.allowBasicTunneling();
        // Anwendungszustand (askai arch / MainframeMate): Fenster, Drawer und die angehakten Spaces je Quelle.
        final ApplicationStateService applicationState = new ApplicationStateService(AppPaths.applicationStateFile());
        final SourcePartitionSelection partitions = new SourcePartitionSelection(applicationState);
        final ApplicationPorts ports;
        final CompositionRoot root;
        final SettingsService settings;
        try {
            final LiveSecretProvider secrets = new LiveSecretProvider(vault(config));
            // Zugang „KeePass“ ohne eingetragene URL: die Basis-URL steht im KeePass-Eintrag des API-Keys.
            config = AdapterAssembly.withVaultLocations(config, file.read(), secrets);
            settings = new SettingsService(config, settingsLoader(file, secrets, config));
            ports = AdapterAssembly.createWith(config, network, secrets, partitions);
            applyProxyAuthentication(null, config, ports, localVoices);
            root = CompositionRoot.compose(settings, ports, SwingUtilities::invokeLater, System::currentTimeMillis,
                    null);
        } catch (AppConfigException | IOException e) {
            LOG.log(Level.SEVERE, "Konfiguration unvollständig", e);
            showError("Konfiguration", e.getMessage());
            AppLogFile.uninstall(logFile);
            System.exit(EXIT_CONFIG);
            return;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Anwendung konnte nicht zusammengesetzt werden", e);
            showError("Start fehlgeschlagen", "Die Anwendung konnte nicht gestartet werden: "
                    + e.getClass().getSimpleName() + ". " + logHint(logFile));
            AppLogFile.uninstall(logFile);
            System.exit(1);
            return;
        }
        // Sprachausgabe: Modell aus der Kategorie TTS, gesprochen über die Quelle dieses Modells (Enterprise-API oder
        // lokale Stimme im Prozess); ohne TTS-Modell bleibt sie aus und der Play/Pause-Orb nennt den Grund.
        // Satz- und Spracherkennung beim Vorlesen (OpenNLP wie askai arch), über alle Neubindungen dieselbe.
        final TextAnalysisService textAnalysis = SpeechOutput.textAnalysis();
        final SwitchableReadAloud readAloud = new SwitchableReadAloud(SpeechOutput.readAloud(config.models(),
                modelCatalogs.speech(config, config.models(), network.current(), chatToken(settings, ports)),
                textAnalysis));
        LOG.info("Sprachausgabe: " + readAloud.currentDescription());
        // Spracheingabe: Modell aus der Kategorie STT, erkannt über die Quelle dieses Modells (heute lokal im Prozess);
        // ohne STT-Modell bleibt der Mikrofon-Knopf deaktiviert und nennt den Grund.
        final Dictation dictation = new Dictation();
        dictation.bind(config.models(), modelCatalogs.speechRecognition(config.models()));
        LOG.info("Spracheingabe: " + dictation.currentDescription());
        root.shutdown().then("read-aloud", new Runnable() {
            @Override
            public void run() {
                readAloud.close();
                dictation.close();
            }
        });
        root.shutdown().then("local-voices", new Runnable() {
            @Override
            public void run() {
                modelCatalogs.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(root.shutdown().asShutdownHook());
        refreshModelsInBackground(modelCatalogs, settings, network, ports);
        // Beobachter auf dem Settings-Service (wie SettingsHelper.addChangeListener in MainframeMate): Adapter,
        // Netz, KeePass, Proxy-Anmeldung, Agent und Modellkatalog übernehmen gespeicherte Einstellungen sofort.
        settings.addListener(new SettingsService.Listener() {
            @Override
            public void settingsChanged(AppConfig previous, AppConfig current) {
                applySettings(previous, current, network, ports, root, localVoices);
                refreshModelsInBackground(modelCatalogs, settings, network, ports);
            }
        });

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
                // Fenstertitel, Sprachausgabe und Spracheingabe auf dem EDT nach jeder Änderung.
                settings.addListener(new SettingsService.Listener() {
                    @Override
                    public void settingsChanged(AppConfig previous, final AppConfig current) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                frame.setTitle(current.windowTitle());
                                rebindSpeech(readAloud, textAnalysis, dictation, current, modelCatalogs, network,
                                        settings, ports);
                            }
                        });
                    }
                });
                view.setSettingsAction(settingsAction(frame, file, settingsActions, palette, settings));
                view.setChatModels(modelCatalogs::cached, () -> installedLocalChats(localVoices,
                        settings.current().models().localModelRoot()), modelId -> {
                    try {
                        file.update(Collections.singletonMap(ModelsConfig.keyOf(ModelCategory.CHAT), modelId), null, null);
                    } catch (IOException | RuntimeException e) {
                        LOG.log(Level.WARNING, "Chat-Modell " + modelId + " nicht gespeichert; gilt bis zum Beenden", e);
                    }
                }, root.workExecutor());
                view.workspace().chatShell().transcript().setReadAloud(readAloud);
                view.workspace().chatShell().composer().setDictation(dictation);
                attachSourceEditing(view, frame, file, palette, settings);
                view.knowledgeSources().setPartitionSelection(partitions);
                ShellFrame.rememberBounds(frame, applicationState);
                view.workspace().setApplicationState(applicationState);
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

    /** Ordnernamen der installierten lokalen Chat-Modelle für die Modellliste des Composers. */
    private static List<String> installedLocalChats(LocalVoices localVoices, java.nio.file.Path modelRoot) {
        List<String> ids = new ArrayList<String>();
        try {
            for (com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer offer : localVoices.offers(modelRoot)) {
                if (offer.isInstalled() && offer.category() == ModelCategory.CHAT) {
                    ids.add(offer.id());
                }
            }
        } catch (RuntimeException unreadable) {
            LOG.log(Level.FINE, "Lokale Chat-Modelle nicht lesbar", unreadable);
        }
        return ids;
    }

    /** Der KeePass- bzw. „Manuell“-Port nach den KeePass-Einstellungen, mit Pairing-Dialog für deren Adresse. */
    private static SecretProvider vault(AppConfig config) {
        return AdapterAssembly.secrets(config, new SwingPairingCallback(
                config.keePass().rpc().host() + ":" + config.keePass().rpc().port()));
    }

    /**
     * Liest die gespeicherten Einstellungen wie der Start: Datei, bei geänderten KeePass-Einstellungen zuerst der
     * neue Security-Port, dann die Basis-URL aus KeePass (Zugang „KeePass“ ohne eingetragene URL).
     */
    private static SettingsService.Loader settingsLoader(final ConfigurationFile file,
                                                         final LiveSecretProvider secrets, AppConfig started) {
        final AtomicReference<String> keePass = new AtomicReference<String>(started.keePass().toString());
        return new SettingsService.Loader() {
            @Override
            public AppConfig load() throws IOException {
                Properties properties = file.read();
                AppConfig loaded = AppConfigLoader.fromProperties(properties);
                String vault = loaded.keePass().toString();
                if (!vault.equals(keePass.get())) {
                    secrets.replace(vault(loaded));
                    keePass.set(vault);
                    LOG.info("KeePass-Anbindung neu: " + vault);
                }
                return AdapterAssembly.withVaultLocations(loaded, properties, secrets);
            }
        };
    }

    /**
     * Übernimmt gespeicherte Einstellungen in den laufenden Graphen: Netz (Proxy, PAC, TLS, User-Agent), Chat- und
     * Embedding-Adapter samt Modellauswahl, Indexverzeichnis, Proxy-Anmeldung; eine offene Agent-Sitzung endet,
     * wenn sich die Agent-Einstellungen geändert haben (die nächste Nachricht startet den Agenten neu).
     */
    private static void applySettings(AppConfig previous, AppConfig current, LiveNetwork network,
                                      ApplicationPorts ports, CompositionRoot root, LocalVoices localVoices) {
        network.apply(current.network());
        ports.apply(current, network.current());
        applyProxyAuthentication(previous, current, ports, localVoices);
        if (!previous.agent().toString().equals(current.agent().toString())) {
            try {
                root.agentService().endSession();
            } catch (IllegalStateException busy) {
                LOG.info("Agent arbeitet noch; neue Agent-Einstellungen gelten ab der nächsten Agent-Sitzung");
            }
        }
        if (previous.network().preferIpv6() != current.network().preferIpv6()) {
            // Die JVM liest java.net.preferIPv6Addresses nur vor der ersten Namensauflösung.
            LOG.info("IPv6 bevorzugen gilt erst beim nächsten Start (die JVM liest den Wert nur einmal)");
        }
        LOG.info("Einstellungen übernommen: " + current);
    }

    /** Proxy-Anmeldung BASIC aus KeePass setzen, ändern oder entfernen. */
    private static void applyProxyAuthentication(AppConfig previous, AppConfig current, ApplicationPorts ports,
                                                 LocalVoices localVoices) {
        NetworkConfig network = current.network();
        boolean basic = network.proxyAuthMode() == ProxyAuthMode.BASIC && network.proxyCredentialRef() != null;
        if (basic) {
            ProxyAuthenticator.install(ports.secrets(), network.proxyCredentialRef());
            localVoices.proxyAuthenticationInstalled();
        } else if (previous != null && previous.network().proxyAuthMode() == ProxyAuthMode.BASIC) {
            ProxyAuthenticator.uninstall();
        }
    }

    /**
     * Das Zahnrad im Drawer: öffnet den Dialog mit den Werten der Datei. Gespeichert wird in die Datei; danach lädt
     * der Settings-Service sie neu und seine Beobachter übernehmen die Änderungen sofort, ohne Neustart.
     */
    private static Runnable settingsAction(final JFrame frame, final ConfigurationFile file,
                                           final SettingsDialogActions actions, final ComicPalette palette,
                                           final SettingsService settings) {
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
                reloadInBackground(settings, "Einstellungen");
            }
        };
    }

    /**
     * Lädt die gespeicherten Einstellungen außerhalb des EDT neu (KeePass kann dabei gefragt werden) und
     * benachrichtigt die Beobachter; scheitert das, bleibt der bisherige Stand und der Grund erscheint.
     */
    private static void reloadInBackground(final SettingsService settings, final String title) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    settings.reload();
                } catch (IOException | RuntimeException e) {
                    LOG.log(Level.WARNING, title + " gespeichert, aber nicht übernommen", e);
                    final String message = title + " sind gespeichert, konnten aber nicht übernommen werden: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            showError(title, message);
                        }
                    });
                }
            }
        }, "enterprise-ai-settings-reload");
        thread.setDaemon(true);
        thread.start();
    }

    /** Bindet Sprachausgabe und Spracheingabe an die gespeicherte TTS-/STT-Auswahl (auf dem EDT). */
    private static void rebindSpeech(SwitchableReadAloud readAloud, TextAnalysisService textAnalysis,
                                     Dictation dictation, AppConfig current,
                                     ModelCatalogs catalogs, LiveNetwork network, SettingsService settings,
                                     ApplicationPorts ports) {
        try {
            ModelsConfig saved = current.models();
            readAloud.replace(SpeechOutput.readAloud(saved,
                    catalogs.speech(current, saved, network.current(), chatToken(settings, ports)), textAnalysis));
            LOG.info("Sprachausgabe: " + readAloud.currentDescription());
            dictation.bind(saved, catalogs.speechRecognition(saved));
            LOG.info("Spracheingabe: " + dictation.currentDescription());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Sprachausgabe/-eingabe nicht neu gebunden", e);
        }
    }

    /** Das Bearer-Token des Chats, je Anfrage frisch: Eintrag aus den aktuellen Einstellungen, Wert aus KeePass. */
    private static java.util.function.Supplier<String> chatToken(final SettingsService settings,
                                                                 final ApplicationPorts ports) {
        return new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                SecretRef ref = settings.current().chat().apiKeyRef();
                return ref == null ? null : new SecretBackedTokenSource(ports.secrets(), ref).token();
            }
        };
    }

    /**
     * Fragt die Modellquelle (KIPITZ {@code GET /models}) im Hintergrund ab, damit der Reiter „Modelle“ aktuelle
     * Listen hat; blockiert weder Start noch EDT. Fehler landen nur im Protokoll.
     */
    private static void refreshModelsInBackground(final ModelCatalogs catalogs, final SettingsService settings,
                                                  final LiveNetwork network, final ApplicationPorts ports) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    catalogs.refresh(settings.current(), network.current(), chatToken(settings, ports));
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Modellkatalog konnte nicht abgefragt werden", e);
                }
            }
        }, "enterprise-ai-model-catalog");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Der Drawer-Reiter „Wissensquellen“ bearbeitet die Quellen der Datei über den Quellen-Dialog und mit
     * „Index …“ Indexverzeichnis und Indexierung beim Start über den Index-Dialog.
     */
    private static void attachSourceEditing(ShellAssembly.ShellView view, final JFrame frame,
                                            ConfigurationFile file, final ComicPalette palette,
                                            SettingsService settings) {
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
        view.workspace().knowledgeSources().setIndexSettingsAction(indexSettingsAction(frame, file, palette,
                settings));
    }

    /**
     * „Index …“ im Drawer-Reiter „Wissensquellen“: öffnet den Index-Dialog mit den Werten der Datei. Gespeichert
     * wird in die Datei; danach übernimmt der Settings-Service das Indexverzeichnis sofort.
     */
    private static Runnable indexSettingsAction(final JFrame frame, final ConfigurationFile file,
                                                final ComicPalette palette, final SettingsService settings) {
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
            reloadInBackground(settings, "Index-Einstellungen");
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
