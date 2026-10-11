package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Knoten im Favoritenbaum, wie die Seite „Favoriten“ ihn zeigt (MainframeMate {@code BookmarkEntry}): ein Ordner mit
 * Namen und Kindern oder ein Favorit mit seinem Verweis. Jeder Knoten hat eine feste ID; Ordner werden nie über ihren
 * Namen gefunden (gleichnamige Ordner sind erlaubt). Reine Anzeigedaten, unveränderlich.
 */
public final class FavoriteEntry {

    private final String id;
    private final String name;
    private final ReaderLink link;
    private final List<FavoriteEntry> children;

    private FavoriteEntry(String id, String name, ReaderLink link, List<FavoriteEntry> children) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.name = name;
        this.link = link;
        this.children = children == null ? Collections.<FavoriteEntry>emptyList()
                : Collections.unmodifiableList(new ArrayList<FavoriteEntry>(children));
    }

    public static FavoriteEntry folder(String id, String name, List<FavoriteEntry> children) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("folder name must not be blank");
        }
        return new FavoriteEntry(id, name.trim(), null, children);
    }

    public static FavoriteEntry favorite(String id, ReaderLink link) {
        if (link == null) {
            throw new IllegalArgumentException("link must not be null");
        }
        return new FavoriteEntry(id, link.title(), link, null);
    }

    public String id() {
        return id;
    }

    /** Ordnername bzw. Titel des Favoriten. */
    public String name() {
        return name;
    }

    public boolean isFolder() {
        return link == null;
    }

    /** Der Verweis eines Favoriten; {@code null} bei einem Ordner. */
    public ReaderLink link() {
        return link;
    }

    /** Die Kinder eines Ordners in Anzeigereihenfolge; leer bei einem Favoriten. */
    public List<FavoriteEntry> children() {
        return children;
    }

    /** Wie viele Favoriten (ohne Ordner) in diesem Knoten stecken, alle Ebenen. */
    public int favoriteCount() {
        if (!isFolder()) {
            return 1;
        }
        int count = 0;
        for (FavoriteEntry child : children) {
            count += child.favoriteCount();
        }
        return count;
    }

    /** Ob {@code otherId} dieser Knoten oder einer darunter ist. */
    public boolean containsId(String otherId) {
        if (id.equals(otherId)) {
            return true;
        }
        for (FavoriteEntry child : children) {
            if (child.containsId(otherId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
