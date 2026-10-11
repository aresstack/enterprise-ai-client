package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.VaultLocations;
import com.aresstack.enterpriseai.app.security.AskedSecretProvider;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceLink;
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
    public SourceScope scope(SourceSettings settings) {
        return delegate.scope(settings);
    }

    @Override
    public KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings) {
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
    private final class Deferred implements SearchableKnowledgeSource {

        private final KnowledgeSourceId sourceId;
        private final SourceSettings settings;
        private KnowledgeSourcePort opened;

        Deferred(KnowledgeSourceId sourceId, SourceSettings settings) {
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
        public KnowledgeSourceId sourceId() {
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
        public String toString() {
            return "VaultSource[" + sourceId + "]";
        }
    }
}
