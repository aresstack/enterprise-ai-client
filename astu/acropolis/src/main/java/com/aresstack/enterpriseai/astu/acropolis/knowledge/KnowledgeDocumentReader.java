package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
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
    KnowledgeDocument read(SourceAddress owner, KnowledgeSourcePort source, KnowledgeResourceId id)
            throws KnowledgeSourceException;

    /** Liest direkt aus dem Quellen-Port. */
    /**
     * Holt die Ressource gezielt neu aus der Quelle, an jeder Zwischenkopie vorbei (corenth {@code REFRESH_EXTERNAL});
     * nötig, wenn ihre Revision unbekannt ist oder sich laut Suche geändert hat. Standard: wie {@link #read}.
     */
    default KnowledgeDocument refresh(SourceAddress owner, KnowledgeSourcePort source, KnowledgeResourceId id)
            throws KnowledgeSourceException {
        return read(owner, source, id);
    }

    static KnowledgeDocumentReader direct() {
        return new KnowledgeDocumentReader() {
            @Override
            public KnowledgeDocument read(SourceAddress owner, KnowledgeSourcePort source,
                                          KnowledgeResourceId id) throws KnowledgeSourceException {
                return source.load(id);
            }
        };
    }
}
