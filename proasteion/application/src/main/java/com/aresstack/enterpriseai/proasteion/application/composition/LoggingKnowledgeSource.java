package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PageNavigationSource;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.OneTimeCodeRequiredException;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceFile;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.List;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hüllt eine Wissensquelle und protokolliert jeden Fehlschlag mit vollständiger Ursachenkette, bevor er an den
 * Use Case geht. {@code IndexKnowledgeUseCase} reduziert eine {@link KnowledgeSourceException} zu einem Text für
 * den Bericht; ohne diese Hülle stünde im Protokoll bei einem TLS- oder Proxy-Fehler der Quelle nur „Quelle nicht
 * lesbar“ ohne den eigentlichen Grund. Secrets gehören laut Vorgabe in keine Ausnahmemeldung, deshalb darf die
 * Meldung der Quelle unverändert ins Protokoll.
 */
public final class LoggingKnowledgeSource implements SearchableKnowledgeSource, PageViewSource, AddressableKnowledgeSource, PageNavigationSource {

    private static final Logger LOG = Logger.getLogger(LoggingKnowledgeSource.class.getName());

    private final KnowledgeSourcePort delegate;

    LoggingKnowledgeSource(KnowledgeSourcePort delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    /** Die gehüllte Quelle (Tests, Diagnose). */
    public KnowledgeSourcePort delegate() {
        return delegate;
    }

    @Override
    public SourceAddress sourceId() {
        return delegate.sourceId();
    }

    @Override
    public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
        try {
            return delegate.discover(scope);
        } catch (KnowledgeSourceException e) {
            throw logged("discover " + scope, e);
        } catch (RuntimeException e) {
            throw unexpected("discover " + scope, e);
        }
    }

