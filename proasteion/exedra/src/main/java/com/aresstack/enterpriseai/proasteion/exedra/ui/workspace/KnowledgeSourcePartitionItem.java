package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

/**
 * Ein Bereich einer Wissensquelle (Confluence: ein Space) im aufklappbaren Teil ihrer Zeile: Schlüssel, Name und ob
 * er angehakt ist. Unveränderlich.
 */
public final class KnowledgeSourcePartitionItem {

    private final String key;
    private final String title;
    private final boolean selected;

    public KnowledgeSourcePartitionItem(String key, String title, boolean selected) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        this.key = key.trim();
        this.title = title == null || title.trim().isEmpty() ? this.key : title.trim();
        this.selected = selected;
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

    @Override
    public String toString() {
        return (selected ? "[x] " : "[ ] ") + key + " " + title;
    }
}
