package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

/**
 * Wie {@link LoadKnowledgeDocumentUseCase} den Text einer Ressource holt. Produktiv ist das der vermittelte Zugriff
 * nach corenth (Tamias prüft, Chalcotheca archiviert, Holkas holt über den Connector der Quelle); {@link #direct()}
 * fragt den Quellen-Port selbst und bleibt für Aufbauten ohne Ressourcenschicht.
 */
public interface KnowledgeDocumentReader {

    /**
     * @param owner  die Quelle, aus der gelesen wird
     * @param source ihr Port
     * @throws KnowledgeSourceException wie {@link KnowledgeSourcePort#load}
     */
    KnowledgeDocument read(KnowledgeSourceId owner, KnowledgeSourcePort source, KnowledgeResourceId id)
            throws KnowledgeSourceException;

    /** Liest direkt aus dem Quellen-Port. */
    static KnowledgeDocumentReader direct() {
        return new KnowledgeDocumentReader() {
            @Override
            public KnowledgeDocument read(KnowledgeSourceId owner, KnowledgeSourcePort source,
                                          KnowledgeResourceId id) throws KnowledgeSourceException {
                return source.load(id);
            }
        };
    }
}
