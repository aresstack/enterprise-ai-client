package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.settings.ModelCatalogLoader;
import com.aresstack.enterpriseai.proasteion.application.settings.LocalVoiceInstaller;
import com.aresstack.enterpriseai.proasteion.application.settings.HubSearchHistory;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.settings.FileSourceActions;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.config.VaultLocations;
import com.aresstack.enterpriseai.proasteion.application.net.HttpRoutes;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.speech.NlpModels;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceInstallProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceSearchCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.NetworkLogListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordStore;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.NlpModelOffer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * {@link SettingsDialogActions} über der Konfigurationsdatei: Prüfen heißt, die Datei mit dem Entwurf zu
 * verschmelzen, durch den {@link AppConfigLoader} zu schicken und die {@link ConfigurationCheck} des Starts
 * laufen zu lassen (dieselben Regeln wie beim Start, Meldungen mit Feldnamen); Speichern schreibt nur die
 * verwalteten Schlüssel. KeePass-Probe und Verbindungstest laufen auf dem Arbeits-Executor,
 * ihre Ergebnisse kommen über den UI-Executor zurück.
 */
public final class FileSettingsActions implements SettingsDialogActions {

    static final String STEP_CONFIGURATION = "Konfiguration";
    static final String STEP_TEST = "Verbindungstest";

    private final ConfigurationFile file;
    private final SecretChecker secretChecker;
    private final ConnectionChecker connectionChecker;
    private final ConfigurationCheck check;
    private final Executor worker;
    private final Executor ui;
    private final ModelCatalogLoader models;
    private final LocalVoiceInstaller voices;
    private final NlpModels nlpModels;

