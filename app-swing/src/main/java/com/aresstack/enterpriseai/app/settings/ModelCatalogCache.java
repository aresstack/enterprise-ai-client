package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.application.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Zwischenspeicher der letzten erfolgreichen Modellabfrage neben der Konfiguration
 * ({@code <Anwendungsverzeichnis>/model-catalog.properties}), damit die Auswahllisten auch ohne Verbindung
 * sichtbar sind. Enthält nur Modellkennungen und Metadaten, keine Secrets. Unlesbar oder fehlend heißt leer.
 */
public final class ModelCatalogCache {

    static final String STALE_PREFIX = "Stand der letzten Abfrage: ";

    private final Path file;

    public ModelCatalogCache(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public synchronized ModelCatalogSnapshot read() {
        if (!Files.isRegularFile(file)) {
            return ModelCatalogSnapshot.empty();
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | RuntimeException e) {
            return ModelCatalogSnapshot.empty();
        }
        List<ModelDescriptor> models = new ArrayList<ModelDescriptor>();
        for (int i = 0; i < count(p, "model.count"); i++) {
            String prefix = "model." + i + ".";
            try {
                models.add(ModelDescriptor.builder(p.getProperty(prefix + "catalog"), p.getProperty(prefix + "id"))
                        .catalogName(p.getProperty(prefix + "catalogName"))
                        .displayName(p.getProperty(prefix + "name"))
                        .capabilities(list(p.getProperty(prefix + "capabilities")))
                        .inputModalities(list(p.getProperty(prefix + "input")))
                        .outputModalities(list(p.getProperty(prefix + "output")))
                        .toolCalling(Boolean.parseBoolean(p.getProperty(prefix + "toolCalling")))
                        .reasoning(Boolean.parseBoolean(p.getProperty(prefix + "reasoning")))
                        .contextLength(count(p, prefix + "contextLength"))
                        .build());
            } catch (IllegalArgumentException skipped) {
                // unvollständiger Eintrag: weglassen
            }
        }
        List<CatalogStatus> statuses = new ArrayList<CatalogStatus>();
        for (int i = 0; i < count(p, "status.count"); i++) {
            String prefix = "status." + i + ".";
            String id = p.getProperty(prefix + "catalog");
            if (id != null && !id.isEmpty()) {
                statuses.add(new CatalogStatus(id, p.getProperty(prefix + "name"),
                        Boolean.parseBoolean(p.getProperty(prefix + "available")),
                        STALE_PREFIX + p.getProperty(prefix + "message", "")));
            }
        }
        return new ModelCatalogSnapshot(models, statuses);
    }

    /** Schreibt den Stand atomar (temporäre Datei, dann umbenennen); Fehler bleiben folgenlos (nur Komfort). */
    public synchronized void write(ModelCatalogSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Properties p = new Properties();
        List<ModelDescriptor> models = snapshot.models();
        p.setProperty("model.count", String.valueOf(models.size()));
        for (int i = 0; i < models.size(); i++) {
            ModelDescriptor model = models.get(i);
            String prefix = "model." + i + ".";
            p.setProperty(prefix + "catalog", model.catalogId());
            p.setProperty(prefix + "catalogName", model.catalogName());
            p.setProperty(prefix + "id", model.modelId());
            p.setProperty(prefix + "name", model.displayName());
            p.setProperty(prefix + "capabilities", join(model.capabilities()));
            p.setProperty(prefix + "input", join(model.inputModalities()));
            p.setProperty(prefix + "output", join(model.outputModalities()));
            p.setProperty(prefix + "toolCalling", String.valueOf(model.toolCalling()));
            p.setProperty(prefix + "reasoning", String.valueOf(model.reasoning()));
            p.setProperty(prefix + "contextLength", String.valueOf(model.contextLength()));
        }
        List<CatalogStatus> statuses = snapshot.statuses();
        p.setProperty("status.count", String.valueOf(statuses.size()));
        for (int i = 0; i < statuses.size(); i++) {
            CatalogStatus status = statuses.get(i);
            String prefix = "status." + i + ".";
            String message = status.message();
            if (message.startsWith(STALE_PREFIX)) {
                message = message.substring(STALE_PREFIX.length());
            }
            p.setProperty(prefix + "catalog", status.catalogId());
            p.setProperty(prefix + "name", status.displayName());
            p.setProperty(prefix + "available", String.valueOf(status.available()));
            p.setProperty(prefix + "message", message);
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                p.store(out, "Modellkatalog der letzten Abfrage (Enterprise AI Client); wird automatisch erneuert");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // Nur ein Zwischenspeicher: ohne ihn fehlen die Listen bis zur nächsten Abfrage.
        }
    }

    private static int count(Properties p, String key) {
        try {
            return Math.max(0, Integer.parseInt(p.getProperty(key, "0").trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> list(String value) {
        List<String> items = new ArrayList<String>();
        if (value != null) {
            for (String item : Arrays.asList(value.split(","))) {
                if (!item.trim().isEmpty()) {
                    items.add(item.trim());
                }
            }
        }
        return items;
    }

    private static String join(List<String> items) {
        StringBuilder text = new StringBuilder();
        for (String item : items) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(item);
        }
        return text.toString();
    }
}
