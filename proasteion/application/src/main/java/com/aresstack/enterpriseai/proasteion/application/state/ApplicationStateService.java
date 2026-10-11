package com.aresstack.enterpriseai.proasteion.application.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein kleiner, eigener Speicher für den Anwendungszustand der Oberfläche (aus askai arch übernommen):
 * Fenstergröße und -lage, ob der Drawer eingerastet ist, seine Breite. Jede Komponente liest und schreibt ihre
 * eigenen Schlüssel. Die Datei {@code application-state.json} liegt getrennt von der Konfigurationsdatei, damit
 * ein gemerkter Zustand nie die Einstellungen berührt. Lesen ist tolerant (fehlende oder kaputte Datei = leer),
 * Schreiben ist atomar und meldet der Oberfläche nie einen Fehler.
 */
public final class ApplicationStateService {

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, String> values;

    public ApplicationStateService(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
        this.values = load(file);
    }

    /** @return der gespeicherte Wert oder {@code defaultValue}, wenn er fehlt */
    public synchronized String get(String key, String defaultValue) {
        String value = key == null ? null : values.get(key);
        return value == null ? defaultValue : value;
    }

    public synchronized boolean getBoolean(String key, boolean defaultValue) {
        String value = key == null ? null : values.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /** @return der gespeicherte Wert als Zahl oder {@code defaultValue}, wenn er fehlt oder keine Zahl ist */
    public synchronized int getInt(String key, int defaultValue) {
        String value = key == null ? null : values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException invalid) {
            return defaultValue;
        }
    }

    /** Alle gespeicherten Werte, deren Schlüssel mit {@code prefix} beginnt (Kopie, in Speicherreihenfolge). */
    public synchronized Map<String, String> withPrefix(String prefix) {
        Map<String, String> found = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (prefix == null || entry.getKey().startsWith(prefix)) {
                found.put(entry.getKey(), entry.getValue());
            }
        }
        return found;
    }

    /** Speichert einen Wert ({@code null} entfernt den Schlüssel) und schreibt sofort; wirft nie. */
    public synchronized void putAndSave(String key, String value) {
        put(key, value);
        save();
    }

    /** Speichert mehrere Werte auf einmal und schreibt sofort; wirft nie. */
    public synchronized void putAllAndSave(Map<String, String> entries) {
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
        save();
    }

    private void put(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    private Map<String, String> load(Path source) {
        if (!Files.isRegularFile(source)) {
            return new LinkedHashMap<String, String>();
        }
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<LinkedHashMap<String, String>>() { }.getType();
            Map<String, String> stored = gson.fromJson(reader, type);
            return stored == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(stored);
        } catch (RuntimeException | IOException ex) {
            return new LinkedHashMap<String, String>(); // fehlender oder kaputter Zustand beginnt leer
        }
    }

    private void save() {
        try {
            Path directory = file.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp-" + System.nanoTime());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(values, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (RuntimeException | IOException ex) {
            // Ein nicht schreibbarer Zustand darf die Oberfläche nie stören.
        }
    }
}
