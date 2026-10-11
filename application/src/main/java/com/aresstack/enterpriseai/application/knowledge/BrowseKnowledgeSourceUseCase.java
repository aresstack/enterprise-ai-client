package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Use Case „Wissensquelle lesen“ für den Lesebereich (wie der Lese-Tab in MainframeMate): listet die Ressourcen einer
 * Quelle im konfigurierten Umfang (höchstens {@link #MAX_LISTED}) und lädt eine Ressource zum Lesen über einen
 * {@link KnowledgeDocumentReader}. Kennt Quellen nur über ihren Port; blockiert für die Dauer des Aufrufs.
 */
public final class BrowseKnowledgeSourceUseCase {

    /** Mehr Einträge zeigt die Übersicht einer Quelle nicht; die Suche findet den Rest. */
    public static final int MAX_LISTED = 200;

    private final KnowledgeDocumentReader reader;
    private final IndexKnowledgeUseCase indexing;

    public BrowseKnowledgeSourceUseCase(KnowledgeDocumentReader reader) {
        this(reader, null);
    }

    private BrowseKnowledgeSourceUseCase(KnowledgeDocumentReader reader, IndexKnowledgeUseCase indexing) {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }
        this.reader = reader;
        this.indexing = indexing;
    }

    /** Derselbe Use Case, der jede gelesene Ressource in den Index nimmt, falls neu oder geändert (On-Demand). */
    public BrowseKnowledgeSourceUseCase withIndexing(IndexKnowledgeUseCase value) {
        if (value == null) {
            throw new IllegalArgumentException("indexing must not be null");
        }
        return new BrowseKnowledgeSourceUseCase(reader, value);
    }

    /** Die Ressourcen der Quelle im Umfang {@code scope}, ohne Inhalt, höchstens {@link #MAX_LISTED}. */
    public List<KnowledgeResource> list(KnowledgeSourcePort source, SourceScope scope) throws KnowledgeSourceException {
        if (source == null || scope == null) {
            throw new IllegalArgumentException("source and scope must not be null");
        }
        SourceScope limited = SourceScope.builder().startPoints(scope.startPoints()).maxDepth(scope.maxDepth())
                .maxResources(Math.min(MAX_LISTED, scope.maxResources())).build();
        List<KnowledgeResource> resources = source.discover(limited);
        return Collections.unmodifiableList(new ArrayList<KnowledgeResource>(
                resources.size() > MAX_LISTED ? resources.subList(0, MAX_LISTED) : resources));
    }

    /** Der aktuelle Text der Ressource aus ihrer Quelle. */
    public KnowledgeDocument read(KnowledgeSourcePort source, KnowledgeResourceId id) throws KnowledgeSourceException {
        if (source == null || id == null) {
            throw new IllegalArgumentException("source and id must not be null");
        }
        return reader.read(source.sourceId(), source, id);
    }

    /**
     * Ein gelesenes Dokument in den Index nehmen, falls neu oder geändert (nach dem Anzeigen aufrufen, es bettet
     * ein); ohne {@link #withIndexing} nichts. Wirft nicht.
     */
    public void remember(KnowledgeDocument document) {
        if (indexing == null || document == null) {
            return;
        }
        try {
            indexing.indexDocumentIfChanged(document);
        } catch (RuntimeException e) {
            // Lesen gelingt auch ohne Indexierung; die nächste Suche versucht es erneut.
        }
    }
}
