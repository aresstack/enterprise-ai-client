package com.aresstack.enterpriseai.application.modelcatalog;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unveränderlicher Stand des vereinten Katalogs: alle Modelle aller Quellen und der Stand jeder Quelle. */
public final class ModelCatalogSnapshot {

    private final List<ModelDescriptor> models;
    private final List<CatalogStatus> statuses;

    public ModelCatalogSnapshot(List<ModelDescriptor> models, List<CatalogStatus> statuses) {
        this.models = Collections.unmodifiableList(new ArrayList<ModelDescriptor>(
                models == null ? Collections.<ModelDescriptor>emptyList() : models));
        this.statuses = Collections.unmodifiableList(new ArrayList<CatalogStatus>(
                statuses == null ? Collections.<CatalogStatus>emptyList() : statuses));
    }

    public static ModelCatalogSnapshot empty() {
        return new ModelCatalogSnapshot(null, null);
    }

    public List<ModelDescriptor> models() {
        return models;
    }

    /** Die Modelle einer Kategorie in Katalogreihenfolge; entfernte und lokale gemischt. */
    public List<ModelDescriptor> modelsFor(ModelCategory category) {
        List<ModelDescriptor> result = new ArrayList<ModelDescriptor>();
        for (ModelDescriptor model : models) {
            if (model.supports(category)) {
                result.add(model);
            }
        }
        return result;
    }

    /** @return das Modell zum Verweis oder {@code null} */
    public ModelDescriptor find(ModelReference reference) {
        if (reference == null) {
            return null;
        }
        for (ModelDescriptor model : models) {
            if (model.catalogId().equals(reference.catalogId()) && model.modelId().equals(reference.modelId())) {
                return model;
            }
        }
        return null;
    }

    public List<CatalogStatus> statuses() {
        return statuses;
    }

    public boolean isEmpty() {
        return models.isEmpty() && statuses.isEmpty();
    }

    @Override
    public String toString() {
        return "ModelCatalogSnapshot[models=" + models.size() + ", " + statuses + "]";
    }
}
