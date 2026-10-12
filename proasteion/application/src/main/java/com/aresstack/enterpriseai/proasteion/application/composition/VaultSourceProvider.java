package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.config.VaultLocations;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceSettingField;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.DownloadAccess;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceFile;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Adresse aus KeePass: Ist bei einer Quelle ein KeePass-Eintrag gewählt und ein Feld, das der Adapter als
 * „aus dem Tresor“ markiert ({@link SourceSettingField#vaultValue()}), leer, nimmt die Quelle die URL des
 * Eintrags (wie MainframeMate). Ein eingetragener Wert überschreibt sie. Die Prüfung verlangt das Feld dann
 * nicht; gelesen wird KeePass erst beim ersten Zugriff der Quelle, nicht beim Start. Gilt für alle Quelltypen,
 * ohne dass Kern oder Oberfläche den Typ kennen.
 */
final class VaultSourceProvider implements KnowledgeSourceProvider {

    private final KnowledgeSourceProvider delegate;
    private final SecretProvider secrets;

    VaultSourceProvider(KnowledgeSourceProvider delegate, SecretProvider secrets) {
        if (delegate == null || secrets == null) {
            throw new IllegalArgumentException("delegate and secrets must not be null");
        }
        this.delegate = delegate;
        this.secrets = secrets;
    }

    @Override
    public KnowledgeSourceType type() {
        return delegate.type();
    }

    @Override
    public List<String> validate(SourceSettings settings) {
        List<String> problems = delegate.validate(settings);
        List<SourceSettingField> fromVault = fieldsFromVault(settings);
        if (fromVault.isEmpty()) {
            return problems;
        }
        List<String> remaining = new ArrayList<String>();
        for (String problem : problems) {
            boolean covered = false;
            for (SourceSettingField field : fromVault) {
                covered |= problem.startsWith(field.label() + ": ");
            }
            if (!covered) {
                remaining.add(problem);
            }
        }
        return remaining;
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
        if (fieldsFromVault(settings).isEmpty()) {
            return delegate.open(sourceId, settings);
        }
        return new Deferred(sourceId, settings);
    }

    /** Die leeren Felder, die ihren Wert aus dem gewählten KeePass-Eintrag bekommen. */
    private List<SourceSettingField> fieldsFromVault(SourceSettings settings) {
        List<SourceSettingField> fields = new ArrayList<SourceSettingField>();
        if (vaultRef(settings) == null) {
            return fields;
        }
        for (SourceSettingField field : delegate.type().fields()) {
            String value = settings.get(field.key());
            if (field.vaultValue() != SourceSettingField.VaultValue.NONE
                    && (value == null || value.trim().isEmpty())) {
                fields.add(field);
            }
        }
        return fields;
    }

    private SecretRef vaultRef(SourceSettings settings) {
        for (SourceSettingField field : delegate.type().fields()) {
            if (field.kind() == SourceSettingField.Kind.SECRET_REF) {
                String value = settings.get(field.key());
                if (value != null && !value.trim().isEmpty()) {
                    SecretRef ref = SecretRef.of(value);
                    return AskedSecretProvider.isAsked(ref) ? null : ref;
                }
            }
        }
        return null;
    }

    /** Die Einstellungen mit den Werten aus KeePass. */
    private SourceSettings completed(SourceSettings settings) throws KnowledgeSourceException {
        SecretRef ref = vaultRef(settings);
        String location;
        try {
            location = secrets.locationOf(ref);
        } catch (SecretUnavailableException e) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE,
                    "Adresse aus KeePass nicht lesbar: " + e.getMessage(), e);
        }
        if (location == null || location.trim().isEmpty()) {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE,
                    "Der KeePass-Eintrag „" + VaultLocations.title(ref) + "“ hat keine URL; bitte dort eintragen "
                            + "oder unter „Erweitert“ angeben");
        }
        SourceSettings filled = settings;
        for (SourceSettingField field : fieldsFromVault(settings)) {
            filled = filled.with(field.key(), field.vaultValue() == SourceSettingField.VaultValue.HOST
                    ? host(location) : location.trim());
        }
        return filled;
    }

    static String host(String location) {
        try {
            String host = URI.create(location.trim()).getHost();
            return host == null ? location.trim() : host;
        } catch (IllegalArgumentException e) {
            return location.trim();
        }
    }

    @Override
    public String toString() {
        return "VaultSourceProvider[" + delegate.type().id() + "]";
    }

    /** Öffnet die eigentliche Quelle beim ersten Zugriff, nachdem KeePass die Adresse geliefert hat. */
    private final class Deferred implements SearchableKnowledgeSource, PageViewSource {

        private final SourceAddress sourceId;
        private final SourceSettings settings;
        private KnowledgeSourcePort opened;

        Deferred(SourceAddress sourceId, SourceSettings settings) {
            this.sourceId = sourceId;
            this.settings = settings;
        }

        private synchronized KnowledgeSourcePort port() throws KnowledgeSourceException {
            if (opened == null) {
                SourceSettings filled = completed(settings);
                try {
                    opened = delegate.open(sourceId, filled);
                } catch (IllegalArgumentException e) {
                    throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE,
                            "Quelle mit der Adresse aus KeePass nicht anbindbar: " + e.getMessage(), e);
                }
            }
            return opened;
        }

        @Override
        public SourceAddress sourceId() {
            return sourceId;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            return port().discover(scope);
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return port().load(resourceId);
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return port().discoverLinks(resourceId);
        }

        @Override
        public List<SourceSearchHit> search(SourceQuery query) throws KnowledgeSourceException {
            KnowledgeSourcePort port = port();
            if (!(port instanceof SearchableKnowledgeSource)) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED,
                        "Quelle " + sourceId.value() + " hat keine eigene Suche");
            }
            return ((SearchableKnowledgeSource) port).search(query);
        }

        @Override
        public List<SourcePartition> partitions() throws KnowledgeSourceException {
            return port().partitions();
        }

        @Override
        public PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException {
            return SourceCapabilities.pageView(port(), page);
        }

        @Override

        public DownloadAccess downloadAccess() {

            KnowledgeSourcePort port;
            synchronized (this) {
                port = opened; // ohne Öffnen: keine KeePass-Abfrage nur für den Zustand
            }
            return port == null ? DownloadAccess.OPEN : SourceCapabilities.downloadAccess(port);

        }


        @Override
        public byte[] loadViewImage(String src) throws KnowledgeSourceException {
            return SourceCapabilities.loadViewImage(port(), src);
        }

        @Override
        public byte[] loadAttachment(PageView.Attachment attachment) throws KnowledgeSourceException {
            return SourceCapabilities.loadAttachment(port(), attachment);
        }

        @Override
        public byte[] loadAttachmentPreview(PageView.Attachment attachment, PageView.Preview preview)
                throws KnowledgeSourceException {
            return SourceCapabilities.loadAttachmentPreview(port(), attachment, preview);
        }

        @Override
        public List<KnowledgeResource> attachmentsOf(KnowledgeResourceId page) throws KnowledgeSourceException {
            return SourceCapabilities.attachmentsOf(port(), page);
        }

        /** Answer only for an opened source: asking must not trigger a KeePass request. */
        @Override
        public boolean isAttachment(KnowledgeResourceId id) {
            KnowledgeSourcePort port;
            synchronized (this) {
                port = opened;
            }
            return port != null && SourceCapabilities.isAttachment(port, id);
        }

        @Override
        public SourceFile loadAttachmentFile(KnowledgeResourceId attachment) throws KnowledgeSourceException {
            return SourceCapabilities.loadAttachmentFile(port(), attachment);
        }

        @Override
        public String toString() {
            return "VaultSource[" + sourceId + "]";
        }
    }
}
