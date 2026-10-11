package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;

/**
 * Ein vorgeschlagenes Modell aus einem Hub-Repository, dessen Dateien erst beim Installieren aus dem Dateibaum des
 * Repositorys bestimmt werden (wie bei einem Treffer der Hub-Suche). Die Kennung gilt nur für den Vorschlag; installiert
 * wird unter dem Ordnernamen, den auch die Suche vergäbe.
 */
public final class HubRepositorySuggestion {

    private final String id;
    private final String displayName;
    private final String repository;
    private final ModelCategory category;

    public HubRepositorySuggestion(String id, String displayName, String repository, ModelCategory category) {
        if (id == null || !id.matches("[A-Za-z0-9._-]+") || id.startsWith(".")) {
            throw new IllegalArgumentException("id must be a plain name: " + id);
        }
        if (repository == null || repository.trim().isEmpty()) {
            throw new IllegalArgumentException("repository must not be blank");
        }
        if (category != ModelCategory.STT && category != ModelCategory.CHAT && category != ModelCategory.EMBEDDING) {
            throw new IllegalArgumentException("category must be STT, CHAT or EMBEDDING: " + category);
        }
        this.id = id;
        this.displayName = displayName == null || displayName.trim().isEmpty() ? repository.trim() : displayName.trim();
        this.repository = repository.trim();
        this.category = category;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String repository() {
        return repository;
    }

    public ModelCategory category() {
        return category;
    }
}
