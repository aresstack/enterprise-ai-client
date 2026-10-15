package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.proasteion.application.composition.ConfluenceEntryCheck;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.config.VaultLocations;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.application.security.ManualCredentials;
import com.aresstack.enterpriseai.proasteion.application.security.ProtectedSecretStore;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.settings.FileSourceActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.CertificatePicker;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialog;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.ConfluenceSetup;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.DraftSettingsActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupCheck;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupVoices;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Seite des Einrichtungsassistenten über der Konfigurationsdatei, ohne eigene Schreib- oder Prüfwege: die
 * Konfiguration schreibt {@link FileSettingsActions#save} (nur verwaltete Schlüssel, nie der API-Key), die Quelle
 * {@link FileSourceActions}; geprüft wird mit derselben KeePass-Probe ({@link SecretChecker}), demselben
 * Verbindungstest ({@link ConnectionChecker}), {@code ConfluenceEntryCheck} und dem {@link AppConfigLoader} wie im
 * Einstellungen-Dialog und beim Start. Blockierendes läuft auf dem Arbeits-Executor, Ergebnisse über den UI-Executor.
 */
public final class FileSetupActions implements SetupActions {

    private static final Logger LOG = Logger.getLogger(FileSetupActions.class.getName());

    static final String CHECK_KEEPASS = "KeePass ist gekoppelt";
    static final String CHECK_SERVICE = "KI-Dienst ist erreichbar";
    static final String CHECK_API_KEY = "API-Key kann gelesen werden";
    static final String CHECK_CHAT_MODEL = "Chat-Modell ist gesetzt";
    static final String CHECK_EMBEDDING_MODEL = "Embedding-Modell ist gesetzt";
    static final String CHECK_DIMENSION = "Embedding-Dimension ist bekannt";
    static final String CHECK_CONFLUENCE = "Confluence-Zugang funktioniert";
    static final String CHECK_SOURCE = "Confluence-Quelle ist gespeichert und aktiviert";
    static final String CHECK_LOADS = "Konfiguration lässt sich vollständig laden";
    /** Detail of the source point while the basic settings are incomplete and nothing was written. */
    static final String SOURCE_PENDING = "Noch nicht gespeichert, weil die Grundeinstellungen unvollständig sind.";
    /** Title of the API-key step of the connection probe. */
    static final String PROBE_KEY = "API-Key";
    /** Stands in for a model not chosen yet, like the connection test of step 2. */
    static final String MODEL_PLACEHOLDER = "-";
    /** Name der Quelle unter Wissensquellen. */
    static final String SOURCE_TITLE = "Confluence";
    /** KeePass point when every chosen access is manual. */
    static final String KEEPASS_NOT_NEEDED = "nicht benötigt (alle Zugänge manuell)";

    private final FileSettingsActions settings;
    private final FileSourceActions sources;
    private final SecretChecker secrets;
    private final ConnectionChecker connection;
    private final ConfigurationCheck check;
    private final Executor worker;
    private final Executor ui;
    private final ComicPalette palette;
    /** Zugang „Manuell“: die Sitzung und die geschützte Ablage („API-Key speichern“). */
    private final ManualCredentials manual;

    public FileSetupActions(FileSettingsActions settings, SecretChecker secrets, ConnectionChecker connection,
                            ConfigurationCheck check, Executor worker, Executor ui, ComicPalette palette) {
        this(settings, secrets, connection, check, worker, ui, palette, new ManualCredentials());
    }

    /** Wie oben mit der Sitzung der manuellen Zugangsdaten, die die Proben {@code secrets}/{@code connection} teilen. */
    public FileSetupActions(FileSettingsActions settings, SecretChecker secrets, ConnectionChecker connection,
                            ConfigurationCheck check, Executor worker, Executor ui, ComicPalette palette,
                            ManualCredentials manual) {
        if (manual == null) {
            throw new IllegalArgumentException("manual must not be null");
        }
        this.manual = manual;
        if (settings == null || secrets == null || connection == null || check == null || worker == null
                || ui == null) {
            throw new IllegalArgumentException("settings, secrets, connection, check, worker and ui must not be null");
        }
        this.settings = settings;
        this.sources = new FileSourceActions(settings.file());
        this.secrets = secrets;
        this.connection = connection;
        this.check = check;
        this.worker = worker;
        this.ui = ui;
        this.palette = palette == null ? ComicPalette.defaultPalette() : palette;
    }

    private ConfigurationFile file() {
        return settings.file();
    }

    // ------------------------------------------------------------------ Embedding-Test

    @Override
    public void embeddingDimension(SettingsForm form, final BiConsumer<Integer, String> done) {
        if (form == null || done == null) {
            throw new IllegalArgumentException("form and done must not be null");
        }
        SettingsForm.Builder probe = form.toBuilder().embeddingDimension("1");
        if (form.chatModel().isEmpty()) {
            probe.chatModel(MODEL_PLACEHOLDER);
        }
        final AppConfig config;
        final Properties merged;
        try {
            merged = SettingsMapper.merge(current(), probe.build());
            config = AppConfigLoader.fromProperties(merged);
        } catch (AppConfigException e) {
            done.accept(null, "Angaben unvollständig: " + join(SettingsMapper.plain(e.problems())));
            return;
        } catch (IOException | RuntimeException e) {
            done.accept(null, "Angaben nicht lesbar (" + e.getClass().getSimpleName() + ").");
            return;
        }
        submit(() -> {
            Integer dimension = null;
            String error = null;
            try {
                dimension = connection.embeddingDimension(located(config, merged));
            } catch (Exception e) {
                LOG.log(Level.INFO, "Embedding-Test fehlgeschlagen: " + e);
                error = message(e);
            }
            final Integer measured = dimension;
            final String failure = error;
            ui.execute(() -> done.accept(measured, failure));
        }, () -> done.accept(null, "Der Embedding-Test konnte nicht gestartet werden."));
    }

    // ------------------------------------------------------------------ Merlin

    @Override
    public void ask(SettingsForm form, final String system, final String question,
                    final BiConsumer<String, String> done) {
        if (form == null || system == null || question == null || done == null) {
            throw new IllegalArgumentException("form, system, question and done must not be null");
        }
        if (form.chatModel().isEmpty()) {
            done.accept(null, "Noch kein Chat-Modell gewählt.");
            return;
        }
        SettingsForm.Builder probe = form.toBuilder();
        // Für die Frage ohne Belang, die Konfiguration verlangt aber Werte (wie der Embedding-Test beim Chat-Modell).
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
            done.accept(null, "Angaben unvollständig: " + join(SettingsMapper.plain(e.problems())));
            return;
        } catch (IOException | RuntimeException e) {
            done.accept(null, "Angaben nicht lesbar (" + e.getClass().getSimpleName() + ").");
            return;
        }
        submit(() -> {
            String answer = null;
            String error = null;
            try {
                answer = connection.ask(located(config, merged), system, question);
            } catch (Exception e) {
                LOG.log(Level.INFO, "Frage an Merlin fehlgeschlagen: " + e.getClass().getSimpleName());
                error = message(e);
            }
            final String delivered = answer;
            final String failure = error;
            ui.execute(() -> done.accept(delivered, failure));
        }, () -> done.accept(null, "Die Frage konnte nicht gestellt werden."));
    }

    @Override
    public void merlinVoice(SettingsForm form, final Consumer<SetupVoices> done) {
        if (form == null || done == null) {
            throw new IllegalArgumentException("form and done must not be null");
        }
        // Merlins Stimme braucht nur Dienst, Key und Netz; fehlende Modelle füllen Platzhalter (wie bei der Frage).
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
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.INFO, "Merlins Stimme nicht gebunden: " + e.getClass().getSimpleName());
            done.accept(SetupVoices.silent(null));
            return;
        }
        submit(() -> {
            SetupVoices voice;
            try {
                voice = connection.merlinVoice(located(config, merged));
            } catch (RuntimeException e) {
                LOG.log(Level.INFO, "Merlins Stimme nicht gebunden: " + e.getClass().getSimpleName());
                voice = SetupVoices.silent(null);
            }
            final SetupVoices bound = voice;
            ui.execute(() -> done.accept(bound));
        }, () -> done.accept(SetupVoices.silent(null)));
    }

    @Override
    public void voices(SettingsForm form, final Consumer<SetupVoices> done) {
        if (form == null || done == null) {
            throw new IllegalArgumentException("form and done must not be null");
        }
        final AppConfig config;
        final Properties merged;
        try {
            merged = SettingsMapper.merge(current(), form);
            config = AppConfigLoader.fromProperties(merged);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.INFO, "Stimmen der Übergabe nicht gebunden: " + e.getClass().getSimpleName());
            done.accept(SetupVoices.silent(null));
            return;
        }
        submit(() -> {
            SetupVoices voices;
            try {
                voices = connection.voices(located(config, merged));
            } catch (RuntimeException e) {
                LOG.log(Level.INFO, "Stimmen der Übergabe nicht gebunden: " + e.getClass().getSimpleName());
                voices = SetupVoices.silent(null);
            }
            final SetupVoices bound = voices;
            ui.execute(() -> done.accept(bound));
        }, () -> done.accept(SetupVoices.silent(null)));
    }

    // ------------------------------------------------------------------ Abschluss

    @Override
    public void finish(final SettingsForm form, final ConfluenceSetup confluence,
                       final Consumer<List<SetupCheck>> done) {
        if (form == null || done == null) {
            throw new IllegalArgumentException("form and done must not be null");
        }
        submit(() -> {
            List<SetupCheck> checks;
            try {
                checks = verify(form, confluence);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Abschlussprüfung der Einrichtung abgebrochen", e);
                checks = Collections.singletonList(SetupCheck.failed("Abschlussprüfung",
                        "abgebrochen (" + e.getClass().getSimpleName() + ")"));
            }
            final List<SetupCheck> delivered = checks;
            ui.execute(() -> done.accept(delivered));
        }, () -> done.accept(Collections.singletonList(SetupCheck.failed("Abschlussprüfung",
                "konnte nicht gestartet werden"))));
    }

    /**
     * Schreibt Konfiguration und Confluence-Quelle und prüft danach die neun Punkte der Abschlussprüfung; blockiert
     * (KeePass, Netz), nie auf dem EDT. Ohne Confluence ({@code null}: im Assistenten übersprungen, optional) entfallen
     * Quelle und die beiden Confluence-Punkte.
     */
    List<SetupCheck> verify(SettingsForm form, ConfluenceSetup confluence) {
        // Keep the points independent: a missing model fails only its own point, never KeePass, service or API key.
        String saveProblem = null;
        boolean incomplete = false;
        try {
            settings.save(form);
        } catch (IllegalArgumentException e) {
            incomplete = true;
            saveProblem = "Angaben unvollständig: " + join(SettingsMapper.plain(settings.problems(form)));
        } catch (IOException e) {
            saveProblem = "Konfigurationsdatei nicht schreibbar (" + e.getClass().getSimpleName() + ").";
        }
        SourceDefinition source = confluence == null ? null : ConfluenceEntryCheck.definition(SOURCE_TITLE,
                confluence.baseUrl(), confluence.credentialRef(), confluence.certificateAlias(),
                confluence.rememberedUser());
        String sourceProblem = null;
        if (saveProblem != null) {
            sourceProblem = incomplete ? SOURCE_PENDING : saveProblem;
        } else if (source != null) {
            try {
                sources.save(source, null);
            } catch (IOException | RuntimeException e) {
                sourceProblem = "Quelle nicht schreibbar (" + e.getClass().getSimpleName() + ").";
            }
        }
        AppConfig config = null;
        String loadProblem = saveProblem;
        if (saveProblem == null) {
            try {
                config = AppConfigLoader.load(file().path());
                check.verify(config);
            } catch (AppConfigException e) {
                config = null;
                loadProblem = join(SettingsMapper.plain(e.problems()));
            }
        }

        KeePassConfig keePass;
        NetworkConfig network;
        try {
            Properties merged = SettingsMapper.merge(current(), form);
            keePass = config != null ? config.keePass() : AppConfigLoader.keePassSection(merged);
            network = config != null ? config.network() : AppConfigLoader.networkSection(merged);
        } catch (IOException | RuntimeException e) {
            keePass = null;
            network = null;
        }

        List<SetupCheck> checks = new ArrayList<SetupCheck>();
        // 1. KeePass gekoppelt (Einträge lesbar), nur wenn ein gewählter Zugang KeePass braucht
        boolean manualService = ManualCredentials.isManual(form.chatApiKeyRef());
        boolean needsKeePass = !manualService || (confluence != null && !confluence.isManual());
        KeePassAccess vault = keePass == null ? KeePassAccess.unavailable() : secrets.vault(keePass);
        if (!needsKeePass) {
            checks.add(SetupCheck.ok(CHECK_KEEPASS, KEEPASS_NOT_NEEDED));
        } else {
            try {
                int count = vault.titles().size();
                checks.add(SetupCheck.ok(CHECK_KEEPASS, count + " Einträge lesbar"));
            } catch (KeePassAccess.Failure | RuntimeException e) {
                checks.add(SetupCheck.failed(CHECK_KEEPASS, message(e)));
            }
        }
        // 2. KI-Dienst erreichbar: the same probe as step 2, with placeholders for models not chosen yet
        checks.add(serviceCheck(form));
        // 3. API-Key lesbar: the reference the wizard chose, independent of a complete chat section
        checks.add(apiKeyCheck(form, keePass));
        // 4.–6. Modelle und Dimension, as entered in the wizard
        String chatModel = form.chatModel().trim();
        checks.add(!chatModel.isEmpty() && !MODEL_PLACEHOLDER.equals(chatModel)
                ? SetupCheck.ok(CHECK_CHAT_MODEL, chatModel)
                : SetupCheck.failed(CHECK_CHAT_MODEL, "kein Chat-Modell gewählt"));
        String embeddingModel = form.embeddingModel().trim();
        checks.add(!embeddingModel.isEmpty() && !MODEL_PLACEHOLDER.equals(embeddingModel)
                ? SetupCheck.ok(CHECK_EMBEDDING_MODEL, embeddingModel)
                : SetupCheck.failed(CHECK_EMBEDDING_MODEL, "kein Embedding-Modell gewählt"));
        int dimension = positiveOrZero(form.embeddingDimension());
        checks.add(dimension > 0 ? SetupCheck.ok(CHECK_DIMENSION, Integer.toString(dimension))
                : SetupCheck.failed(CHECK_DIMENSION, "unbekannt"));
        if (confluence != null) {
            // 7. Confluence-Zugang (Spaces abrufbar) mit den Zugangsdaten aus KeePass
            checks.add(confluenceCheck(vault, network, confluence));
            // 8. Quelle gespeichert und aktiviert
            checks.add(sourceCheck(source, sourceProblem));
        }
        // 9. Konfiguration lädt vollständig
        checks.add(config != null ? SetupCheck.ok(CHECK_LOADS, "")
                : SetupCheck.failed(CHECK_LOADS, loadProblem == null ? "unbekannter Fehler" : loadProblem));
        for (SetupCheck item : checks) {
            LOG.info("Einrichtung: " + item);
        }
        return checks;
    }

    /** Runs the connection probe of step 2 on the draft; missing models become placeholders. */
    private SetupCheck serviceCheck(SettingsForm form) {
        AppConfig probe;
        try {
            Properties merged = SettingsMapper.merge(current(), probeForm(form));
            probe = located(AppConfigLoader.fromProperties(merged), merged);
        } catch (AppConfigException e) {
            return SetupCheck.failed(CHECK_SERVICE, "Angaben unvollständig: "
                    + join(SettingsMapper.plain(e.problems())));
        } catch (IOException | RuntimeException e) {
            return SetupCheck.failed(CHECK_SERVICE, message(e));
        }
        final List<String> failures = new ArrayList<String>();
        boolean reachable;
        try {
            reachable = connection.check(probe, step -> {
                if (step.isFailure() || (PROBE_KEY.equals(step.title())
                        && step.status() != ConnectionCheckStep.Status.OK)) {
                    failures.add(step.title() + ": " + step.detail());
                }
            });
        } catch (RuntimeException e) {
            reachable = false;
            failures.add(message(e));
        }
        return reachable && failures.isEmpty() ? SetupCheck.ok(CHECK_SERVICE, "")
                : SetupCheck.failed(CHECK_SERVICE, failures.isEmpty() ? "Verbindungstest fehlgeschlagen"
                : join(failures));
    }

    /**
     * Reads the API key through the KeePass section and the reference of the draft. A manual reference resolves
     * through the same path as the application (session, protected store, then the dialog).
     */
    private SetupCheck apiKeyCheck(SettingsForm form, KeePassConfig keePass) {
        String ref = form.chatApiKeyRef() == null ? "" : form.chatApiKeyRef().trim();
        if (ref.isEmpty()) {
            return SetupCheck.failed(CHECK_API_KEY, "kein KeePass-Eintrag für den KI-Dienst gewählt");
        }
        if (ManualCredentials.isManual(ref) && !manual.knows(ref) && !manual.persistent().contains(ref)) {
            return SetupCheck.failed(CHECK_API_KEY, "manueller API-Key in dieser Sitzung nicht eingegeben");
        }
        if (keePass == null) {
            return SetupCheck.failed(CHECK_API_KEY, "KeePass-Einstellungen ungültig");
        }
        SecretCheckResult result;
        try {
            result = secrets.check(keePass, SecretRef.of(ref));
        } catch (RuntimeException e) {
            result = SecretCheckResult.failed(message(e));
        }
        if (ManualCredentials.isManual(ref)) {
            return result.isSuccess() ? SetupCheck.ok(CHECK_API_KEY, manual.knows(ref)
                    ? "manuell eingegeben" : "manuell, geschützt gespeichert")
                    : SetupCheck.failed(CHECK_API_KEY, result.message());
        }
        return result.isSuccess() ? SetupCheck.ok(CHECK_API_KEY, "")
                : SetupCheck.failed(CHECK_API_KEY, result.message());
    }

    /** Same placeholders as the connection test of step 2. */
    static SettingsForm probeForm(SettingsForm form) {
        SettingsForm.Builder probe = form.toBuilder();
        if (form.chatModel().trim().isEmpty()) {
            probe.chatModel(MODEL_PLACEHOLDER);
        }
        if (form.embeddingModel().trim().isEmpty()) {
            probe.embeddingModel(MODEL_PLACEHOLDER);
        }
        if (positiveOrZero(form.embeddingDimension()) <= 0) {
            probe.embeddingDimension("1");
        }
        return probe.build();
    }

    private static int positiveOrZero(String text) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return Math.max(0, value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private SetupCheck confluenceCheck(KeePassAccess vault, NetworkConfig network, ConfluenceSetup confluence) {
        if (network == null) {
            return SetupCheck.failed(CHECK_CONFLUENCE, "Netzwerkeinstellungen ungültig");
        }
        if (confluence.isManual()) {
            return manualConfluenceCheck(network, confluence);
        }
        char[] password = null;
        try {
            KeePassAccess.Entry entry = vault.read(confluence.entryTitle());
            password = entry.password();
            if (password == null || password.length == 0) {
                return SetupCheck.failed(CHECK_CONFLUENCE, "im KeePass-Eintrag fehlt Passwort bzw. Token");
            }
            String message = secrets.testConfluence(network, confluence.baseUrl(), entry.userName(), password,
                    confluence.certificateAlias());
            return SetupCheck.ok(CHECK_CONFLUENCE, message);
        } catch (Exception e) {
            return SetupCheck.failed(CHECK_CONFLUENCE, message(e));
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /** Confluence „Manuell“: mit dem in dieser Sitzung eingegebenen Passwort bzw. Token, ohne KeePass. */
    private SetupCheck manualConfluenceCheck(NetworkConfig network, ConfluenceSetup confluence) {
        String ref = confluence.credentialRef();
        char[] password = manual.secret(ref);
        if (password == null || password.length == 0) {
            return SetupCheck.failed(CHECK_CONFLUENCE, "Passwort bzw. Token in dieser Sitzung nicht eingegeben");
        }
        try {
            return SetupCheck.ok(CHECK_CONFLUENCE, secrets.testConfluence(network, confluence.baseUrl(),
                    manual.user(ref), password, confluence.certificateAlias()));
        } catch (Exception e) {
            return SetupCheck.failed(CHECK_CONFLUENCE, message(e));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private SetupCheck sourceCheck(SourceDefinition expected, String problem) {
        if (problem != null) {
            return SetupCheck.failed(CHECK_SOURCE, problem);
        }
        try {
            for (SourceDefinition stored : sources.definitions()) {
                if (stored.address().equals(expected.address()) && stored.typeId().equals(expected.typeId())) {
                    return stored.enabled() ? SetupCheck.ok(CHECK_SOURCE, stored.address())
                            : SetupCheck.failed(CHECK_SOURCE, "gespeichert, aber ausgeschaltet");
                }
            }
            return SetupCheck.failed(CHECK_SOURCE, "nicht in der Konfiguration gefunden");
        } catch (IOException | RuntimeException e) {
            return SetupCheck.failed(CHECK_SOURCE, message(e));
        }
    }

    // ------------------------------------------------------------------ Zugang „Manuell“

    @Override
    public void testConfluence(SettingsForm form, final String url, final String user, char[] password,
                               final String certificateAlias, final BiConsumer<String, String> done) {
        if (form == null || done == null) {
            throw new IllegalArgumentException("form and done must not be null");
        }
        final NetworkConfig network;
        try {
            network = AppConfigLoader.networkSection(SettingsMapper.merge(current(), form));
        } catch (IOException | RuntimeException e) {
            done.accept(null, "Netzwerkeinstellungen ungültig (" + e.getClass().getSimpleName() + ").");
            return;
        }
        final char[] copy = password == null ? new char[0] : Arrays.copyOf(password, password.length);
        submit(() -> {
            String message = null;
            String error = null;
            try {
                message = secrets.testConfluence(network, url, user, copy, certificateAlias);
            } catch (Exception e) {
                LOG.log(Level.INFO, "Confluence-Test (manuell) fehlgeschlagen: " + e.getClass().getSimpleName());
                error = message(e);
            } finally {
                Arrays.fill(copy, '\0');
            }
            final String delivered = message;
            final String failure = error;
            ui.execute(() -> done.accept(delivered, failure));
        }, () -> {
            Arrays.fill(copy, '\0');
            done.accept(null, "Der Confluence-Test konnte nicht gestartet werden.");
        });
    }

    @Override
    public void rememberManual(String ref, String user, char[] secret) {
        manual.remember(ref, user, secret);
    }

    @Override
    public void forgetManual(String ref) {
        if (ManualCredentials.isManual(ref)) {
            manual.forget(ref);
        }
    }

    @Override
    public boolean knowsManual(String ref) {
        return manual.knows(ref);
    }

    @Override
    public boolean canStoreManualKey() {
        return manual.persistent().available();
    }

    @Override
    public String manualKeyStorage() {
        return manual.persistent().describe();
    }

    @Override
    public boolean manualKeyStored(String ref) {
        return ManualCredentials.isManual(ref) && manual.persistent().contains(ref);
    }

    @Override
    public void storeManualKey(final String ref, final boolean keep, final Consumer<String> done) {
        if (done == null) {
            throw new IllegalArgumentException("done must not be null");
        }
        if (!ManualCredentials.isManual(ref)) {
            done.accept("keine manuelle Referenz");
            return;
        }
        final ProtectedSecretStore store = manual.persistent();
        submit(() -> {
            String error = null;
            char[] secret = keep ? manual.secret(ref) : null;
            try {
                if (!keep) {
                    store.remove(ref);
                } else if (!store.available()) {
                    error = store.describe();
                } else if (secret == null) {
                    error = "Kein API-Key in dieser Sitzung eingegeben.";
                } else {
                    store.store(ref, secret);
                }
            } catch (Exception e) {
                LOG.log(Level.INFO, "API-Key nicht gespeichert: " + e.getClass().getSimpleName());
                error = "Der API-Key ließ sich nicht geschützt speichern (" + e.getClass().getSimpleName() + ").";
            } finally {
                if (secret != null) {
                    Arrays.fill(secret, '\0');
                }
            }
            final String failure = error;
            ui.execute(() -> done.accept(failure));
        }, () -> done.accept("Das Speichern konnte nicht gestartet werden."));
    }

    // ------------------------------------------------------------------ Dialoge

    @Override
    public SettingsForm editNetwork(Component parent, SettingsForm form) {
        DraftSettingsActions draft = new DraftSettingsActions(settings);
        SettingsForm edited = SettingsDialog.show(parent == null ? null : SwingUtilities.getWindowAncestor(parent),
                form, Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, draft, null, palette,
                SettingsPanel.tabIndexStartingWith("Netzwerk"), null);
        return edited == null ? null : draft.saved();
    }

    @Override
    public String chooseCertificate(Component parent, String current) {
        return CertificatePicker.choose(parent, current);
    }

    // ------------------------------------------------------------------ Hilfen

    /** Die Datei als Properties; fehlt sie (Erststart), die Vorlage, aus der {@link FileSettingsActions#save} schreibt. */
    private Properties current() throws IOException {
        if (file().exists()) {
            return file().read();
        }
        Properties template = new Properties();
        template.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        return template;
    }

    /** Zugang „KeePass“ ohne eingetragene URL: die Basis-URL aus dem KeePass-Eintrag ({@link VaultLocations}). */
    private AppConfig located(AppConfig config, Properties properties) {
        if (properties == null || VaultLocations.pendingChatRef(properties) == null) {
            return config;
        }
        final KeePassConfig keePass = config.keePass();
        return AppConfigLoader.fromProperties(VaultLocations.complete(properties,
                ref -> secrets.locationOf(keePass, ref)));
    }

    private void submit(Runnable work, Runnable rejected) {
        try {
            worker.execute(work);
        } catch (RuntimeException e) {
            rejected.run();
        }
    }

    private static String message(Throwable e) {
        return e.getMessage() == null || e.getMessage().trim().isEmpty() ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(part);
        }
        return sb.toString();
    }
}
