package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

/**
 * Use Case "Wissensdokument laden": holt den vollständigen, aktuellen Text einer Ressource aus ihrer Quelle
 * (nicht aus dem Index, der nur eine wiederherstellbare Projektion ist).
 *
 * <p>Welche Quelle eine ID kennt, entscheiden die Quellen selbst: Jeder {@link KnowledgeSourcePort} lehnt eine
 * fremde ID laut Vertrag mit {@link KnowledgeSourceException.Kind#UNSUPPORTED} ab. Ohne explizite Quelle werden
 * deshalb die konfigurierten Quellen in Katalogreihenfolge gefragt; die erste, die die ID nicht als fremd ablehnt,
 * entscheidet (Dokument oder Fehler). Kennt keine Quelle die ID, ist das Ergebnis {@code UNSUPPORTED}.
 *
 * <p>Kennt nur die Quellen über den Port; keine Adapter-, Index- oder Security-Typen. Blockiert für die Dauer des
 * Ladens.
 */
public final class LoadKnowledgeDocumentUseCase {

    private final KnowledgeSourceCatalog catalog;

    public LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog ist Pflicht");
        }
        this.catalog = catalog;
    }

    public KnowledgeSourceCatalog catalog() {
        return catalog;
    }

    /**
     * Lädt die Ressource aus der ersten konfigurierten Quelle, die ihre ID kennt.
     *
     * @throws KnowledgeSourceException {@code UNSUPPORTED}, wenn keine Quelle die ID kennt; sonst der Fehler der
     *                                  zuständigen Quelle ({@code NOT_FOUND}, {@code UNAVAILABLE}, ...)
     */
    public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId ist Pflicht");
        }
        for (KnowledgeSourcePort source : catalog.ports()) {
            try {
                return source.load(resourceId);
            } catch (KnowledgeSourceException e) {
                if (e.kind() != KnowledgeSourceException.Kind.UNSUPPORTED) {
                    throw e;
                }
            }
        }
        throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                "keine konfigurierte Quelle kennt " + resourceId);
    }

    /**
     * Lädt die Ressource aus genau dieser Quelle.
     *
     * @throws KnowledgeSourceException {@code NOT_FOUND}, wenn die Quelle nicht konfiguriert ist; sonst der Fehler
     *                                  der Quelle
     */
    public KnowledgeDocument load(KnowledgeResourceId resourceId, KnowledgeSourceId sourceId)
            throws KnowledgeSourceException {
        if (resourceId == null || sourceId == null) {
            throw new IllegalArgumentException("resourceId und sourceId sind Pflicht");
        }
        KnowledgeSourceRegistration registration = catalog.find(sourceId);
        if (registration == null) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND,
                    "Quelle " + sourceId + " ist nicht konfiguriert");
        }
        return registration.port().load(resourceId);
    }
}
