package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.FavoriteEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Favoriten als Ordnerbaum wie die Lesezeichen in MainframeMate ({@code BookmarkHelper}), in {@code favorites.json}
 * neben der Konfigurationsdatei. Ein Favorit ist ein Verweis (Quelle + kanonische Ressource) und kommt im ganzen Baum
 * höchstens einmal vor; Ordner dürfen gleich heißen. Alles läuft über die IDs der Knoten, nie über Namen oder Adressen
 * (MainframeMate löschte über den Ordnernamen alle gleichnamigen Ordner). Unbekannte oder ungültige Ziele landen in der
 * Wurzel bzw. werden abgelehnt, nie verworfen: ein Eintrag geht beim Verschieben nicht verloren.
 *
 * <p>Lesen ist tolerant (fehlende oder kaputte Datei = keine Favoriten), Schreiben atomar; ein Schreibfehler landet im
 * Protokoll, der Stand gilt bis zum Beenden. Eine Datei im alten Format (Liste ohne Ordner) wird verworfen.
 */
public final class FavoritesStore {

    private static final Logger LOG = Logger.getLogger(FavoritesStore.class.getName());
    static final int FORMAT_VERSION = 2;

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<Node> root = new ArrayList<Node>();

    public FavoritesStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
        load();
    }

    // ------------------------------------------------------------------ Lesen

    /** Der Baum unterhalb der (unsichtbaren) Wurzel. */
    public synchronized List<FavoriteEntry> tree() {
        return snapshot(root);
    }

    /** Alle Favoriten (ohne Ordner) in Baumreihenfolge. */
    public synchronized List<ReaderLink> all() {
        List<ReaderLink> links = new ArrayList<ReaderLink>();
        collectLinks(root, links);
        return Collections.unmodifiableList(links);
    }

    public synchronized boolean contains(ReaderLink link) {
        return link != null && findLink(root, link) != null;
    }

    /** Ob {@code id} ein vorhandener Ordner ist. */
    public synchronized boolean isFolder(String id) {
        Node node = find(root, id);
        return node != null && node.isFolder();
    }

    // ------------------------------------------------------------------ Ändern

    /**
     * Stern geklickt: ist der Verweis schon ein Favorit (egal in welchem Ordner), wird er entfernt; sonst kommt er ans
     * Ende des Ordners {@code folderId} ({@code null}, unbekannt oder kein Ordner = Wurzel).
     *
     * @return die ID des neuen Favoriten, {@code null} wenn er entfernt wurde
     */
    public synchronized String toggle(ReaderLink link, String folderId) {
        if (link == null) {
            return null;
        }
        Node existing = findLink(root, link);
        if (existing != null) {
            removeNode(root, existing.id);
            save();
            return null;
        }
        Node node = Node.favorite(newId(), link);
        childrenOf(folderId).add(node);
        save();
        return node.id;
    }

    /** Legt einen Ordner am Ende von {@code parentId} an ({@code null}/unbekannt = Wurzel); {@code null} bei leerem Namen. */
    public synchronized String createFolder(String name, String parentId) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Node folder = Node.folder(newId(), name.trim());
        childrenOf(parentId).add(folder);
        save();
        return folder.id;
    }

    /** Benennt einen Ordner um; Favoriten behalten den Titel ihrer Ressource. */
    public synchronized boolean renameFolder(String id, String name) {
        Node node = find(root, id);
        if (node == null || !node.isFolder() || name == null || name.trim().isEmpty()) {
            return false;
        }
        node.name = name.trim();
        save();
        return true;
    }

    /** Entfernt einen Favoriten bzw. einen Ordner samt Inhalt. */
    public synchronized boolean remove(String id) {
        if (removeNode(root, id) == null) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Verschiebt einen Knoten in den Ordner {@code targetFolderId} ({@code null} = Wurzel) an Position {@code index}
     * (gezählt in der Liste VOR dem Herausnehmen, wie die Einfügemarke im Baum; außerhalb = ans Ende). Abgelehnt wird
     * ein unbekannter Knoten, ein Ziel, das kein Ordner ist, und ein Ordner in sich selbst oder seinen Unterbaum.
     */
    public synchronized boolean move(String id, String targetFolderId, int index) {
        Node moved = find(root, id);
        if (moved == null) {
            return false;
        }
        List<Node> target;
        if (targetFolderId == null) {
            target = root;
        } else {
            Node folder = find(root, targetFolderId);
            if (folder == null || !folder.isFolder() || find(Collections.singletonList(moved), targetFolderId) != null) {
                return false;
            }
            target = folder.children;
        }
        int oldIndex = target.indexOf(moved);
        if (oldIndex >= 0 && (index == oldIndex || index == oldIndex + 1)) {
            return true; // an derselben Stelle abgelegt
        }
        removeNode(root, id);
        if (oldIndex >= 0 && index > oldIndex) {
            index--; // in derselben Liste nach unten: die Liste ist durch das Herausnehmen kürzer geworden
        }
        if (index < 0 || index > target.size()) {
            index = target.size();
        }
        target.add(index, moved);
        save();
        return true;
    }

    // ------------------------------------------------------------------ Baum

    private List<Node> childrenOf(String folderId) {
        Node folder = folderId == null ? null : find(root, folderId);
        return folder != null && folder.isFolder() ? folder.children : root;
    }

    private static Node find(List<Node> nodes, String id) {
        if (id == null) {
            return null;
        }
        for (Node node : nodes) {
            if (node.id.equals(id)) {
                return node;
            }
            if (node.isFolder()) {
                Node found = find(node.children, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Node findLink(List<Node> nodes, ReaderLink link) {
        for (Node node : nodes) {
            if (node.isFolder()) {
                Node found = findLink(node.children, link);
                if (found != null) {
                    return found;
                }
            } else if (node.link.equals(link)) {
                return node;
            }
        }
        return null;
    }

    private static Node removeNode(List<Node> nodes, String id) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.id.equals(id)) {
                return nodes.remove(i);
            }
            if (node.isFolder()) {
                Node removed = removeNode(node.children, id);
                if (removed != null) {
                    return removed;
                }
            }
        }
        return null;
    }

    private static void collectLinks(List<Node> nodes, List<ReaderLink> links) {
        for (Node node : nodes) {
            if (node.isFolder()) {
                collectLinks(node.children, links);
            } else {
                links.add(node.link);
            }
        }
    }

    private static List<FavoriteEntry> snapshot(List<Node> nodes) {
        List<FavoriteEntry> entries = new ArrayList<FavoriteEntry>();
        for (Node node : nodes) {
            entries.add(node.isFolder() ? FavoriteEntry.folder(node.id, node.name, snapshot(node.children))
                    : FavoriteEntry.favorite(node.id, node.link));
        }
        return Collections.unmodifiableList(entries);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------ Datei

    private void save() {
        FileFormat content = new FileFormat();
        content.version = FORMAT_VERSION;
        content.entries = toEntries(root);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(content, writer);
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

    private static List<Entry> toEntries(List<Node> nodes) {
        List<Entry> entries = new ArrayList<Entry>();
        for (Node node : nodes) {
            Entry entry = new Entry();
            entry.id = node.id;
            if (node.isFolder()) {
                entry.folder = true;
                entry.name = node.name;
                entry.children = toEntries(node.children);
            } else {
                entry.sourceId = node.link.sourceId();
                entry.resourceId = node.link.resourceId();
                entry.title = node.link.title();
                entry.location = node.link.location();
            }
            entries.add(entry);
        }
        return entries;
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        boolean discard = false;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (json != null && json.isJsonObject()) {
                FileFormat content = gson.fromJson(json, FileFormat.class);
                if (content != null && content.version == FORMAT_VERSION) {
                    Set<String> ids = new HashSet<String>();
                    root.addAll(fromEntries(content.entries, ids, new ArrayList<ReaderLink>()));
                } else {
                    discard = true;
                }
            } else if (json != null && json.isJsonArray()) {
                discard = true; // altes Format ohne Ordner: kein Produktivbetrieb, keine Migration
            }
        } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException e) {
            LOG.log(Level.WARNING, "Favoriten nicht lesbar: " + file, e);
        }
        if (discard) {
            LOG.info("Favoriten im alten Format verworfen: " + file);
            save();
        }
    }

    /** Liest den Baum; doppelte IDs bekommen eine neue, doppelte Verweise und unvollständige Einträge entfallen. */
    private static List<Node> fromEntries(List<Entry> entries, Set<String> ids, List<ReaderLink> links) {
        List<Node> nodes = new ArrayList<Node>();
        if (entries == null) {
            return nodes;
        }
        for (Entry entry : entries) {
            if (entry == null) {
                continue;
            }
            String id = entry.id == null || entry.id.isEmpty() || ids.contains(entry.id) ? newId() : entry.id;
            if (entry.folder) {
                if (entry.name == null || entry.name.trim().isEmpty()) {
                    continue;
                }
                ids.add(id);
                Node folder = Node.folder(id, entry.name.trim());
                folder.children.addAll(fromEntries(entry.children, ids, links));
                nodes.add(folder);
            } else if (entry.sourceId != null && !entry.sourceId.trim().isEmpty()) {
                ReaderLink link = new ReaderLink(entry.sourceId, entry.resourceId, entry.title, "", entry.location);
                if (!links.contains(link)) {
                    ids.add(id);
                    links.add(link);
                    nodes.add(Node.favorite(id, link));
                }
            }
        }
        return nodes;
    }

    /** Ein Knoten im Speicher. */
    private static final class Node {
        final String id;
        String name;
        final ReaderLink link;
        final List<Node> children;

        private Node(String id, String name, ReaderLink link) {
            this.id = id;
            this.name = name;
            this.link = link;
            this.children = link == null ? new ArrayList<Node>() : Collections.<Node>emptyList();
        }

        static Node folder(String id, String name) {
            return new Node(id, name, null);
        }

        static Node favorite(String id, ReaderLink link) {
            return new Node(id, link.title(), link);
        }

        boolean isFolder() {
            return link == null;
        }
    }

    /** Die Datei: Formatversion und Baum. */
    private static final class FileFormat {
        int version;
        List<Entry> entries;
    }

    /** Ein Knoten der Datei: Ordner mit Name und Kindern oder Favorit mit Quelle, Ressource, Titel und Fundstelle. */
    private static final class Entry {
        String id;
        boolean folder;
        String name;
        List<Entry> children;
        String sourceId;
        String resourceId;
        String title;
        String location;
    }
}
