package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.source.api.SourceDefinitionStore;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Die Quellen in der Konfigurationsdatei ({@link SourceDefinitionStore}), typneutral als Liste: je Quelle
 * {@code source.<n>.address} (kanonische Adresse, die Identität), {@code .type}, {@code .enabled} (nur {@code false}
 * steht in der Datei) und die Schlüssel des Adapters. {@code n} ist nur die Listenposition; gefunden wird eine Quelle
 * über ihre Adresse. Alle übrigen Schlüssel, Kommentare und die Reihenfolge der Datei bleiben, wie sie sind
 * ({@link ConfigurationFile#update}); entfernte Schlüssel werden auskommentiert, nicht gelöscht. Geprüft wird hier
 * nichts, das tut der Quellen-Port des Adapters im Use Case.
 */
public final class FileSourceActions implements SourceDefinitionStore {

    static final String SOURCE_PREFIX = AppConfigLoader.SOURCE_PREFIX;
    /** Frühere Form {@code sources=<id>,<id>} mit {@code source.<id>.*}; wird beim Start entfernt. */
    static final String LEGACY_KEY_SOURCES = "sources";

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
    public void save(SourceDefinition definition, String originalAddress) throws IOException {
        if (definition == null || definition.address().isEmpty()) {
            throw new IllegalArgumentException("definition with address required");
        }
        Properties current = current();
        Integer slot = originalAddress == null ? null : slotOf(current, originalAddress, definition.typeId());
        if (slot == null) {
            slot = slotOf(current, definition.address(), definition.typeId());
        }
        if (slot == null) {
            List<Integer> slots = AppConfigLoader.sourceSlots(current);
            slot = slots.isEmpty() ? 1 : slots.get(slots.size() - 1) + 1;
        }
        String prefix = SOURCE_PREFIX + slot + ".";
        Map<String, String> set = new LinkedHashMap<String, String>();
        set.put(prefix + AppConfigLoader.SOURCE_ADDRESS_FIELD, definition.address());
        set.put(prefix + "type", definition.typeId());
        if (!definition.enabled()) {
            set.put(prefix + "enabled", "false");
        }
        for (Map.Entry<String, String> entry : definition.settings().asMap().entrySet()) {
            set.put(prefix + entry.getKey(), entry.getValue());
        }
        Set<String> remove = new LinkedHashSet<String>();
        for (String key : slotKeys(current, slot)) {
            if (!set.containsKey(key)) {
                remove.add(key);
            }
        }
        file.update(set, remove, AppConfigLoader.exampleConfiguration());
    }

    @Override
    public void remove(String address) throws IOException {
        Properties current = current();
        Integer slot = slotOf(current, address, null);
        if (slot == null) {
            return;
        }
        file.update(Collections.<String, String>emptyMap(), slotKeys(current, slot),
                AppConfigLoader.exampleConfiguration());
    }

    @Override
    public void setEnabled(String address, boolean enabled) throws IOException {
        Integer slot = slotOf(current(), address, null);
        if (slot == null) {
            return;
        }
        String key = SOURCE_PREFIX + slot + ".enabled";
        if (enabled) {
            file.update(Collections.<String, String>emptyMap(), Collections.singleton(key),
                    AppConfigLoader.exampleConfiguration());
        } else {
            file.update(Collections.singletonMap(key, "false"), Collections.<String>emptySet(),
                    AppConfigLoader.exampleConfiguration());
        }
    }

    /**
     * Entfernt Quellen der früheren Form ({@code sources=<id>,…} und {@code source.<id>.*} mit frei vergebener ID).
     * Keine Überführung: solche Quellen werden im Reiter „Wissensquellen“ neu angelegt.
     *
     * @return die Zahl der entfernten Schlüssel
     */
    public int dropLegacyEntries() throws IOException {
        if (!file.exists()) {
            return 0;
        }
        Set<String> remove = new LinkedHashSet<String>();
        for (String key : file.read().stringPropertyNames()) {
            if (LEGACY_KEY_SOURCES.equals(key)
                    || key.startsWith(SOURCE_PREFIX) && AppConfigLoader.sourceSlotOf(key) == null) {
                remove.add(key);
            }
        }
        if (!remove.isEmpty()) {
            file.update(Collections.<String, String>emptyMap(), remove, AppConfigLoader.exampleConfiguration());
        }
        return remove.size();
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

    /**
     * Die Listenposition der Quelle mit dieser Adresse; bei leerer Adresse die erste Quelle des Typs ohne Adresse.
     */
    private static Integer slotOf(Properties current, String address, String typeId) {
        for (int slot : AppConfigLoader.sourceSlots(current)) {
            String stored = current.getProperty(SOURCE_PREFIX + slot + "." + AppConfigLoader.SOURCE_ADDRESS_FIELD,
                    "").trim();
            if (!address.isEmpty() && stored.equals(address)) {
                return slot;
            }
            if (address.isEmpty() && stored.isEmpty() && typeId != null && typeId.equalsIgnoreCase(
                    current.getProperty(SOURCE_PREFIX + slot + ".type", "").trim())) {
                return slot;
            }
        }
        return null;
    }

    /** Die Schlüssel {@code source.<n>.*} der Datei. */
    private static Set<String> slotKeys(Properties current, int slot) {
        Set<String> keys = new LinkedHashSet<String>();
        String prefix = SOURCE_PREFIX + slot + ".";
        for (String key : current.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        return keys;
    }

    static boolean isSourceKey(String key) {
        return LEGACY_KEY_SOURCES.equals(key) || key.startsWith(SOURCE_PREFIX);
    }
}
