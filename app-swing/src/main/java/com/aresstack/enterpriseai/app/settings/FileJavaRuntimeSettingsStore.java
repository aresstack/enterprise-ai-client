package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.model.api.JavaRuntimeSettingsStore;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Java-Laufzeit des Sidecars im Schlüssel {@code models.local.java} der Konfigurationsdatei. Fehlt die Datei
 * (Erststart), wird nichts geschrieben: der Einstellungen-Dialog legt sie an und übernimmt dabei die Wahl.
 */
public final class FileJavaRuntimeSettingsStore implements JavaRuntimeSettingsStore {

    private static final Logger LOG = Logger.getLogger(FileJavaRuntimeSettingsStore.class.getName());

    private final ConfigurationFile file;

    public FileJavaRuntimeSettingsStore(ConfigurationFile file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    @Override
    public Path load() {
        try {
            String value = file.read().getProperty(SettingsMapper.KEY_LOCAL_JAVA);
            return value == null || value.trim().isEmpty() ? null : Paths.get(value.trim());
        } catch (IOException | InvalidPathException e) {
            LOG.log(Level.WARNING, "models.local.java nicht lesbar", e);
            return null;
        }
    }

    @Override
    public void save(Path javaExecutable) {
        if (javaExecutable == null || !file.exists()) {
            return;
        }
        try {
            file.update(Collections.singletonMap(SettingsMapper.KEY_LOCAL_JAVA, javaExecutable.toString()), null,
                    null);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Java-Laufzeit " + javaExecutable + " nicht gespeichert", e);
        }
    }
}
