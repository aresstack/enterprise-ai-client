package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;

/** Eine MediaWiki-Site als Wissensquelle ({@code source.<id>.type=mediawiki}). */
public final class MediaWikiSourceConfig extends SourceConfig {

    private final MediaWikiSiteConfig site;

    MediaWikiSourceConfig(KnowledgeSourceId sourceId, SourceScope scope, SecretRef credentialRef,
                          MediaWikiSiteConfig site, boolean enabled) {
        super(sourceId, scope, credentialRef, enabled);
        this.site = site;
    }

    public MediaWikiSiteConfig site() {
        return site;
    }

    @Override
    public String type() {
        return "mediawiki";
    }
}