    @Override
    public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        try {
            return delegate.load(resourceId);
        } catch (KnowledgeSourceException e) {
            throw logged("load " + value(resourceId), e);
        } catch (RuntimeException e) {
            throw unexpected("load " + value(resourceId), e);
        }
    }

    @Override
    public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        try {
            return delegate.discoverLinks(resourceId);
        } catch (KnowledgeSourceException e) {
            throw logged("discoverLinks " + value(resourceId), e);
        } catch (RuntimeException e) {
            throw unexpected("discoverLinks " + value(resourceId), e);
        }
    }

    /** Reicht die Suche der Quelle durch; eine Quelle ohne eigene Suche meldet {@code UNSUPPORTED}. */
    @Override
    public List<SourceSearchHit> search(SourceQuery query) throws KnowledgeSourceException {
        if (!(delegate instanceof SearchableKnowledgeSource)) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                    "Quelle " + sourceId().value() + " hat keine eigene Suche");
        }
        try {
            return ((SearchableKnowledgeSource) delegate).search(query);
        } catch (KnowledgeSourceException e) {
            throw logged("search", e);
        } catch (RuntimeException e) {
            throw unexpected("search", e);
        }
    }

    @Override
    public List<SourcePartition> partitions() throws KnowledgeSourceException {
        try {
            return delegate.partitions();
        } catch (KnowledgeSourceException e) {
            throw logged("partitions", e);
        } catch (RuntimeException e) {
            throw unexpected("partitions", e);
        }
    }

    @Override
    public PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.pageView(delegate, page);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e : logged("pageView " + value(page), e);
        } catch (RuntimeException e) {
            throw unexpected("pageView " + value(page), e);
        }
    }

    @Override

    public DownloadAccess downloadAccess() {

        return SourceCapabilities.downloadAccess(delegate);

    }


    @Override
    public Pattern addressPattern() {
        return SourceCapabilities.addressPattern(delegate);
    }

    @Override
    public KnowledgeResourceId resolveAddress(String address) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.resolveAddress(delegate, address);
        } catch (KnowledgeSourceException e) {
            throw logged("resolveAddress", e);
        } catch (RuntimeException e) {
            throw unexpected("resolveAddress", e);
        }
    }

    @Override
    public PagePosition position(KnowledgeResourceId page) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.position(delegate, page);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e : logged("position " + value(page), e);
        } catch (RuntimeException e) {
            throw unexpected("position " + value(page), e);
        }
    }

    @Override
    public NavigationChildren children(NavigationNode parent, String cursor) throws KnowledgeSourceException {
        String what = "children " + (parent == null ? "null" : parent.key());
        try {
            return SourceCapabilities.children(delegate, parent, cursor);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e : logged(what, e);
        } catch (RuntimeException e) {
            throw unexpected(what, e);
        }
    }

    @Override
    public CrossReferences crossReferences(KnowledgeResourceId page) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.crossReferences(delegate, page);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e
                    : logged("crossReferences " + value(page), e);
        } catch (RuntimeException e) {
            throw unexpected("crossReferences " + value(page), e);
        }
    }

    @Override
    public byte[] loadViewImage(String src) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.loadViewImage(delegate, src);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e : logged("loadViewImage " + src, e);
        } catch (RuntimeException e) {
            throw unexpected("loadViewImage " + src, e);
        }
    }

    @Override
    public byte[] loadAttachment(PageView.Attachment attachment) throws KnowledgeSourceException {
        String what = "loadAttachment " + (attachment == null ? "null" : attachment.id());
        try {
            return SourceCapabilities.loadAttachment(delegate, attachment);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e : logged(what, e);
        } catch (RuntimeException e) {
            throw unexpected(what, e);
        }
    }

    @Override
    public List<KnowledgeResource> attachmentsOf(KnowledgeResourceId page) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.attachmentsOf(delegate, page);
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e : logged("attachmentsOf " + value(page), e);
        } catch (RuntimeException e) {
            throw unexpected("attachmentsOf " + value(page), e);
        }
    }

    @Override
    public boolean isAttachment(KnowledgeResourceId id) {
        return SourceCapabilities.isAttachment(delegate, id);
    }

    /** A one-time code request is expected and goes to the caller without a warning. */
    @Override
    public SourceFile loadAttachmentFile(KnowledgeResourceId attachment) throws KnowledgeSourceException {
        try {
            return SourceCapabilities.loadAttachmentFile(delegate, attachment);
        } catch (OneTimeCodeRequiredException e) {
            throw e;
        } catch (KnowledgeSourceException e) {
            throw e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED ? e
                    : logged("loadAttachmentFile " + value(attachment), e);
        } catch (RuntimeException e) {
            throw unexpected("loadAttachmentFile " + value(attachment), e);
        }
    }

    /** Fehlende Vorschauen sind normal (nicht umwandelbar, noch nicht erzeugt) und werden nur durchgereicht. */
    @Override
    public byte[] loadAttachmentPreview(PageView.Attachment attachment, PageView.Preview preview)
            throws KnowledgeSourceException {
        try {
            return SourceCapabilities.loadAttachmentPreview(delegate, attachment, preview);
        } catch (RuntimeException e) {
            throw unexpected("loadAttachmentPreview " + (attachment == null ? "null" : attachment.id()), e);
        }
    }

    private KnowledgeSourceException logged(String operation, KnowledgeSourceException e) {
        LOG.log(Level.WARNING, "Quelle " + sourceId().value() + ": " + operation + " fehlgeschlagen (" + e.kind()
                + "): " + e.getMessage(), e);
        return e;
    }

    private RuntimeException unexpected(String operation, RuntimeException e) {
        LOG.log(Level.SEVERE, "Quelle " + sourceId().value() + ": " + operation + " mit unerwartetem Fehler", e);
        return e;
    }

    private static String value(KnowledgeResourceId resourceId) {
        return resourceId == null ? "null" : resourceId.value();
    }

    @Override
    public String toString() {
        return "Logging(" + delegate + ")";
    }
}
