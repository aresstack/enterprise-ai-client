package com.aresstack.enterpriseai.domain.localruntime;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;

/**
 * Ein Vorschlag im Dropdown der Hub-Suche: Suchbegriff und die Funktion, für die er ein ladbares Modell findet. Wird
 * er gewählt, sucht die Oberfläche mit genau diesem Begriff in genau dieser Funktion.
 */
public final class HubSearchSuggestion {

    private final String query;
    private final ModelCategory category;

    public HubSearchSuggestion(String query, ModelCategory category) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        this.query = query.trim();
        this.category = category;
    }

    public String query() {
        return query;
    }

    public ModelCategory category() {
        return category;
    }

    /** Im Eingabefeld der Suche steht nur der Suchbegriff. */
    @Override
    public String toString() {
        return query;
    }
}
