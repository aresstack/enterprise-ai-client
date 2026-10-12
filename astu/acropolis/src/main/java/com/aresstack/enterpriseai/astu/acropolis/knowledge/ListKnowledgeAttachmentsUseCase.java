package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.PageViewSource;

import java.util.List;
import java.util.function.Supplier;

/**
 * Die Dateianhänge einer Seite (Name, Typ, Größe, Revision, ID), ohne sie zu laden: für {@code knowledge_attachments}.
 * Gelesen wird ein Anhang danach wie jede Ressource über {@link LoadKnowledgeDocumentUseCase}.
 */
public final class ListKnowledgeAttachmentsUseCase {

    private final Supplier<KnowledgeSourceCatalog> catalog;

    /** @param catalog die angebundenen Quellen je Aufruf */
    public ListKnowledgeAttachmentsUseCase(Supplier<KnowledgeSourceCatalog> catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog ist Pflicht");
        }
        this.catalog = catalog;
    }

    /**
     * @throws KnowledgeSourceException {@code UNSUPPORTED}, wenn keine Quelle Anhänge zu dieser Seite kennt; sonst der
     *                                  Fehler der zuständigen Quelle
     */
    public List<KnowledgeResource> attachmentsOf(KnowledgeResourceId page) throws KnowledgeSourceException {
        if (page == null) {
            throw new IllegalArgumentException("page ist Pflicht");
        }
        KnowledgeSourceCatalog current = catalog.get();
        for (KnowledgeSourceRegistration registration : current.registrations()) {
            if (!(registration.port() instanceof PageViewSource)) {
                continue;
            }
            try {
                return ((PageViewSource) registration.port()).attachmentsOf(page);
            } catch (KnowledgeSourceException e) {
                if (e.kind() != KnowledgeSourceException.Kind.UNSUPPORTED) {
                    throw e;
                }
            }
        }
        throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                "keine konfigurierte Quelle kennt Anhänge von " + page);
    }
}
