package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.app.ui.settings.SourceActions;
import com.aresstack.enterpriseai.app.ui.settings.SourceForm;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * {@link SourceActions} über der Konfigurationsdatei. Geschrieben werden nur {@code sources} und
 * {@code source.<id>.*}; alle übrigen Schlüssel, Kommentare und die Reihenfolge der Datei bleiben, wie sie sind
 * ({@link ConfigurationFile#update}). Geprüft wird ein Entwurf wie beim Start, aber streng nur für diese eine Quelle
 * ({@link AppConfigLoader#sourceSection}); entfernte Quellen werden auskommentiert, nicht gelöscht.
 */
public final class FileSourceActions implements SourceActions {

    static final String KEY_SOURCES = SettingsMapper.KEY_SOURCES;
    static final String SOURCE_PREFIX = SettingsMapper.SOURCE_PREFIX;

    private final ConfigurationFile file;

    public FileSourceActions(ConfigurationFile file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    public ConfigurationFile file() {
        return file;
    }

    @Override
    public List<SourceForm> sources() throws IOException {
        return SettingsMapper.fromProperties(current()).sources();
    }

    /**
     * Die gespeicherte Quelle als Konfiguration, damit die Anwendung sie ohne Neustart anbinden kann.
     *
     * @throws AppConfigException wenn die Quelle in der Datei fehlerhaft ist
     */
    public SourceConfig sourceConfig(String id) throws IOException {
        return AppConfigLoader.sourceSection(current(), id);
    }

    @Override
    public List<String> validate(SourceForm draft, String originalId) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        Properties current;
        try {
            current = current();
        } catch (IOException e) {
            return Collections.singletonList("Konfigurationsdatei nicht lesbar: " + file.path() + " ("
                    + e.getClass().getSimpleName() + ")");
        }
        String id = draft.id();
        if (id.isEmpty()) {
            return Collections.singletonList("ID: fehlt (Kurzname der Quelle)");
        }
        for (SourceForm other : SettingsMapper.fromProperties(current).sources()) {
            if (other.id().equals(id) && !id.equals(originalId)) {
                return Collections.singletonList("ID: eine Quelle „" + id + "“ gibt es schon");
            }
        }
        try {
            AppConfigLoader.sourceSection(SettingsMapper.merge(current, with(current, draft, originalId)), id);
            return Collections.emptyList();
        } catch (AppConfigException e) {
            return SettingsMapper.describe(e.problems());
        }
    }

    @Override
    public void save(SourceForm draft, String originalId) throws IOException {
        List<String> problems = validate(draft, originalId);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Entwurf hat Probleme: " + problems);
        }
        Properties current = current();
        write(current, with(current, draft, originalId));
    }

    @Override
    public void remove(String id) throws IOException {
        Properties current = current();
        SettingsForm form = SettingsMapper.fromProperties(current);
        List<SourceForm> remaining = new ArrayList<SourceForm>();
        for (SourceForm source : form.sources()) {
            if (!source.id().equals(id)) {
                remaining.add(source);
            }
        }
        write(current, form.toBuilder().sources(remaining).build());
    }

    @Override
    public void setEnabled(String id, boolean enabled) throws IOException {
        String key = SOURCE_PREFIX + id + ".enabled";
        if (enabled) {
            file.update(Collections.<String, String>emptyMap(), Collections.singleton(key),
                    AppConfigLoader.exampleConfiguration());
        } else {
            file.update(Collections.singletonMap(key, "false"), Collections.<String>emptySet(),
                    AppConfigLoader.exampleConfiguration());
        }
    }

    // ------------------------------------------------------------------ Helfer

    /** Die Datei als Properties; fehlt sie, zählt die Vorlage (daraus schreibt {@link ConfigurationFile#update}). */
    private Properties current() throws IOException {
        if (file.exists()) {
            return file.read();
        }
        Properties template = new Properties();
        template.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        return template;
    }

    /** Das Formular der Datei mit dem Entwurf an der Stelle von {@code originalId} (sonst hinten angefügt). */
    private static SettingsForm with(Properties current, SourceForm draft, String originalId) {
        SettingsForm form = SettingsMapper.fromProperties(current);
        List<SourceForm> sources = new ArrayList<SourceForm>();
        boolean replaced = false;
        for (SourceForm source : form.sources()) {
            if (originalId != null && source.id().equals(originalId)) {
                sources.add(draft);
                replaced = true;
            } else {
                sources.add(source);
            }
        }
        if (!replaced) {
            sources.add(draft);
        }
        return form.toBuilder().sources(sources).build();
    }

    /** Schreibt nur die Quell-Schlüssel des Formulars; der Rest der Datei bleibt unberührt. */
    private void write(Properties current, SettingsForm form) throws IOException {
        file.update(sourceKeys(SettingsMapper.writes(form)), sourceKeys(SettingsMapper.removals(form, current)),
                AppConfigLoader.exampleConfiguration());
    }

    static boolean isSourceKey(String key) {
        return KEY_SOURCES.equals(key) || key.startsWith(SOURCE_PREFIX);
    }

    private static Map<String, String> sourceKeys(Map<String, String> writes) {
        Map<String, String> filtered = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : writes.entrySet()) {
            if (isSourceKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static Set<String> sourceKeys(Collection<String> keys) {
        Set<String> filtered = new LinkedHashSet<String>();
        for (String key : keys) {
            if (isSourceKey(key)) {
                filtered.add(key);
            }
        }
        return filtered;
    }
}
