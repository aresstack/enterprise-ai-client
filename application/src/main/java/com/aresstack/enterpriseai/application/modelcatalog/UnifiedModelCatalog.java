package com.aresstack.enterpriseai.application.modelcatalog;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.model.api.ModelCatalogException;
import com.aresstack.enterpriseai.model.api.ModelCatalogPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Führt mehrere {@link ModelCatalogPort}s zu einem Katalog zusammen (askai-java8 arch: eine Modellauswahl je
 * Funktion, egal ob entfernt oder lokal). {@link #refresh()} fragt jede Quelle nacheinander ab und blockiert;
 * Aufrufer laufen nie auf dem EDT. Scheitert eine Quelle, bleiben ihre zuletzt bekannten Modelle stehen und ihr
 * Stand meldet den Grund. Doppelte Kennungen derselben Quelle zählen einmal.
 */
public final class UnifiedModelCatalog {

    private final List<ModelCatalogPort> catalogs;
    private volatile ModelCatalogSnapshot current;

    /**
     * @param catalogs die Quellen in Anzeigereihenfolge
     * @param initial  der zuletzt bekannte Stand (z. B. aus einem Zwischenspeicher); {@code null} = leer
     */
    public UnifiedModelCatalog(List<ModelCatalogPort> catalogs, ModelCatalogSnapshot initial) {
        if (catalogs == null) {
            throw new IllegalArgumentException("catalogs must not be null");
        }
        this.catalogs = Collections.unmodifiableList(new ArrayList<ModelCatalogPort>(catalogs));
        this.current = initial == null ? ModelCatalogSnapshot.empty() : initial;
    }

    /** Der zuletzt bekannte Stand, ohne zu blockieren. */
    public ModelCatalogSnapshot current() {
        return current;
    }

    /** Fragt alle Quellen ab, merkt sich den neuen Stand und liefert ihn. */
    public ModelCatalogSnapshot refresh() {
        ModelCatalogSnapshot previous = current;
        List<ModelDescriptor> models = new ArrayList<ModelDescriptor>();
        List<CatalogStatus> statuses = new ArrayList<CatalogStatus>();
        for (ModelCatalogPort catalog : catalogs) {
            String id = catalog.catalogId();
            try {
                List<ModelDescriptor> fetched = catalog.models();
                int added = addUnique(models, fetched, id);
                statuses.add(new CatalogStatus(id, catalog.displayName(), true, added + " Modell(e)"));
            } catch (ModelCatalogException | RuntimeException e) {
                List<ModelDescriptor> kept = new ArrayList<ModelDescriptor>();
                for (ModelDescriptor model : previous.models()) {
                    if (model.catalogId().equals(id)) {
                        kept.add(model);
                    }
                }
                addUnique(models, kept, id);
                String reason = e instanceof ModelCatalogException && e.getMessage() != null
                        ? e.getMessage() : e.getClass().getSimpleName();
                statuses.add(new CatalogStatus(id, catalog.displayName(), false, kept.isEmpty() ? reason
                        : reason + "; zuletzt bekannte Modelle bleiben sichtbar"));
            }
        }
        ModelCatalogSnapshot refreshed = new ModelCatalogSnapshot(models, statuses);
        current = refreshed;
        return refreshed;
    }

    private static int addUnique(List<ModelDescriptor> into, List<ModelDescriptor> models, String catalogId) {
        Set<String> seen = new LinkedHashSet<String>();
        for (ModelDescriptor existing : into) {
            if (existing.catalogId().equals(catalogId)) {
                seen.add(existing.modelId());
            }
        }
        int added = 0;
        if (models != null) {
            for (ModelDescriptor model : models) {
                if (model != null && model.catalogId().equals(catalogId) && seen.add(model.modelId())) {
                    into.add(model);
                    added++;
                }
            }
        }
        return added;
    }
}
