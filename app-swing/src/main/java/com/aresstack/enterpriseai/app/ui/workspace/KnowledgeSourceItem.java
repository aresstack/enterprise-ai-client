package com.aresstack.enterpriseai.app.ui.workspace;

/** Eine konfigurierte Wissensquelle, wie der Drawer sie zeigt: Name und eine stille Beschreibung (Typ, Umfang). */
public final class KnowledgeSourceItem {

    private final String name;
    private final String description;

    public KnowledgeSourceItem(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return name + (description.isEmpty() ? "" : " (" + description + ")");
    }
}
