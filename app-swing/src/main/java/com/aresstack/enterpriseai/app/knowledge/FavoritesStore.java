package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.ui.reader.ReaderLink;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Favoriten (Lesezeichen wie in MainframeMate, über ihre Adresse mit Präfix) in {@code favorites.json} neben der
 * Konfigurationsdatei: je Favorit Quelle, Ressource, Titel und Fundstelle. Lesen ist tolerant (fehlende oder kaputte
 * Datei = keine Favoriten), Schreiben atomar; ein Schreibfehler landet im Protokoll, der Favorit gilt bis zum Beenden.
 */
public final class FavoritesStore {

    private static final Logger LOG = Logger.getLogger(FavoritesStore.class.getName());

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<ReaderLink> favorites = new ArrayList<ReaderLink>();

    public FavoritesStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
        favorites.addAll(load(file));
    }

    public synchronized List<ReaderLink> all() {
        return Collections.unmodifiableList(new ArrayList<ReaderLink>(favorites));
    }

    public synchronized boolean contains(ReaderLink link) {
        return link != null && favorites.contains(link);
    }

    /** Merkt den Verweis bzw. vergisst ihn, wenn er schon gemerkt ist; {@code true} = jetzt ein Favorit. */
    public synchronized boolean toggle(ReaderLink link) {
        if (link == null) {
            return false;
        }
        boolean added;
        if (favorites.remove(link)) {
            added = false;
        } else {
            favorites.add(link);
            added = true;
        }
        save();
        return added;
    }

    public synchronized void remove(ReaderLink link) {
        if (favorites.remove(link)) {
            save();
        }
    }

    private void save() {
        List<Entry> entries = new ArrayList<Entry>();
        for (ReaderLink link : favorites) {
            Entry entry = new Entry();
            entry.sourceId = link.sourceId();
            entry.resourceId = link.resourceId();
            entry.title = link.title();
            entry.location = link.location();
            entries.add(entry);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(entries, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Favoriten nicht gespeichert: " + file, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignore) {
                // nichts weiter zu tun
            }
        }
    }

    private List<ReaderLink> load(Path path) {
        List<ReaderLink> loaded = new ArrayList<ReaderLink>();
        if (!Files.isRegularFile(path)) {
            return loaded;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<Entry> entries = gson.fromJson(reader, new TypeToken<List<Entry>>() { }.getType());
            if (entries != null) {
                for (Entry entry : entries) {
                    if (entry != null && entry.sourceId != null && !entry.sourceId.trim().isEmpty()) {
                        ReaderLink link = new ReaderLink(entry.sourceId, entry.resourceId, entry.title, "",
                                entry.location);
                        if (!loaded.contains(link)) {
                            loaded.add(link);
                        }
                    }
                }
            }
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Favoriten nicht lesbar: " + path, e);
        }
        return loaded;
    }

    /** Ein Eintrag der Datei. */
    private static final class Entry {
        String sourceId;
        String resourceId;
        String title;
        String location;
    }
}
