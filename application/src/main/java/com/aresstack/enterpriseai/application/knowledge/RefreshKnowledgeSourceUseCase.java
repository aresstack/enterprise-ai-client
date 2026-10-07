package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

/**
 * Use Case "Wissensquelle aktualisieren": indexiert eine konfigurierte Quelle in ihrem konfigurierten
 * {@code SourceScope} neu, über {@link IndexKnowledgeUseCase#indexSource}.
 *
 * <p>Löst die Quelle über den {@link KnowledgeSourceCatalog} auf, damit Aufrufer (etwa die MCP-Wissenswerkzeuge) nur
 * die {@link KnowledgeSourceId} kennen müssen und weder Port noch Scope in die Hand bekommen. Blockiert für die
 * Dauer des Laufs; der {@link IndexingListener} kann ihn zwischen zwei Ressourcen abbrechen.
 */
public final class RefreshKnowledgeSourceUseCase {

    private final IndexKnowledgeUseCase indexing;
    private final KnowledgeSourceCatalog catalog;

    public RefreshKnowledgeSourceUseCase(IndexKnowledgeUseCase indexing, KnowledgeSourceCatalog catalog) {
        if (indexing == null || catalog == null) {
            throw new IllegalArgumentException("indexing und catalog sind Pflicht");
        }
        this.indexing = indexing;
        this.catalog = catalog;
    }

    public KnowledgeSourceCatalog catalog() {
        return catalog;
    }

    /**
     * Indexiert die Quelle in ihrem konfigurierten Scope neu.
     *
     * @param listener Beobachter und Abbruchschalter; {@code null} für keinen
     * @throws KnowledgeSourceException {@code NOT_FOUND}, wenn die Quelle nicht konfiguriert ist
     */
    public IndexingReport refresh(KnowledgeSourceId sourceId, IndexingListener listener) throws KnowledgeSourceException {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId ist Pflicht");
        }
        KnowledgeSourceRegistration registration = catalog.find(sourceId);
        if (registration == null) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND,
                    "Quelle " + sourceId + " ist nicht konfiguriert");
        }
        return indexing.indexSource(registration.port(), registration.scope(), listener);
    }
}
