package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PageNavigationSource;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.SourceFile;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reicht die optionalen Fähigkeiten {@link PageViewSource} (Lese-Ansicht) und {@link PageNavigationSource} (Kompass) durch die Hüllen der Composition Root
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

    static PagePosition position(KnowledgeSourcePort port, KnowledgeResourceId page) throws KnowledgeSourceException {
        if (!(port instanceof PageNavigationSource)) {
            throw unsupported(port, "keine Seitenhierarchie");
        }
        return ((PageNavigationSource) port).position(page);
    }

    static NavigationChildren children(KnowledgeSourcePort port, NavigationNode parent, String cursor)
            throws KnowledgeSourceException {
        if (!(port instanceof PageNavigationSource)) {
            throw unsupported(port, "keine Seitenhierarchie");
        }
        return ((PageNavigationSource) port).children(parent, cursor);
    }

    static CrossReferences crossReferences(KnowledgeSourcePort port, KnowledgeResourceId page)
            throws KnowledgeSourceException {
        if (!(port instanceof PageNavigationSource)) {
            throw unsupported(port, "keine Querverweise");
        }
        return ((PageNavigationSource) port).crossReferences(page);
    }

    static Pattern addressPattern(KnowledgeSourcePort port) {
        return port instanceof AddressableKnowledgeSource
                ? ((AddressableKnowledgeSource) port).addressPattern() : null;
    }

    static KnowledgeResourceId resolveAddress(KnowledgeSourcePort port, String address)
            throws KnowledgeSourceException {
        return port instanceof AddressableKnowledgeSource
                ? ((AddressableKnowledgeSource) port).resolveAddress(address) : null;
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
