package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.ui.settings.IndexActions;
import com.aresstack.enterpriseai.app.ui.settings.IndexForm;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * {@link IndexActions} über der Konfigurationsdatei, derselbe Weg wie der Einstellungen-Dialog: Prüfen heißt, die
 * Datei mit dem Entwurf zu verschmelzen und durch den {@link AppConfigLoader} zu schicken (Meldungen mit
 * Feldnamen); geschrieben werden nur {@code knowledge.indexDirectory} und {@code knowledge.indexOnStartup}, ein
 * geleertes Verzeichnis wird auskommentiert, alle übrigen Schlüssel, Kommentare und die Reihenfolge der Datei
 * bleiben ({@link ConfigurationFile#update}). Die Anwendung liest die Werte erst beim nächsten Start.
 */
public final class FileIndexActions implements IndexActions {

    private final ConfigurationFile file;

    public FileIndexActions(ConfigurationFile file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    public ConfigurationFile file() {
        return file;
    }

    @Override
    public IndexForm current() throws IOException {
        SettingsForm form = SettingsMapper.fromProperties(properties());
        return new IndexForm(form.indexDirectory(), form.indexOnStartup());
    }

    @Override
    public List<String> validate(IndexForm draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        Properties current;
        try {
            current = properties();
        } catch (IOException e) {
            return Collections.singletonList("Konfigurationsdatei nicht lesbar: " + file.path() + " ("
                    + e.getClass().getSimpleName() + ")");
        }
        try {
            AppConfigLoader.fromProperties(SettingsMapper.merge(current, with(current, draft)));
            return Collections.emptyList();
        } catch (AppConfigException e) {
            return SettingsMapper.describe(e.problems());
        } catch (RuntimeException e) {
            return Collections.singletonList("Konfiguration ungültig: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void save(IndexForm draft) throws IOException {
        List<String> problems = validate(draft);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Entwurf hat Probleme: " + problems);
        }
        Properties current = properties();
        SettingsForm form = with(current, draft);
        file.update(indexKeys(SettingsMapper.writes(form)), indexKeys(SettingsMapper.removals(form, current)),
                AppConfigLoader.exampleConfiguration());
    }

    /** Die beiden Schlüssel des Index-Dialogs; der Einstellungen-Dialog schreibt sie nicht mehr. */
    static boolean isIndexKey(String key) {
        return SettingsMapper.KEY_INDEX_DIRECTORY.equals(key) || SettingsMapper.KEY_INDEX_ON_STARTUP.equals(key);
    }

    // ------------------------------------------------------------------ Helfer

    /** Die Datei als Properties; fehlt sie, zählt die Vorlage (daraus schreibt {@link ConfigurationFile#update}). */
    private Properties properties() throws IOException {
        if (file.exists()) {
            return file.read();
        }
        Properties template = new Properties();
        template.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        return template;
    }

    /** Das Formular der Datei mit dem Entwurf an der Stelle der Index-Felder. */
    private static SettingsForm with(Properties current, IndexForm draft) {
        return SettingsMapper.fromProperties(current).toBuilder()
                .indexDirectory(draft.indexDirectory()).indexOnStartup(draft.indexOnStartup()).build();
    }

    private static Map<String, String> indexKeys(Map<String, String> writes) {
        Map<String, String> filtered = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : writes.entrySet()) {
            if (isIndexKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static Set<String> indexKeys(Collection<String> keys) {
        Set<String> filtered = new LinkedHashSet<String>();
        for (String key : keys) {
            if (isIndexKey(key)) {
                filtered.add(key);
            }
        }
        return filtered;
    }
}
