package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
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

    /**
     * Die Seite der Quelle hinter einem Link ihrer Lese-Ansicht ({@link PageViewSource#resolveLink}); {@code null}, wenn
     * die Quelle keine Lese-Ansicht hat oder der Link nicht zu ihr gehört.
     */
    public KnowledgeResourceId resolveLink(KnowledgeSourcePort source, String address)
            throws KnowledgeSourceException {
        if (!(source instanceof PageViewSource) || address == null || address.trim().isEmpty()) {
            return null;
        }
        try {
            return ((PageViewSource) source).resolveLink(address.trim());
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

    /**
     * Ob Dateien der Quelle gerade ohne Einmalcode laden ({@link DownloadAccess}); eine Quelle ohne Lese-Ansicht ist
     * {@link DownloadAccess#OPEN}.
     */
    public DownloadAccess downloadAccess(KnowledgeSourcePort source) {
        return source instanceof PageViewSource ? ((PageViewSource) source).downloadAccess() : DownloadAccess.OPEN;
    }

    /** Die Originaldatei eines Anhangs aus {@link PageView#attachments()} über die Quelle (mit deren Anmeldung). */
    public byte[] attachment(KnowledgeSourcePort source, PageView.Attachment attachment)
            throws KnowledgeSourceException {
        if (attachment == null) {
            throw new IllegalArgumentException("attachment must not be null");
        }
        if (!(source instanceof PageViewSource)) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "Quelle ohne Lese-Ansicht");
        }
        return ((PageViewSource) source).loadAttachment(attachment);
    }

    /**
     * Die erste Vorschau des Anhangs, die die Quelle liefern kann, in der Reihenfolge {@code order} (nur Arten aus
     * {@link PageView.Attachment#previews()}); gibt es keine, der Fehler der letzten Art.
     */
    public byte[] attachmentPreview(KnowledgeSourcePort source, PageView.Attachment attachment,
                                    List<PageView.Preview> order) throws KnowledgeSourceException {
        if (attachment == null || order == null) {
            throw new IllegalArgumentException("attachment and order must not be null");
        }
        if (!(source instanceof PageViewSource)) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "Quelle ohne Lese-Ansicht");
        }
        KnowledgeSourceException last = null;
        for (PageView.Preview preview : order) {
            if (!attachment.previews().contains(preview)) {
                continue;
            }
            try {
                return ((PageViewSource) source).loadAttachmentPreview(attachment, preview);
            } catch (KnowledgeSourceException e) {
                if (e.kind() == KnowledgeSourceException.Kind.ACCESS_DENIED) {
                    throw e; // die nächste Art scheitert genauso
                }
                last = e;
            }
        }
        throw last != null ? last : new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                "Die Quelle bietet für " + attachment.fileName() + " keine Vorschau an");
    }
}
