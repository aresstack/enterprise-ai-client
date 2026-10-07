package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.confluence.ConfluenceConfig;

/**
 * Eine Confluence-Data-Center-Instanz als Wissensquelle ({@code source.<id>.type=confluence}). Transportdetails
 * (Timeouts, Client-Zertifikat) liegen hier, weil der Adapter sie über die Transport-Naht von außen bekommt.
 */
public final class ConfluenceSourceConfig extends SourceConfig {

    private final ConfluenceConfig confluence;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final ClientCertificateConfig clientCertificate;

    ConfluenceSourceConfig(KnowledgeSourceId sourceId, SourceScope scope, ConfluenceConfig confluence,
                           int connectTimeoutMillis, int readTimeoutMillis, ClientCertificateConfig clientCertificate) {
        super(sourceId, scope, confluence.credentialRef());
        this.confluence = confluence;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.clientCertificate = clientCertificate;
    }

    public ConfluenceConfig confluence() {
        return confluence;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    /** Client-Zertifikat für mTLS oder {@code null} für Standard-TLS. */
    public ClientCertificateConfig clientCertificate() {
        return clientCertificate;
    }

    @Override
    public String type() {
        return "confluence";
    }
}
