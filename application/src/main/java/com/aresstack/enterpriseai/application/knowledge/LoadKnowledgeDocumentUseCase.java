package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

/**
 * Use Case "Wissensdokument laden": holt den vollständigen, aktuellen Text einer Ressource aus ihrer Quelle
 * (nicht aus dem Index, der nur eine wiederherstellbare Projektion ist).
 *
 * <p><b>Der Index führt</b> ({@link #LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog, KnowledgeIndexPort,
 * EmbeddingModelIdentity) mit Index}): Geladen wird nur, was im Namespace der konfigurierten Embedding-Welt
 * indexiert ist ({@link KnowledgeIndexPort#resourceIds}); die Quelle, unter der die ID indexiert ist, liefert den
 * Text. Eine nicht indexierte ID ergibt {@link KnowledgeDocumentNotIndexedException}, ohne dass eine Quelle gefragt
 * wird. So bleibt die Wissensbasis auf den konfigurierten Scope der Quellen beschränkt, auch wenn eine Quelle
 * technisch jede ihrer IDs laden könnte.
 *
 * <p>Ohne Index ({@link #LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog)}) entscheiden die Quellen selbst,
 * welche ID sie kennen: Jeder {@link KnowledgeSourcePort} lehnt eine fremde ID laut Vertrag mit
 * {@link KnowledgeSourceException.Kind#UNSUPPORTED} ab. Die konfigurierten Quellen werden in Katalogreihenfolge
 * gefragt; die erste, die die ID nicht als fremd ablehnt, entscheidet (Dokument oder Fehler). Kennt keine Quelle die
 * ID, ist das Ergebnis {@code UNSUPPORTED}.
 *
 * <p>Gelesen wird über einen {@link KnowledgeDocumentReader}: produktiv der vermittelte Zugriff nach corenth
 * ({@link #withReader}), sonst direkt aus dem Quellen-Port.
 *
 * <p>Kennt Quellen und Index nur über ihre Ports; keine Adapter- oder Security-Typen. Blockiert für die Dauer des
 * Ladens.
 */
public final class LoadKnowledgeDocumentUseCase {

    private final KnowledgeSourceCatalog catalog;
    private final KnowledgeIndexPort index;
    private final EmbeddingModelIdentity space;
    private final KnowledgeDocumentReader reader;

    /** Ohne Index: Die Quellen entscheiden, welche ID sie kennen; jede akzeptierte ID wird geladen. */
    public LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog catalog) {
        this(catalog, null, null, false, KnowledgeDocumentReader.direct());
    }

    /**
     * Der Index führt: Nur im Namespace {@code space} indexierte Ressourcen werden geladen.
     *
     * @param index Wissensindex, in dem die Ressource liegen muss
     * @param space Namespace (Embedding-Welt) aus der Konfiguration, derselbe wie bei der Indexierung
     */
    public LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog catalog, KnowledgeIndexPort index,
                                        EmbeddingModelIdentity space) {
        this(catalog, index, space, true, KnowledgeDocumentReader.direct());
    }

    private LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog catalog, KnowledgeIndexPort index,
                                         EmbeddingModelIdentity space, boolean gated, KnowledgeDocumentReader reader) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog ist Pflicht");
        }
        if (gated && (index == null || space == null)) {
            throw new IllegalArgumentException("index und space sind Pflicht");
        }
        this.catalog = catalog;
        this.index = index;
        this.space = space;
        this.reader = reader;
    }

    /** Derselbe Use Case, der über {@code value} liest (z. B. den vermittelten Zugriff). */
    public LoadKnowledgeDocumentUseCase withReader(KnowledgeDocumentReader value) {
        if (value == null) {
            throw new IllegalArgumentException("reader ist Pflicht");
        }
        return new LoadKnowledgeDocumentUseCase(catalog, index, space, index != null, value);
    }

    public KnowledgeSourceCatalog catalog() {
        return catalog;
    }

    /** {@code true}, wenn nur indexierte Ressourcen geladen werden. */
    public boolean isIndexGated() {
        return index != null;
    }

    /**
     * Lädt die Ressource aus ihrer Quelle: mit Index aus der Quelle, unter der sie indexiert ist; ohne Index aus der
     * ersten konfigurierten Quelle, die ihre ID kennt.
     *
     * @throws KnowledgeDocumentNotIndexedException mit Index, wenn die Ressource nicht indexiert ist
     * @throws KnowledgeSourceException             {@code UNSUPPORTED}, wenn ohne Index keine Quelle die ID kennt;
     *                                              sonst der Fehler der zuständigen Quelle ({@code NOT_FOUND},
     *                                              {@code UNAVAILABLE}, ...)
     */
    public KnowledgeDocument load(KnowledgeResourceId resourceId)
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId ist Pflicht");
        }
        if (index != null) {
            KnowledgeSourceId owner = indexedSourceOf(resourceId);
            if (owner == null) {
                throw new KnowledgeDocumentNotIndexedException(resourceId, null);
            }
            return reader.read(owner, catalog.find(owner).port(), resourceId);
        }
        for (KnowledgeSourceRegistration registration : catalog.registrations()) {
            try {
                return reader.read(registration.sourceId(), registration.port(), resourceId);
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
     * @throws KnowledgeDocumentNotIndexedException mit Index, wenn die Ressource dort nicht unter dieser Quelle
     *                                              indexiert ist
     * @throws KnowledgeSourceException             {@code NOT_FOUND}, wenn die Quelle nicht konfiguriert ist; sonst
     *                                              der Fehler der Quelle
     */
    public KnowledgeDocument load(KnowledgeResourceId resourceId, KnowledgeSourceId sourceId)
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        if (resourceId == null || sourceId == null) {
            throw new IllegalArgumentException("resourceId und sourceId sind Pflicht");
        }
        KnowledgeSourceRegistration registration = catalog.find(sourceId);
        if (registration == null) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND,
                    "Quelle " + sourceId + " ist nicht konfiguriert");
        }
        if (index != null && !index.resourceIds(space, sourceId).contains(resourceId)) {
            throw new KnowledgeDocumentNotIndexedException(resourceId, sourceId);
        }
        return reader.read(sourceId, registration.port(), resourceId);
    }

    /** Die konfigurierte Quelle, unter der die Ressource im Namespace indexiert ist, oder {@code null}. */
    private KnowledgeSourceId indexedSourceOf(KnowledgeResourceId resourceId) {
        for (KnowledgeSourceId sourceId : catalog.ids()) {
            if (index.resourceIds(space, sourceId).contains(resourceId)) {
                return sourceId;
            }
        }
        return null;
    }
}