    /** Wie der Konstruktor mit {@link ConfigurationCheck}, ohne zusätzliche Prüfung (nur der Loader). */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker, Executor worker, Executor ui) {
        this(file, secretChecker, ConfigurationCheck.none(), worker, ui);
    }

    /** Wie der vollständige Konstruktor, ohne Verbindungstest (der Knopf meldet, dass er nicht verfügbar ist). */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker, ConfigurationCheck check,
                               Executor worker, Executor ui) {
        this(file, secretChecker, null, check, worker, ui);
    }

    /**
     * @param secretChecker     die KeePass-Probe oder {@code null}, wenn keine möglich ist (Prüfen meldet das)
     * @param connectionChecker der Verbindungstest oder {@code null}, wenn keiner möglich ist (der Knopf meldet das)
     * @param check             zusätzliche Prüfung des Starts (z. B. TLS-Vertrauensregel), läuft bei Prüfen und
     *                          Speichern
     * @param worker            führt Probe und Verbindungstest aus (nie der EDT)
     * @param ui                liefert die Ergebnisse ab (produktiv {@code SwingUtilities::invokeLater})
     */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker,
                               ConnectionChecker connectionChecker, ConfigurationCheck check,
                               Executor worker, Executor ui) {
        this(file, secretChecker, connectionChecker, check, null, worker, ui);
    }

    /**
     * Wie oben, dazu die Modellabfrage des Reiters „Modelle“.
     *
     * @param models Zwischenspeicher und Abfrage der Modellquellen oder {@code null} (dann keine Listen)
     */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker,
                               ConnectionChecker connectionChecker, ConfigurationCheck check,
                               ModelCatalogLoader models, Executor worker, Executor ui) {
        this(file, secretChecker, connectionChecker, check, models, null, worker, ui);
    }

    /**
     * Wie oben, dazu die lokalen Stimmen der Sprachausgabe.
     *
     * @param voices Anzeige und Installation lokaler Stimmen oder {@code null} (dann bleibt die Liste leer)
     */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker,
                               ConnectionChecker connectionChecker, ConfigurationCheck check,
                               ModelCatalogLoader models, LocalVoiceInstaller voices, Executor worker, Executor ui) {
        this(file, secretChecker, connectionChecker, check, models, voices, null, worker, ui);
    }

    /**
     * Wie oben, dazu die OpenNLP-Modelle des Vorlesens.
     *
     * @param nlpModels Liste und Installation der OpenNLP-Modelle oder {@code null} (dann bleibt die Liste leer)
     */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker,
                               ConnectionChecker connectionChecker, ConfigurationCheck check,
                               ModelCatalogLoader models, LocalVoiceInstaller voices, NlpModels nlpModels,
                               Executor worker, Executor ui) {
        if (file == null || check == null || worker == null || ui == null) {
            throw new IllegalArgumentException("file, check, worker and ui must not be null");
        }
        this.file = file;
        this.secretChecker = secretChecker;
        this.connectionChecker = connectionChecker;
        this.check = check;
        this.worker = worker;
        this.ui = ui;
        this.models = models;
        this.voices = voices;
        this.nlpModels = nlpModels;
    }

    /** Wie viel vom Ende der Protokolldatei „Technische Details“ zeigt. */
    static final int TECHNICAL_TAIL_BYTES = 64 * 1024;

    /** Konfigurationsdatei, Protokolldatei und deren letzte Zeilen (askai arch: „Technical Details“). */
    @Override
    public String technicalDetails() {
        Path log = AppPaths.defaultLogDirectory().resolve("enterprise-ai-client.0.log");
        StringBuilder text = new StringBuilder();
        text.append("Konfiguration: ").append(file.path().toAbsolutePath()).append('\n');
        text.append("Protokoll: ").append(log.toAbsolutePath()).append('\n');
        text.append("Java: ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")\n\n");
        if (!Files.isRegularFile(log)) {
            return text.append("Keine Protokolldatei vorhanden.").toString();
        }
        try (RandomAccessFile in = new RandomAccessFile(log.toFile(), "r")) {
            long length = in.length();
            long start = Math.max(0L, length - TECHNICAL_TAIL_BYTES);
            byte[] bytes = new byte[(int) (length - start)];
            in.seek(start);
            in.readFully(bytes);
            String tail = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) {
                int firstLine = tail.indexOf('\n');
                tail = firstLine < 0 ? tail : tail.substring(firstLine + 1);
                text.append("… (gekürzt auf die letzten ").append(TECHNICAL_TAIL_BYTES / 1024).append(" KB)\n");
            }
            return text.append(tail).toString();
        } catch (IOException | RuntimeException e) {
            return text.append("Protokoll nicht lesbar: ").append(e.getClass().getSimpleName()).append(' ')
                    .append(e.getMessage()).toString();
        }
    }

    public ConfigurationFile file() {
        return file;
    }

    /**
     * Die Datei als Properties; fehlt sie (Erststart), zählt die Vorlage, weil {@link #save} genau daraus
     * schreibt. So prüft {@link #validate} dasselbe, was nachher auf der Platte steht.
     */
    private Properties current() throws IOException {
        if (file.exists()) {
            return file.read();
        }
        Properties template = new Properties();
        template.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        return template;
    }

    @Override
    public List<String> validate(SettingsForm form) {
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }
        Properties current;
        try {
            current = current();
        } catch (IOException e) {
            return Collections.singletonList("Konfigurationsdatei nicht lesbar: " + file.path() + " ("
                    + e.getClass().getSimpleName() + ")");
        }
        try {
            AppConfig config = AppConfigLoader.fromProperties(SettingsMapper.merge(current,
                    repair(form, brokenIndexKeys(current))));
            check.verify(config);
            return Collections.emptyList();
        } catch (AppConfigException e) {
            return SettingsMapper.describe(e.problems());
        } catch (RuntimeException e) {
            return Collections.singletonList("Konfiguration ungültig: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void save(SettingsForm form) throws IOException {
        List<String> problems = validate(form);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Entwurf hat Probleme: " + problems);
        }
        Properties current = current();
        Set<String> broken = brokenIndexKeys(current);
        SettingsForm repaired = repair(form, broken);
        // Leere Felder sind Entfernungen (Zeile auskommentieren), nie "schlüssel=" ohne Wert.
        // Quellen (FileSourceActions) und Index-Einstellungen (FileIndexActions) verwaltet der Drawer-Reiter
        // „Wissensquellen“; der Dialog schreibt sie nicht zurück, sonst würde eine fehlerhafte Quelle beim
        // Speichern anderer Einstellungen umgeschrieben. Ausnahme: ein Index-Schlüssel, der in der Datei ungültig
        // steht. Dann läuft dieser Dialog beim Start vor dem Hauptfenster und ist die einzige Stelle, die ihn
        // reparieren kann; sonst käme der Start nie über den Dialog hinaus.
        Map<String, String> writes = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : SettingsMapper.writes(repaired).entrySet()) {
            if (!managedElsewhere(entry.getKey(), broken)) {
                writes.put(entry.getKey(), entry.getValue());
            }
        }
        Set<String> removals = new LinkedHashSet<String>();
        for (String key : SettingsMapper.removals(repaired, current)) {
            if (!managedElsewhere(key, broken)) {
                removals.add(key);
            }
        }
        file.update(writes, removals, AppConfigLoader.exampleConfiguration());
    }

    /**
     * Schlüssel, die die Dialoge des Drawer-Reiters „Wissensquellen“ schreiben: Quellen und Index-Einstellungen,
     * letztere nur, solange sie in der Datei gültig stehen ({@code brokenIndexKeys}).
     */
    private static boolean managedElsewhere(String key, Set<String> brokenIndexKeys) {
        return FileSourceActions.isSourceKey(key)
                || (FileIndexActions.isIndexKey(key) && !brokenIndexKeys.contains(key));
    }

    /**
     * Die Index-Schlüssel, die der Loader in der Datei selbst beanstandet (Problemzeilen {@code schlüssel: …} zu
     * {@code knowledge.indexDirectory} oder {@code knowledge.indexOnStartup}); leer, wenn die Datei insoweit in
     * Ordnung ist oder fehlt (dann zählt die Vorlage).
     */
    private static Set<String> brokenIndexKeys(Properties current) {
        Set<String> broken = new LinkedHashSet<String>();
        try {
            AppConfigLoader.fromProperties(current);
        } catch (AppConfigException e) {
            for (String problem : e.problems()) {
                int colon = problem.indexOf(':');
                String key = (colon < 0 ? problem : problem.substring(0, colon)).trim();
                if (FileIndexActions.isIndexKey(key)) {
                    broken.add(key);
                }
            }
        } catch (RuntimeException e) {
            // Unklare Datei: hier wird nichts repariert; die Prüfung meldet das Problem.
        }
        return broken;
    }

    /**
     * Das Formular, wie es gespeichert wird: ein in der Datei unbrauchbares Indexverzeichnis wird geleert (die
     * Zeile auskommentiert, es gilt das Standardverzeichnis; der Index-Dialog der Seitenleiste setzt später ein
     * neues). Ein unbrauchbares Häkchen trägt das Formular bereits normalisiert ({@link SettingsMapper#fromProperties}).
     */
    private static SettingsForm repair(SettingsForm form, Set<String> brokenIndexKeys) {
        if (!brokenIndexKeys.contains(SettingsMapper.KEY_INDEX_DIRECTORY)) {
            return form;
        }
        return form.toBuilder().indexDirectory("").build();
    }

    @Override
    public void checkSecret(final SettingsForm form, final String secretRef,
                            final Consumer<SecretCheckResult> onResult) {
        if (form == null || onResult == null) {
            throw new IllegalArgumentException("form and onResult must not be null");
        }
        final SecretRef ref;
        try {
            ref = SecretRef.of(secretRef == null ? "" : secretRef.trim());
        } catch (IllegalArgumentException e) {
            onResult.accept(SecretCheckResult.failed("Bitte zuerst den Titel des KeePass-Eintrags eintragen."));
            return;
        }
        if (secretChecker == null) {
            onResult.accept(SecretCheckResult.failed("Die KeePass-Probe ist in dieser Umgebung nicht verfügbar."));
            return;
        }
        final KeePassConfig keePass;
        try {
            keePass = AppConfigLoader.keePassSection(SettingsMapper.merge(current(), form));
        } catch (AppConfigException e) {
            onResult.accept(SecretCheckResult.failed("KeePass-Einstellungen ungültig: "
                    + SettingsMapper.describe(e.problems())));
            return;
        } catch (IOException e) {
            onResult.accept(SecretCheckResult.failed("Konfigurationsdatei nicht lesbar ("
                    + e.getClass().getSimpleName() + ")."));
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    SecretCheckResult result;
                    try {
                        result = secretChecker.check(keePass, ref);
                    } catch (RuntimeException e) {
                        result = SecretCheckResult.failed("Prüfung fehlgeschlagen: " + e.getClass().getSimpleName());
                    }
                    final SecretCheckResult delivered = result;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            onResult.accept(delivered);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            onResult.accept(SecretCheckResult.failed("Prüfung konnte nicht gestartet werden."));
        }
    }

    @Override
    public void keePassEntries(SettingsForm form, final Consumer<List<String>> onResult) {
        if (form == null || onResult == null) {
            throw new IllegalArgumentException("form and onResult must not be null");
        }
        final KeePassConfig keePass;
        try {
            keePass = AppConfigLoader.keePassSection(SettingsMapper.merge(current(), form));
        } catch (IOException | RuntimeException e) {
            onResult.accept(Collections.<String>emptyList());
            return;
        }
        if (secretChecker == null) {
            onResult.accept(Collections.<String>emptyList());
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    List<String> titles;
                    try {
                        titles = secretChecker.entryTitles(keePass);
                    } catch (Exception e) {
                        titles = Collections.emptyList(); // KeePass zu: das Feld bleibt eine freie Eingabe
                    }
                    final List<String> delivered = titles;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            onResult.accept(delivered);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            onResult.accept(Collections.<String>emptyList());
        }
    }

    @Override
    public PasswordStore passwordStore(SettingsForm form) {
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }
        final KeePassConfig keePass;
        try {
            keePass = AppConfigLoader.keePassSection(SettingsMapper.merge(current(), form));
        } catch (IOException | RuntimeException e) {
            return PasswordStore.unavailable("Die KeePass-Einstellungen sind ungültig.");
        }
        if (secretChecker == null) {
            return PasswordStore.unavailable("KeePass ist hier nicht verfügbar.");
        }
        if (!keePass.enabled()) {
            return PasswordStore.unavailable("KeePassRPC ist ausgeschaltet.");
        }
        NetworkConfig network;
        try {
            network = AppConfigLoader.networkSection(SettingsMapper.merge(current(), form));
        } catch (IOException | RuntimeException e) {
            network = null;
        }
        final NetworkConfig testNetwork = network;
        return new VaultPasswordStore(() -> secretChecker.vault(keePass), worker, ui,
                testNetwork == null ? null : (url, user, password, alias) ->
                        secretChecker.testConfluence(testNetwork, url, user, password, alias));
    }

    /**
     * Zugang „KeePass“ ohne eingetragene URL: die Basis-URL kommt aus dem KeePass-Eintrag (nur auf dem
     * Arbeits-Executor, KeePass kann blockieren).
     */
    private AppConfig located(AppConfig config, Properties merged) {
        if (config.chat() == null || config.chat().baseUrl() != null) {
            return config;
        }
        if (secretChecker == null) {
            throw new AppConfigException("Basis-URL des KI-Dienstes fehlt (KeePass ist hier nicht verfügbar).");
        }
        final KeePassConfig keePass = config.keePass();
        return AppConfigLoader.fromProperties(VaultLocations.complete(merged, new VaultLocations.Lookup() {
            @Override
            public String locationOf(SecretRef ref) throws Exception {
                return secretChecker.locationOf(keePass, ref);
            }
        }));
    }

    /**
     * Der Entwurf geht durch den Loader (ein ungültiger Entwurf endet hier mit einem Schritt „Konfiguration“); mit
     * der geladenen Konfiguration läuft der {@link ConnectionChecker} auf dem Arbeits-Executor, jeder Schritt und
     * das Ende kommen über den UI-Executor.
     */
    @Override
    public void checkConnection(SettingsForm form, final ConnectionCheckListener listener) {
        if (form == null || listener == null) {
            throw new IllegalArgumentException("form and listener must not be null");
        }
        final AppConfig config;
        final Properties merged;
        try {
            merged = SettingsMapper.merge(current(), form);
            config = AppConfigLoader.fromProperties(merged);
        } catch (AppConfigException e) {
            finish(listener, ConnectionCheckStep.failed(STEP_CONFIGURATION, "Der Entwurf ist ungültig: "
                    + join(SettingsMapper.describe(e.problems()))));
            return;
        } catch (IOException e) {
            finish(listener, ConnectionCheckStep.failed(STEP_CONFIGURATION, "Konfigurationsdatei nicht lesbar ("
                    + e.getClass().getSimpleName() + ")."));
            return;
        } catch (RuntimeException e) {
            finish(listener, ConnectionCheckStep.failed(STEP_CONFIGURATION, "Der Entwurf ist ungültig ("
                    + e.getClass().getSimpleName() + ")."));
            return;
        }
        if (connectionChecker == null) {
            finish(listener, ConnectionCheckStep.failed(STEP_TEST,
                    "Der Verbindungstest ist in dieser Umgebung nicht verfügbar."));
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    boolean success;
                    try {
                        AppConfig located = located(config, merged);
                        success = connectionChecker.check(located, new Consumer<ConnectionCheckStep>() {
                            @Override
                            public void accept(final ConnectionCheckStep step) {
                                ui.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        listener.onStep(step);
                                    }
                                });
                            }
                        });
                    } catch (RuntimeException e) {
                        final ConnectionCheckStep aborted = e instanceof AppConfigException
                                ? ConnectionCheckStep.failed(STEP_CONFIGURATION, e.getMessage())
                                : ConnectionCheckStep.failed(STEP_TEST, "Abgebrochen: " + e.getClass().getSimpleName());
                        ui.execute(new Runnable() {
                            @Override
                            public void run() {
                                listener.onStep(aborted);
                            }
                        });
                        success = false;
                    }
                    final boolean delivered = success;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFinished(delivered);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            finish(listener, ConnectionCheckStep.failed(STEP_TEST, "Der Verbindungstest konnte nicht gestartet werden."));
        }
    }

    @Override
    public ModelCatalogSnapshot cachedModels() {
        return models == null ? ModelCatalogSnapshot.empty() : models.cached();
    }

    /**
     * Der Entwurf geht durch den Loader; fehlen nur Chat- oder Embedding-Modell (Erststart: die Liste soll ja erst
     * bei der Wahl helfen), setzt die Abfrage Platzhalter dafür ein. Die Abfrage läuft auf dem Arbeits-Executor, das
     * Ergebnis kommt über den UI-Executor.
     */
    @Override
    public void refreshModels(SettingsForm form, final Consumer<ModelCatalogSnapshot> onResult) {
        if (form == null || onResult == null) {
            throw new IllegalArgumentException("form and onResult must not be null");
        }
        if (models == null) {
            onResult.accept(failed("Modellabfrage in dieser Umgebung nicht verfügbar."));
            return;
        }
        SettingsForm.Builder probe = form.toBuilder();
        if (form.chatModel().isEmpty()) {
            probe.chatModel("-");
        }
        if (form.embeddingModel().isEmpty()) {
            probe.embeddingModel("-");
        }
        if (form.embeddingDimension().isEmpty()) {
            probe.embeddingDimension("1");
        }
        final AppConfig config;
        final Properties merged;
        try {
            merged = SettingsMapper.merge(current(), probe.build());
            config = AppConfigLoader.fromProperties(merged);
        } catch (AppConfigException e) {
            onResult.accept(failed("Entwurf unvollständig: " + join(SettingsMapper.describe(e.problems()))));
            return;
        } catch (IOException | RuntimeException e) {
            onResult.accept(failed("Entwurf nicht lesbar (" + e.getClass().getSimpleName() + ")."));
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    ModelCatalogSnapshot result;
                    try {
                        result = models.refresh(located(config, merged));
                    } catch (AppConfigException e) {
                        result = failed(e.getMessage());
                    } catch (RuntimeException e) {
                        result = failed("Abfrage fehlgeschlagen: " + e.getClass().getSimpleName());
                    }
                    final ModelCatalogSnapshot delivered = result;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            onResult.accept(delivered);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            onResult.accept(failed("Abfrage konnte nicht gestartet werden."));
        }
    }

    @Override
    public void localVoices(SettingsForm form, final Consumer<List<LocalVoiceOffer>> onResult) {
        if (form == null || onResult == null) {
            throw new IllegalArgumentException("form and onResult must not be null");
        }
        if (voices == null) {
            onResult.accept(Collections.<LocalVoiceOffer>emptyList());
            return;
        }
        final Path root = localModelRoot(form);
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    List<LocalVoiceOffer> result;
                    try {
                        result = voices.offers(root);
                    } catch (RuntimeException e) {
                        result = Collections.emptyList();
                    }
                    final List<LocalVoiceOffer> delivered = result;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            onResult.accept(delivered);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            onResult.accept(Collections.<LocalVoiceOffer>emptyList());
        }
    }

    /**
     * Lädt mit den Netzwerkeinstellungen des Entwurfs; nur dieser Abschnitt wird geprüft, der Rest darf noch
     * unvollständig sein (Erststart).
     * Fortschritt höchstens einmal je Prozentpunkt und Datei.
     */
    @Override
    public void installLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        download(form, voiceId, progress, false);
    }

    @Override
    public void updateLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        download(form, voiceId, progress, true);
    }

    private void download(SettingsForm form, final String voiceId, final LocalVoiceInstallProgress progress,
                          final boolean update) {
        if (form == null || voiceId == null || progress == null) {
            throw new IllegalArgumentException("form, voiceId and progress must not be null");
        }
        if (voices == null) {
            progress.finished(false, "In dieser Umgebung nicht verfügbar.");
            return;
        }
        final Path root = localModelRoot(form);
        final NetworkDraft draft;
        try {
            draft = networkDraft(form);
        } catch (IllegalStateException e) {
            progress.finished(false, e.getMessage());
            return;
        }
        final NetworkConfig network = draft.network;
        final KeePassConfig keePass = draft.keePass;
        try {
            worker.execute(new Runnable() {
                private String lastLine = "";

                @Override
                public void run() {
                    boolean ok;
                    String message;
                    try {
                        LocalVoiceInstallListener listener = new LocalVoiceInstallListener() {
                            @Override
                            public void progress(final String file, final int number, final int count,
                                                 final long done, final long total) {
                                // nur bei geändertem Prozent (bzw. MB ohne Größe) an den EDT, nicht je Puffer
                                final String line = number + "/" + count + ":" + file + ":"
                                        + (total > 0 ? done * 100 / total : done / (1024L * 1024L));
                                if (line.equals(lastLine)) {
                                    return;
                                }
                                lastLine = line;
                                ui.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        progress.file(file, number, count, done, total);
                                    }
                                });
                            }
                        };
                        if (update) {
                            voices.update(network, keePass, voiceId, root, listener);
                        } else {
                            voices.install(network, keePass, voiceId, root, listener);
                        }
                        ok = true;
                        message = (update ? "Aktualisiert in " : "Installiert in ") + root.resolve(voiceId);
                    } catch (LocalVoiceInstallException e) {
                        ok = false;
                        message = e.getMessage();
                    } catch (RuntimeException e) {
                        ok = false;
                        message = "Installation fehlgeschlagen: " + e;
                    }
                    final boolean installed = ok;
                    final String result = message;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            progress.finished(installed, result);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            progress.finished(false, "Installation konnte nicht gestartet werden.");
        }
    }

    @Override
    public void nlpModels(Consumer<List<NlpModelOffer>> onResult) {
        List<NlpModelOffer> offers = new ArrayList<NlpModelOffer>();
        if (nlpModels != null) {
            for (NlpModels.Entry entry : nlpModels.entries()) {
                offers.add(new NlpModelOffer(entry.id(), entry.name(), nlpModels.isInstalled(entry)));
            }
        }
        onResult.accept(offers);
    }

    /** Lädt mit den Netzwerkeinstellungen des Entwurfs; Ergebnis genau einmal auf dem EDT. */
    @Override
    public void installNlpModel(SettingsForm form, String id, final BiConsumer<Boolean, String> onFinished) {
        final NlpModels.Entry entry = nlpModels == null ? null : nlpModels.entry(id);
        if (entry == null) {
            onFinished.accept(Boolean.FALSE, "In dieser Umgebung nicht verfügbar.");
            return;
        }
        final NetworkConfig network;
        try {
            network = networkDraft(form).network;
        } catch (IllegalStateException e) {
            onFinished.accept(Boolean.FALSE, e.getMessage());
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    boolean ok;
                    String message;
                    try {
                        nlpModels.install(entry, NetworkServices.from(network));
                        ok = true;
                        message = "✓ installiert";
                    } catch (IOException | RuntimeException e) {
                        ok = false;
                        message = "Installation fehlgeschlagen: " + (e.getMessage() == null
                                ? e.getClass().getSimpleName() : e.getMessage());
                    }
                    final boolean installed = ok;
                    final String result = message;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            onFinished.accept(installed, result);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            onFinished.accept(Boolean.FALSE, "Installation konnte nicht gestartet werden.");
        }
    }

    /** Netzwerk- und KeePass-Abschnitt des Entwurfs für Downloads und Suche. */
    private static final class NetworkDraft {
        final NetworkConfig network;
        final KeePassConfig keePass;

        NetworkDraft(NetworkConfig network, KeePassConfig keePass) {
            this.network = network;
            this.keePass = keePass;
        }
    }

    /** @throws IllegalStateException mit dem Grund für die Oberfläche, wenn der Netzwerkabschnitt nicht passt */
    private NetworkDraft networkDraft(SettingsForm form) {
        try {
            Properties merged = SettingsMapper.merge(current(), form);
            NetworkConfig network = AppConfigLoader.networkSection(merged);
            KeePassConfig keePass;
            try {
                keePass = AppConfigLoader.keePassSection(merged);
            } catch (AppConfigException noKeePass) {
                // nur für die Proxy-Anmeldung nötig; ohne gültigen Abschnitt lädt der Download ohne sie
                keePass = null;
            }
            return new NetworkDraft(network, keePass);
        } catch (AppConfigException e) {
            throw new IllegalStateException("Netzwerkeinstellungen unvollständig: "
                    + join(SettingsMapper.describe(e.problems())));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Entwurf nicht lesbar (" + e.getClass().getSimpleName() + ").");
        }
    }

    /** Hub-Suche mit den Netzwerkeinstellungen des Entwurfs; Ergebnis genau einmal auf dem EDT. */
    @Override
    public void searchLocalVoices(SettingsForm form, final String query, final ModelCategory category,
                                  final LocalVoiceSearchCallback callback) {
        if (form == null || callback == null) {
            throw new IllegalArgumentException("form and callback must not be null");
        }
        if (voices == null) {
            callback.failed("In dieser Umgebung nicht verfügbar.");
            return;
        }
        final Path root = localModelRoot(form);
        final NetworkDraft draft;
        try {
            draft = networkDraft(form);
        } catch (IllegalStateException e) {
            callback.failed(e.getMessage());
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    List<LocalVoiceOffer> found = null;
                    String message = null;
                    try {
                        found = voices.search(draft.network, draft.keePass, query, category, root);
                    } catch (LocalVoiceInstallException e) {
                        message = e.getMessage();
                    } catch (RuntimeException e) {
                        message = "Suche fehlgeschlagen: " + e;
                    }
                    final List<LocalVoiceOffer> result = found;
                    final String error = message;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (result != null) {
                                callback.found(result);
                            } else {
                                callback.failed(error);
                            }
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            callback.failed("Suche konnte nicht gestartet werden.");
        }
    }

    @Override
    public void removeLocalVoice(SettingsForm form, final String voiceId, final LocalVoiceInstallProgress progress) {
        if (form == null || voiceId == null || progress == null) {
            throw new IllegalArgumentException("form, voiceId and progress must not be null");
        }
        if (voices == null) {
            progress.finished(false, "In dieser Umgebung nicht verfügbar.");
            return;
        }
        final Path root = localModelRoot(form);
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    boolean ok;
                    String message;
                    try {
                        voices.remove(voiceId, root);
                        ok = true;
                        message = "Entfernt aus " + root;
                    } catch (LocalVoiceInstallException e) {
                        ok = false;
                        message = e.getMessage();
                    } catch (RuntimeException e) {
                        ok = false;
                        message = "Entfernen fehlgeschlagen: " + e;
                    }
                    final boolean removed = ok;
                    final String result = message;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            progress.finished(removed, result);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            progress.finished(false, "Entfernen konnte nicht gestartet werden.");
        }
    }

    @Override
    public boolean isLocal(ModelReference reference) {
        return !ModelsConfig.defaultCatalogId().equals(reference.catalogId());
    }

    @Override
    public List<String> hubSearchHistory() {
        return HubSearchHistory.read(historyFile());
    }

    @Override
    public List<String> rememberHubSearch(String query) {
        return HubSearchHistory.remember(historyFile(), query);
    }

    /** Neben der Konfigurationsdatei. */
    private Path historyFile() {
        return file.path().toAbsolutePath().resolveSibling(HubSearchHistory.FILE_NAME);
    }

    @Override
    public String localVoiceSelection(String voiceId) {
        return ModelsConfig.LOCAL_CATALOG_ID + ":" + voiceId;
    }

    /** Modellverzeichnis des Entwurfs; leer heißt wie beim Start {@code <Anwendungsverzeichnis>/local-models}. */
    static Path localModelRoot(SettingsForm form) {
        String root = form.localModelRoot() == null ? "" : form.localModelRoot().trim();
        if (!root.isEmpty()) {
            try {
                return java.nio.file.Paths.get(root);
            } catch (java.nio.file.InvalidPathException ignored) {
                // wie leer behandeln
            }
        }
        return AppPaths.appDirectory().resolve(AppConfigLoader.DEFAULT_LOCAL_MODEL_DIRECTORY);
    }


    /** Die zuletzt bekannten Modelle mit einer Meldung, warum diesmal nichts abgefragt wurde. */
    private ModelCatalogSnapshot failed(String message) {
        ModelCatalogSnapshot cached = cachedModels();
        return new ModelCatalogSnapshot(cached.models(), java.util.Collections.singletonList(
                new CatalogStatus(ModelsConfig.defaultCatalogId(), "Modellquellen", false, message)));
    }

    @Override
    public ModelReference parseModel(String text) {
        return ModelsConfig.parse(text);
    }

    /** Modelle der Enterprise-API ohne Präfix (wie bisher {@code chat.model}), lokale mit {@code local:}. */
    @Override
    public String storedModel(ModelReference reference) {
        return ModelsConfig.defaultCatalogId().equals(reference.catalogId()) ? reference.modelId() : reference.key();
    }

    @Override
    public void resolveProxy(SettingsForm form, NetworkLogListener listener) {
        runNetworkProbe(form, listener, new BiFunction<NetworkConfig, Consumer<String>, Boolean>() {
            @Override
            public Boolean apply(NetworkConfig config, Consumer<String> out) {
                return NetworkProbe.resolve(config, out);
            }
        });
    }

    @Override
    public void checkHttps(SettingsForm form, NetworkLogListener listener) {
        runNetworkProbe(form, listener, new BiFunction<NetworkConfig, Consumer<String>, Boolean>() {
            @Override
            public Boolean apply(NetworkConfig config, Consumer<String> out) {
                return NetworkProbe.checkHttps(config, out);
            }
        });
    }

    @Override
    public String defaultDiscoveryScript(String proxyMode) {
        return HttpRoutes.discoveryScriptFor(proxyMode);
    }

    /** Netzwerkabschnitt des Entwurfs laden, Probe auf dem Arbeits-Executor, Zeilen und Ende über den UI-Executor. */
    private void runNetworkProbe(SettingsForm form, final NetworkLogListener listener,
                                 final BiFunction<NetworkConfig, Consumer<String>, Boolean> probe) {
        if (form == null || listener == null) {
            throw new IllegalArgumentException("form and listener must not be null");
        }
        final NetworkConfig config;
        try {
            config = AppConfigLoader.networkSection(SettingsMapper.merge(current(), form));
        } catch (AppConfigException e) {
            listener.line("ERROR: " + join(SettingsMapper.describe(e.problems())));
            listener.finished(false);
            return;
        } catch (IOException e) {
            listener.line("ERROR: Konfigurationsdatei nicht lesbar (" + e.getClass().getSimpleName() + ")");
            listener.finished(false);
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    boolean success;
                    try {
                        success = probe.apply(config, new Consumer<String>() {
                            @Override
                            public void accept(final String line) {
                                ui.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        listener.line(line);
                                    }
                                });
                            }
                        });
                    } catch (RuntimeException e) {
                        final String aborted = "ERROR: " + e.getClass().getSimpleName();
                        ui.execute(new Runnable() {
                            @Override
                            public void run() {
                                listener.line(aborted);
                            }
                        });
                        success = false;
                    }
                    final boolean delivered = success;
                    ui.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.finished(delivered);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            listener.line("ERROR: konnte nicht gestartet werden");
            listener.finished(false);
        }
    }

    private static void finish(ConnectionCheckListener listener, ConnectionCheckStep step) {
        listener.onStep(step);
        listener.onFinished(false);
    }

    private static String join(List<String> parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(part);
        }
        return text.toString();
    }
}
