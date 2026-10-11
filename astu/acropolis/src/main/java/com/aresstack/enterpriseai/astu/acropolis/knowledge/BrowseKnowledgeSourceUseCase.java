package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Use Case „Wissensquelle lesen“ für den Lesebereich (wie der Lese-Tab in MainframeMate): listet die Ressourcen einer
 * Quelle im konfigurierten Umfang (höchstens {@link #MAX_LISTED}) und lädt eine Ressource zum Lesen über einen
 * {@link KnowledgeDocumentReader}. Kennt Quellen nur über ihren Port; blockiert für die Dauer des Aufrufs.
 * Quellen mit {@link PageViewSource} liefern zusätzlich ihre Lese-Ansicht (HTML mit Bildern).
 */
public final class BrowseKnowledgeSourceUseCase {

    /** Mehr Einträge zeigt die Übersicht einer Quelle nicht; die Suche findet den Rest. */
    public static final int MAX_LISTED = 200;

    private final KnowledgeDocumentReader reader;

    public BrowseKnowledgeSourceUseCase(KnowledgeDocumentReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }
        this.reader = reader;
    }

    /** Die Ressourcen der Quelle im Umfang {@code scope}, ohne Inhalt, höchstens {@link #MAX_LISTED}. */
    public List<KnowledgeResource> list(KnowledgeSourcePort source, SourceScope scope) throws KnowledgeSourceException {
        if (source == null || scope == null) {
            throw new IllegalArgumentException("source and scope must not be null");
        }
        SourceScope.Builder builder = SourceScope.builder().startPoints(scope.startPoints()).maxDepth(scope.maxDepth())
                .maxResources(Math.min(MAX_LISTED, scope.maxResources()));
        SourceScope limited = (scope.selectionApplies() ? builder : builder.ignoringSelection()).build();
        List<KnowledgeResource> resources = source.discover(limited);
        return Collections.unmodifiableList(new ArrayList<KnowledgeResource>(
                resources.size() > MAX_LISTED ? resources.subList(0, MAX_LISTED) : resources));
    }

    /**
     * Der aktuelle Text der Ressource aus ihrer Quelle. Der Drawer liest über den Priority-Lader des
     * {@link KnowledgePrefetchService}, der die Seite danach indexiert.
     */
    public KnowledgeDocument read(KnowledgeSourcePort source, KnowledgeResourceId id) throws KnowledgeSourceException {
        if (source == null || id == null) {
            throw new IllegalArgumentException("source and id must not be null");
        }
        return reader.read(source.sourceId(), source, id);
    }

    /**
     * Die Lese-Ansicht der Seite, wenn die Quelle eine hat; sonst {@code null} (dann zeigt der Lesebereich den Text).
     */
    public PageView view(KnowledgeSourcePort source, KnowledgeResourceId id) throws KnowledgeSourceException {
        if (source == null || id == null) {
            throw new IllegalArgumentException("source and id must not be null");
        }
        if (!(source instanceof PageViewSource)) {
            return null;
        }
        try {
            return ((PageViewSource) source).pageView(id);
        } catch (KnowledgeSourceException e) {
            if (e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED) {
                return null; // gehüllte Quelle ohne Lese-Ansicht
            }
            throw e;
        }
    }

    /** Die Bytes eines Bildes aus {@link PageView#images()} über die Quelle (mit deren Anmeldung). */
    public byte[] viewImage(KnowledgeSourcePort source, String src) throws KnowledgeSourceException {
        if (!(source instanceof PageViewSource)) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "Quelle ohne Lese-Ansicht");
        }
        return ((PageViewSource) source).loadViewImage(src);
    }
}
