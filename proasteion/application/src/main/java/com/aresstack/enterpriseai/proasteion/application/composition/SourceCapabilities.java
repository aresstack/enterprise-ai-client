package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SourceFile;

import java.util.List;

/**
 * Reicht die optionale Fähigkeit {@link PageViewSource} (Lese-Ansicht) durch die Hüllen der Composition Root
 * (Protokoll, Bereichsauswahl, KeePass-Aufschub); eine Quelle ohne die Fähigkeit meldet {@code UNSUPPORTED}, wie bei
 * der Suche.
 */
final class SourceCapabilities {

    private SourceCapabilities() {
    }

    static PageView pageView(KnowledgeSourcePort port, KnowledgeResourceId page) throws KnowledgeSourceException {
        if (!(port instanceof PageViewSource)) {
            throw unsupported(port, "keine Lese-Ansicht");
        }
        return ((PageViewSource) port).pageView(page);
    }

    static byte[] loadViewImage(KnowledgeSourcePort port, String src) throws KnowledgeSourceException {
        if (!(port instanceof PageViewSource)) {
            throw unsupported(port, "keine Lese-Ansicht");
        }
        return ((PageViewSource) port).loadViewImage(src);
    }

    static byte[] loadAttachment(KnowledgeSourcePort port, PageView.Attachment attachment)
            throws KnowledgeSourceException {
        if (!(port instanceof PageViewSource)) {
            throw unsupported(port, "keine Lese-Ansicht");
        }
        return ((PageViewSource) port).loadAttachment(attachment);
    }

    static byte[] loadAttachmentPreview(KnowledgeSourcePort port, PageView.Attachment attachment,
                                        PageView.Preview preview) throws KnowledgeSourceException {
        if (!(port instanceof PageViewSource)) {
            throw unsupported(port, "keine Lese-Ansicht");
        }
        return ((PageViewSource) port).loadAttachmentPreview(attachment, preview);
    }

    static List<KnowledgeResource> attachmentsOf(KnowledgeSourcePort port, KnowledgeResourceId page)
            throws KnowledgeSourceException {
        if (!(port instanceof PageViewSource)) {
            throw unsupported(port, "keine Anhänge");
        }
        return ((PageViewSource) port).attachmentsOf(page);
    }

    static KnowledgeResourceId resolveLink(KnowledgeSourcePort port, String address)
            throws KnowledgeSourceException {
        return port instanceof PageViewSource ? ((PageViewSource) port).resolveLink(address) : null;
    }

    static boolean isAttachment(KnowledgeSourcePort port, KnowledgeResourceId id) {
        return port instanceof PageViewSource && ((PageViewSource) port).isAttachment(id);
    }

    static SourceFile loadAttachmentFile(KnowledgeSourcePort port, KnowledgeResourceId attachment)
            throws KnowledgeSourceException {
        if (!(port instanceof PageViewSource)) {
            throw unsupported(port, "keine Anhänge");
        }
        return ((PageViewSource) port).loadAttachmentFile(attachment);
    }

    static DownloadAccess downloadAccess(KnowledgeSourcePort port) {
        return port instanceof PageViewSource ? ((PageViewSource) port).downloadAccess() : DownloadAccess.OPEN;
    }

    private static KnowledgeSourceException unsupported(KnowledgeSourcePort port, String what) {
        return new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                "Quelle " + port.sourceId().value() + " hat " + what);
    }
}
