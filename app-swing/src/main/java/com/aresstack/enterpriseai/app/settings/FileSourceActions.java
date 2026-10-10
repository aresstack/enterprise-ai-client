package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.source.api.SourceDefinitionStore;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Die Quellen in der Konfigurationsdatei ({@link SourceDefinitionStore}), typneutral: {@code sources} und je Quelle
 * {@code source.<id>.type}, {@code .enabled} (nur {@code false} steht in der Datei) und die Schlüssel des Adapters.
 * Alle übrigen Schlüssel, Kommentare und die Reihenfolge der Datei bleiben, wie sie sind
 * ({@link ConfigurationFile#update}); entfernte Schlüssel werden auskommentiert, nicht gelöscht. Geprüft wird hier
 * nichts, das tut der Quellen-Port des Adapters im Use Case.
 */
public final class FileSourceActions implements SourceDefinitionStore {

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
    public List<SourceDefinition> definitions() throws IOException {
        return AppConfigLoader.sourceDefinitions(current());
    }

    @Override
    public void save(SourceDefinition definition, String originalId) throws IOException {
        if (definition == null || definition.id().isEmpty()) {
            throw new IllegalArgumentException("definition with id required");
        }
        Properties current = current();
        List<String> ids = ids(current);
        List<String> next = new ArrayList<String>();
        boolean replaced = false;
        for (String id : ids) {
            if (originalId != null && id.equals(originalId) && !replaced) {
                next.add(definition.id());
                replaced = true;
            } else if (!id.equals(definition.id())) {
                next.add(id);
            }
        }
        if (!replaced) {
            next.add(definition.id());
        }
        Set<String> known = new LinkedHashSet<String>(ids);
        known.addAll(next);
        Map<String, String> set = new LinkedHashMap<String, String>();
        set.put(KEY_SOURCES, String.join(",", next));
        String prefix = SOURCE_PREFIX + definition.id() + ".";
        set.put(prefix + "type", definition.typeId());
        if (!definition.enabled()) {
            set.put(prefix + "enabled", "false");
        }
        for (Map.Entry<String, String> entry : definition.settings().asMap().entrySet()) {
            set.put(prefix + entry.getKey(), entry.getValue());
        }
        Set<String> remove = new LinkedHashSet<String>();
        for (String key : ownedKeys(current, definition.id(), known)) {
            if (!set.containsKey(key)) {
                remove.add(key);
            }
        }
        if (originalId != null && !originalId.equals(definition.id())) {
            remove.addAll(ownedKeys(current, originalId, known));
            remove.removeAll(set.keySet());
        }
        file.update(set, remove, AppConfigLoader.exampleConfiguration());
    }

    @Override
    public void remove(String id) throws IOException {
        Properties current = current();
        List<String> ids = ids(current);
        List<String> remaining = new ArrayList<String>();
        for (String other : ids) {
            if (!other.equals(id)) {
                remaining.add(other);
            }
        }
        Set<String> remove = new LinkedHashSet<String>(ownedKeys(current, id, ids));
        Map<String, String> set = new LinkedHashMap<String, String>();
        if (remaining.isEmpty()) {
            remove.add(KEY_SOURCES);
        } else {
            set.put(KEY_SOURCES, String.join(",", remaining));
        }
        file.update(set, remove, AppConfigLoader.exampleConfiguration());
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

    private static List<String> ids(Properties current) {
        List<String> ids = new ArrayList<String>();
        String value = current.getProperty(KEY_SOURCES, "");
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty() && !ids.contains(trimmed)) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    /** Die Schlüssel {@code source.<id>.*} der Datei, die keiner längeren bekannten ID gehören. */
    private static Set<String> ownedKeys(Properties current, String id, Iterable<String> knownIds) {
        Set<String> keys = new LinkedHashSet<String>();
        for (String key : current.stringPropertyNames()) {
            if (id.equals(SettingsMapper.sourceIdOf(key, withId(knownIds, id)))) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static List<String> withId(Iterable<String> knownIds, String id) {
        List<String> ids = new ArrayList<String>();
        for (String known : knownIds) {
            ids.add(known);
        }
        if (!ids.contains(id)) {
            ids.add(id);
        }
        return ids;
    }

    static boolean isSourceKey(String key) {
        return KEY_SOURCES.equals(key) || key.startsWith(SOURCE_PREFIX);
    }
}
