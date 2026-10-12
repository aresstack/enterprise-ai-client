package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;

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

    static DownloadAccess downloadAccess(KnowledgeSourcePort port) {
        return port instanceof PageViewSource ? ((PageViewSource) port).downloadAccess() : DownloadAccess.OPEN;
    }

    private static KnowledgeSourceException unsupported(KnowledgeSourcePort port, String what) {
        return new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                "Quelle " + port.sourceId().value() + " hat " + what);
    }
}
