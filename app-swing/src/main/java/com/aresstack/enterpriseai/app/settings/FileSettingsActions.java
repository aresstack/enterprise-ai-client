package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.net.HttpRoutes;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.app.ui.settings.NetworkLogListener;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.app.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.application.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.domain.security.SecretRef;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
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
        try {
            config = AppConfigLoader.fromProperties(SettingsMapper.merge(current(), form));
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
                        success = connectionChecker.check(config, new Consumer<ConnectionCheckStep>() {
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
                        final ConnectionCheckStep aborted = ConnectionCheckStep.failed(STEP_TEST,
                                "Abgebrochen: " + e.getClass().getSimpleName());
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
        try {
            config = AppConfigLoader.fromProperties(SettingsMapper.merge(current(), probe.build()));
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
                        result = models.refresh(config);
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
        try {
            com.aresstack.winproxy.ProxyMode mode = com.aresstack.winproxy.ProxyMode.valueOf(proxyMode);
            if (mode != com.aresstack.winproxy.ProxyMode.PAC_URL_POWERSHELL
                    && mode != com.aresstack.winproxy.ProxyMode.PAC_URL_WSCRIPT) {
                return "";
            }
            return HttpRoutes.defaultDiscoveryScript(mode);
        } catch (RuntimeException e) {
            return "";
        }
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
