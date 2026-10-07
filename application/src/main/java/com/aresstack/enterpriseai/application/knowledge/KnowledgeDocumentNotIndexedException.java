package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

/**
 * Die angefragte Ressource liegt nicht im Wissensindex (im Namespace der konfigurierten Embedding-Welt), deshalb
 * wird sie nicht aus der Quelle geladen: Der Index führt, lesbar ist nur, was indexiert ist.
 */
public final class KnowledgeDocumentNotIndexedException extends Exception {

    private static final long serialVersionUID = 1L;

    private final KnowledgeResourceId resourceId;
    private final KnowledgeSourceId sourceId;

    /** @param sourceId die ausdrücklich angefragte Quelle oder {@code null}, wenn alle konfigurierten geprüft wurden */
    public KnowledgeDocumentNotIndexedException(KnowledgeResourceId resourceId, KnowledgeSourceId sourceId) {
        super(resourceId + (sourceId == null ? " ist nicht indexiert" : " ist in Quelle " + sourceId + " nicht indexiert"));
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId ist Pflicht");
        }
        this.resourceId = resourceId;
        this.sourceId = sourceId;
    }

    public KnowledgeResourceId resourceId() {
        return resourceId;
    }

    /** Die angefragte Quelle oder {@code null}. */
    public KnowledgeSourceId sourceId() {
        return sourceId;
    }
}
