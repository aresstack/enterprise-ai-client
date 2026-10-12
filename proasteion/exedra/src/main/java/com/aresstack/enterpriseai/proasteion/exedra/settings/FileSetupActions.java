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
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.settings.FileSourceActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.CertificatePicker;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialog;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.ConfluenceSetup;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.DraftSettingsActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupCheck;
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
    /** Name der Quelle unter Wissensquellen. */
    static final String SOURCE_TITLE = "Confluence";

    private final FileSettingsActions settings;
    private final FileSourceActions sources;
    private final SecretChecker secrets;
    private final ConnectionChecker connection;
    private final ConfigurationCheck check;
    private final Executor worker;
    private final Executor ui;
    private final ComicPalette palette;

    public FileSetupActions(FileSettingsActions settings, SecretChecker secrets, ConnectionChecker connection,
                            ConfigurationCheck check, Executor worker, Executor ui, ComicPalette palette) {
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
            probe.chatModel("-");
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
        String loadProblem = null;
        try {
            settings.save(form);
        } catch (IllegalArgumentException e) {
            loadProblem = "Angaben unvollständig: " + join(SettingsMapper.plain(settings.problems(form)));
        } catch (IOException e) {
            loadProblem = "Konfigurationsdatei nicht schreibbar (" + e.getClass().getSimpleName() + ").";
        }
        SourceDefinition source = confluence == null ? null : ConfluenceEntryCheck.definition(SOURCE_TITLE,
                confluence.baseUrl(), confluence.entryTitle(), confluence.certificateAlias());
        String sourceProblem = null;
        if (loadProblem == null && source != null) {
            try {
                sources.save(source, null);
            } catch (IOException | RuntimeException e) {
                sourceProblem = "Quelle nicht schreibbar (" + e.getClass().getSimpleName() + ").";
            }
        }
        AppConfig config = null;
        Properties stored = null;
        if (loadProblem == null) {
            try {
                stored = file().read();
                config = AppConfigLoader.load(file().path());
                check.verify(config);
            } catch (AppConfigException e) {
                config = null;
                loadProblem = join(SettingsMapper.plain(e.problems()));
            } catch (IOException e) {
                config = null;
                loadProblem = "Konfigurationsdatei nicht lesbar (" + e.getClass().getSimpleName() + ").";
            }
        }

        // Ohne eingetragene Basis-URL kommt sie aus dem KeePass-Eintrag (VaultLocations); erst damit gibt es
        // Chat- und Embedding-Abschnitt.
        AppConfig effective = null;
        String locateProblem = loadProblem;
        if (config != null) {
            try {
                effective = located(config, stored);
            } catch (AppConfigException e) {
                locateProblem = join(SettingsMapper.plain(e.problems()));
            } catch (RuntimeException e) {
                locateProblem = message(e);
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
        // 1. KeePass gekoppelt (Einträge lesbar)
        KeePassAccess vault = keePass == null ? KeePassAccess.unavailable() : secrets.vault(keePass);
        try {
            int count = vault.titles().size();
            checks.add(SetupCheck.ok(CHECK_KEEPASS, count + " Einträge lesbar"));
        } catch (KeePassAccess.Failure | RuntimeException e) {
            checks.add(SetupCheck.failed(CHECK_KEEPASS, message(e)));
        }
        // 2. KI-Dienst erreichbar
        if (effective == null || effective.chat() == null) {
            checks.add(SetupCheck.failed(CHECK_SERVICE, "Konfiguration unvollständig: " + locateProblem));
        } else {
            final List<String> failures = new ArrayList<String>();
            boolean reachable;
            try {
                reachable = connection.check(effective, step -> {
                    if (step.isFailure()) {
                        failures.add(step.title() + ": " + step.detail());
                    }
                });
            } catch (RuntimeException e) {
                reachable = false;
                failures.add(message(e));
            }
            checks.add(reachable && failures.isEmpty() ? SetupCheck.ok(CHECK_SERVICE, "")
                    : SetupCheck.failed(CHECK_SERVICE, failures.isEmpty() ? "Verbindungstest fehlgeschlagen"
                    : join(failures)));
        }
        // 3. API-Key lesbar
        SecretRef apiKey = effective == null || effective.chat() == null ? null : effective.chat().apiKeyRef();
        if (keePass == null || apiKey == null) {
            checks.add(SetupCheck.failed(CHECK_API_KEY, "kein KeePass-Eintrag für den KI-Dienst"));
        } else {
            SecretCheckResult result;
            try {
                result = secrets.check(keePass, apiKey);
            } catch (RuntimeException e) {
                result = SecretCheckResult.failed(message(e));
            }
            checks.add(result.isSuccess() ? SetupCheck.ok(CHECK_API_KEY, "")
                    : SetupCheck.failed(CHECK_API_KEY, result.message()));
        }
        // 4.–6. Modelle und Dimension
        String chatModel = effective == null || effective.chat() == null ? null : effective.chat().model();
        checks.add(!blank(chatModel) && !"-".equals(chatModel) ? SetupCheck.ok(CHECK_CHAT_MODEL, chatModel)
                : SetupCheck.failed(CHECK_CHAT_MODEL, "kein Chat-Modell gewählt"));
        String embeddingModel = effective == null || effective.embedding() == null ? null
                : effective.embedding().model();
        checks.add(!blank(embeddingModel) && !"-".equals(embeddingModel)
                ? SetupCheck.ok(CHECK_EMBEDDING_MODEL, embeddingModel)
                : SetupCheck.failed(CHECK_EMBEDDING_MODEL, "kein Embedding-Modell gewählt"));
        checks.add(effective != null && effective.embedding() != null && effective.embedding().dimension() > 0
                ? SetupCheck.ok(CHECK_DIMENSION, Integer.toString(effective.embedding().dimension()))
                : SetupCheck.failed(CHECK_DIMENSION, "unbekannt"));
        if (confluence != null) {
            // 7. Confluence-Zugang (Spaces abrufbar) mit den Zugangsdaten aus KeePass
            checks.add(confluenceCheck(vault, network, confluence));
            // 8. Quelle gespeichert und aktiviert
            checks.add(sourceCheck(source, sourceProblem == null ? loadProblem : sourceProblem));
        }
        // 9. Konfiguration lädt vollständig
        checks.add(config != null ? SetupCheck.ok(CHECK_LOADS, "")
                : SetupCheck.failed(CHECK_LOADS, loadProblem == null ? "unbekannter Fehler" : loadProblem));
        for (SetupCheck item : checks) {
            LOG.info("Einrichtung: " + item);
        }
        return checks;
    }

    private SetupCheck confluenceCheck(KeePassAccess vault, NetworkConfig network, ConfluenceSetup confluence) {
        if (network == null) {
            return SetupCheck.failed(CHECK_CONFLUENCE, "Netzwerkeinstellungen ungültig");
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

    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
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
