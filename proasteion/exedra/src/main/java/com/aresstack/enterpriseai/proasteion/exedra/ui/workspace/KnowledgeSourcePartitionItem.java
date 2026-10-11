package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

/**
 * Ein Bereich einer Wissensquelle (Confluence: ein Space) im aufklappbaren Teil ihrer Zeile: Schlüssel, Name, ob er
 * angehakt ist, und wohin ein Klick auf den Namen führt (MainframeMate: Startseite des Space, ohne sie die Seiten
 * des Space); {@code null} = der Name ist kein Link. Unveränderlich.
 */
public final class KnowledgeSourcePartitionItem {

    private final String key;
    private final String title;
    private final boolean selected;
    private final ReaderLink link;

    public KnowledgeSourcePartitionItem(String key, String title, boolean selected) {
        this(key, title, selected, null);
    }

    public KnowledgeSourcePartitionItem(String key, String title, boolean selected, ReaderLink link) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        this.key = key.trim();
        this.title = title == null || title.trim().isEmpty() ? this.key : title.trim();
        this.selected = selected;
        this.link = link;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public boolean selected() {
        return selected;
    }

    /** Ziel des Namens im Lesebereich oder {@code null}. */
    public ReaderLink link() {
        return link;
    }

    @Override
    public String toString() {
        return (selected ? "[x] " : "[ ] ") + key + " " + title;
    }
}
