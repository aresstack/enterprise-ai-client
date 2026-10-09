package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.domain.security.SecretRef;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * {@link SettingsDialogActions} über der Konfigurationsdatei: Prüfen heißt, die Datei mit dem Entwurf zu
 * verschmelzen und durch den {@link AppConfigLoader} zu schicken (dieselben Regeln wie beim Start, Meldungen mit
 * Feldnamen); Speichern schreibt nur die verwalteten Schlüssel. Die KeePass-Probe läuft auf dem Arbeits-Executor,
 * ihr Ergebnis kommt über den UI-Executor zurück.
 */
public final class FileSettingsActions implements SettingsDialogActions {

    private final ConfigurationFile file;
    private final SecretChecker secretChecker;
    private final Executor worker;
    private final Executor ui;

    /**
     * @param secretChecker die KeePass-Probe oder {@code null}, wenn keine möglich ist (Prüfen meldet das)
     * @param worker        führt die Probe aus (nie der EDT)
     * @param ui            liefert das Ergebnis der Probe ab (produktiv {@code SwingUtilities::invokeLater})
     */
    public FileSettingsActions(ConfigurationFile file, SecretChecker secretChecker, Executor worker, Executor ui) {
        if (file == null || worker == null || ui == null) {
            throw new IllegalArgumentException("file, worker and ui must not be null");
        }
        this.file = file;
        this.secretChecker = secretChecker;
        this.worker = worker;
        this.ui = ui;
    }

    public ConfigurationFile file() {
        return file;
    }

    @Override
    public List<String> validate(SettingsForm form) {
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }
        Properties current;
        try {
            current = file.read();
        } catch (IOException e) {
            return Collections.singletonList("Konfigurationsdatei nicht lesbar: " + file.path() + " ("
                    + e.getClass().getSimpleName() + ")");
        }
        try {
            AppConfigLoader.fromProperties(SettingsMapper.merge(current, form));
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
        Properties current = file.read();
        file.update(SettingsMapper.changes(form), SettingsMapper.removals(form, current),
                AppConfigLoader.exampleConfiguration());
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
            keePass = AppConfigLoader.keePassSection(SettingsMapper.merge(file.read(), form));
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
}
