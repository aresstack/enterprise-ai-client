package com.aresstack.enterpriseai.proasteion.exedra;

import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationStart;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.exedra.security.SwingUserPrompts;
import com.aresstack.enterpriseai.proasteion.application.composition.ModelCatalogs;
import com.aresstack.enterpriseai.proasteion.exedra.speech.Dictation;
import com.aresstack.enterpriseai.proasteion.exedra.speech.SwitchableReadAloud;
import com.aresstack.enterpriseai.proasteion.exedra.speech.SpeechOutput;
import com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService;
import com.aresstack.enterpriseai.proasteion.application.composition.LocalVoices;
import com.aresstack.enterpriseai.proasteion.exedra.assembly.SettingsAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.assembly.ShellAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.StartupNotices;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.KnowledgeSourcesController;
import com.aresstack.enterpriseai.proasteion.application.net.LiveNetwork;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.settings.FileIndexActions;
import com.aresstack.enterpriseai.proasteion.application.settings.FileSourceActions;
import com.aresstack.enterpriseai.proasteion.exedra.settings.ConfigurationStartup;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.IndexDialog;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.IndexForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.IndexPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialog;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SourceActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SourceDialog;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsPanel;
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
 * Einstiegspunkt der Desktop-Anwendung (Schale exedra). Ablauf: Protokolldatei anhängen, Konfiguration beschaffen
 * (fehlt sie oder lädt sie nicht, öffnet sich mit Oberfläche der Einstellungen-Dialog; ohne Display wird die
 * Beispieldatei angelegt und erklärt), die Anwendung im Kompositionspunkt ohne Oberfläche zusammensetzen
 * ({@link ApplicationStart}, corenth ADR-0001: Netz, Tresor, Adapter, Graph, Übernahme gespeicherter
 * Einstellungen), Sprache binden, Shutdown-Hook registrieren, Fenster zeigen. Schließen des Fensters fährt geordnet herunter und beendet die JVM; der
 * Shutdown-Hook deckt hartes Beenden ab (beide idempotent).
 *
 * <p>Start: {@code ./gradlew :proasteion:exedra:run}; Konfigurationsdatei per {@code -Denterpriseai.config=<Pfad>},
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
        final LocalVoices localVoices = new LocalVoices(new SwingUserPrompts());
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
        // Anwendungszustand (askai arch / MainframeMate): Fenster, Drawer und die angehakten Spaces je Quelle.
        final ApplicationStateService applicationState = new ApplicationStateService(AppPaths.applicationStateFile());
        ApplicationStart.dropLegacySourceEntries(config.knowledge().indexDirectory(), applicationState);
        final SourcePartitionSelection partitions = new SourcePartitionSelection(applicationState);
        // Zusammengesetzt wird im Kompositionspunkt ohne Oberfläche (proasteion:application, corenth ADR-0001);
        // die Schale liefert nur ihre Dialoge (Pairing, Zugang „Manuell“) und den UI-Executor.
        final ApplicationStart application;
        try {
            application = ApplicationStart.start(file, config, new SwingUserPrompts(), localVoices, modelCatalogs,
                    partitions, SwingUtilities::invokeLater, System::currentTimeMillis, null);
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
        final CompositionRoot root = application.root();
        final SettingsService settings = application.settings();
        final LiveNetwork network = application.network();
        final java.util.function.Supplier<String> chatToken = application.chatToken();
        config = settings.current(); // mit der Basis-URL aus KeePass (Zugang „KeePass“ ohne eingetragene URL)
        // Sprachausgabe: Modell aus der Kategorie TTS, gesprochen über die Quelle dieses Modells (Enterprise-API oder
        // lokale Stimme im Prozess); ohne TTS-Modell bleibt sie aus und der Play/Pause-Orb nennt den Grund.
        // Satz- und Spracherkennung beim Vorlesen (OpenNLP wie askai arch), über alle Neubindungen dieselbe.
        final TextAnalysisService textAnalysis = SpeechOutput.textAnalysis();
        final SwitchableReadAloud readAloud = new SwitchableReadAloud(SpeechOutput.readAloud(config.models(),
                modelCatalogs.speech(config, config.models(), network.current(), chatToken),
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
                                        chatToken);
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
            for (com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer offer : localVoices.offers(modelRoot)) {
                if (offer.isInstalled() && offer.category() == ModelCategory.CHAT) {
                    ids.add(offer.id());
                }
            }
        } catch (RuntimeException unreadable) {
            LOG.log(Level.FINE, "Lokale Chat-Modelle nicht lesbar", unreadable);
        }
        return ids;
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
                                     ModelCatalogs catalogs, LiveNetwork network,
                                     java.util.function.Supplier<String> chatToken) {
        try {
            ModelsConfig saved = current.models();
            readAloud.replace(SpeechOutput.readAloud(saved,
                    catalogs.speech(current, saved, network.current(), chatToken), textAnalysis));
            LOG.info("Sprachausgabe: " + readAloud.currentDescription());
            dictation.bind(saved, catalogs.speechRecognition(saved));
            LOG.info("Spracheingabe: " + dictation.currentDescription());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Sprachausgabe/-eingabe nicht neu gebunden", e);
        }
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
