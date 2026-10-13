package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceFile;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Gibt die unter Wissensquellen abgehakten Bereiche (Confluence-Spaces) bei jedem {@code discover} und jeder
 * Suche an die Quelle weiter, gelesen aus dem Anwendungszustand. So wirkt ein Häkchen beim nächsten Lauf, ohne die Quelle neu
 * anzubinden, und Kern wie Oberfläche kennen den Quelltyp nicht.
 */
final class PartitionSelectingSourceProvider implements KnowledgeSourceProvider, AddressableKnowledgeSourceProvider {

    private final KnowledgeSourceProvider delegate;
    private final SourcePartitionSelection selection;

    PartitionSelectingSourceProvider(KnowledgeSourceProvider delegate, SourcePartitionSelection selection) {
        if (delegate == null || selection == null) {
            throw new IllegalArgumentException("delegate and selection must not be null");
        }
        this.delegate = delegate;
        this.selection = selection;
    }

    @Override
    public KnowledgeSourceType type() {
        return delegate.type();
    }

    @Override
    public Pattern addressPattern() {
        return delegate instanceof AddressableKnowledgeSourceProvider
                ? ((AddressableKnowledgeSourceProvider) delegate).addressPattern() : null;
    }

    @Override
    public List<String> validate(SourceSettings settings) {
        return delegate.validate(settings);
    }

    @Override
    public String locator(SourceSettings settings) {
        return delegate.locator(settings);
    }

    @Override
    public SourceScope scope(SourceSettings settings) {
        return delegate.scope(settings);
    }

    @Override
    public KnowledgeSourcePort open(SourceAddress sourceId, SourceSettings settings) {
        KnowledgeSourcePort port = delegate.open(sourceId, settings);
        return delegate.type().partitionsLabel().isEmpty() ? port : new Selecting(port);
    }

    @Override
    public String toString() {
        return "PartitionSelectingSourceProvider[" + delegate + "]";
    }

    private final class Selecting implements SearchableKnowledgeSource, PageViewSource, AddressableKnowledgeSource {

        private final KnowledgeSourcePort port;

        Selecting(KnowledgeSourcePort port) {
            this.port = port;
        }

        @Override
        public SourceAddress sourceId() {
            return port.sourceId();
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            if (!scope.selectionApplies()) {
                return port.discover(scope); // Klick auf einen Bereich: Blättern, keine Suche (wie MainframeMate)
            }
            Set<String> excluded = new LinkedHashSet<String>(scope.excludedPartitions());
            excluded.addAll(selection.excluded(port.sourceId()));
            return port.discover(scope.withExcludedPartitions(excluded));
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return port.load(resourceId);
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return port.discoverLinks(resourceId);
        }

        /** Wie die gehüllte Quelle; ohne eigene Suche {@code UNSUPPORTED} wie bei den übrigen Hüllen. */
        @Override
        public List<SourceSearchHit> search(SourceQuery query) throws KnowledgeSourceException {
            if (!(port instanceof SearchableKnowledgeSource)) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                        "Quelle " + port.sourceId().value() + " hat keine eigene Suche");
            }
            Set<String> excluded = new LinkedHashSet<String>(query.excludedPartitions());
            excluded.addAll(selection.excluded(port.sourceId()));
            return ((SearchableKnowledgeSource) port).search(query.withExcludedPartitions(excluded));
        }

        @Override
        public List<SourcePartition> partitions() throws KnowledgeSourceException {
            return port.partitions();
        }

        @Override
        public PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException {
            return SourceCapabilities.pageView(port, page);
        }

        @Override

        public DownloadAccess downloadAccess() {

            return SourceCapabilities.downloadAccess(port);

        }


        @Override
        public KnowledgeResourceId resolveLink(String address) throws KnowledgeSourceException {
            return SourceCapabilities.resolveLink(port, address);
        }

        @Override
        public Pattern addressPattern() {
            return SourceCapabilities.addressPattern(port);
        }

        @Override
        public KnowledgeResourceId resolveAddress(String address) throws KnowledgeSourceException {
            return SourceCapabilities.resolveAddress(port, address);
        }

        @Override
        public byte[] loadViewImage(String src) throws KnowledgeSourceException {
            return SourceCapabilities.loadViewImage(port, src);
        }

        @Override
        public byte[] loadAttachment(PageView.Attachment attachment) throws KnowledgeSourceException {
            return SourceCapabilities.loadAttachment(port, attachment);
        }

        @Override
        public byte[] loadAttachmentPreview(PageView.Attachment attachment, PageView.Preview preview)
                throws KnowledgeSourceException {
            return SourceCapabilities.loadAttachmentPreview(port, attachment, preview);
        }

        @Override
        public List<KnowledgeResource> attachmentsOf(KnowledgeResourceId page) throws KnowledgeSourceException {
            return SourceCapabilities.attachmentsOf(port, page);
        }

        @Override
        public boolean isAttachment(KnowledgeResourceId id) {
            return SourceCapabilities.isAttachment(port, id);
        }

        @Override
        public SourceFile loadAttachmentFile(KnowledgeResourceId attachment) throws KnowledgeSourceException {
            return SourceCapabilities.loadAttachmentFile(port, attachment);
        }

        @Override
        public String toString() {
            return port.toString();
        }
    }
}
