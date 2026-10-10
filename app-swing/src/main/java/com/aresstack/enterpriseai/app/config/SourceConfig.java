package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.source.api.SourceScope;

/**
 * Gemeinsamer Teil einer konfigurierten Wissensquelle: ID, Crawl-Umfang und optionaler Verweis auf Zugangsdaten.
 * Konkrete Quellen: {@link MediaWikiSourceConfig}, {@link ConfluenceSourceConfig}. Weitere Quelltypen (SharePoint,
 * Dateien) kommen als weitere Unterklasse plus Zweig in der Composition Root hinzu, ohne den Kern zu ändern.
 */
public abstract class SourceConfig {

    private final KnowledgeSourceId sourceId;
    private final SourceScope scope;
    private final SecretRef credentialRef;
    private final boolean enabled;

    SourceConfig(KnowledgeSourceId sourceId, SourceScope scope, SecretRef credentialRef, boolean enabled) {
        this.sourceId = sourceId;
        this.scope = scope;
        this.credentialRef = credentialRef;
        this.enabled = enabled;
    }

    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    /** Startpunkte, Tiefe und Obergrenze des Crawls beim Indexieren. */
    public SourceScope scope() {
        return scope;
    }

    /** Verweis auf die Zugangsdaten im Security-Backend oder {@code null} für anonymen Zugriff. */
    public SecretRef credentialRef() {
        return credentialRef;
    }

    /**
     * {@code source.<id>.enabled} (Standard {@code true}): abgewählte Quellen bleiben konfiguriert, werden aber
     * beim Start nicht indexiert und im Chat nicht durchsucht; das Häkchen im Drawer schaltet sie zur Laufzeit um.
     */
    public boolean enabled() {
        return enabled;
    }

    /** Kurzer Typname für Meldungen, z. B. {@code mediawiki}. */
    public abstract String type();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + sourceId + ", scope=" + scope + ", credentialRef="
                + (credentialRef == null ? "keine" : credentialRef) + (enabled ? "" : ", abgewählt") + "]";
    }
}
