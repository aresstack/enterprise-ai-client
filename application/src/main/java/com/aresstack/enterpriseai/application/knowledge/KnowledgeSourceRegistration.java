package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;

/**
 * Eine konfigurierte Wissensquelle: der {@link KnowledgeSourcePort Port} zusammen mit dem
 * {@link SourceScope Ausschnitt}, der bei einer (Neu-)Indexierung durchlaufen wird. Die Composition Root baut je
 * konfigurierter Quelle eine Registrierung; Use Cases und MCP-Werkzeuge erhalten sie über den
 * {@link KnowledgeSourceCatalog}. Enthält keine Zugangsdaten; die bleiben im Adapter.
 */
public final class KnowledgeSourceRegistration {

    private final KnowledgeSourcePort port;
    private final SourceScope scope;

    public KnowledgeSourceRegistration(KnowledgeSourcePort port, SourceScope scope) {
        if (port == null || scope == null) {
            throw new IllegalArgumentException("port und scope sind Pflicht");
        }
        if (port.sourceId() == null) {
            throw new IllegalArgumentException("der Source-Port liefert keine Source-ID");
        }
        this.port = port;
        this.scope = scope;
    }

    public KnowledgeSourceId sourceId() {
        return port.sourceId();
    }

    public KnowledgeSourcePort port() {
        return port;
    }

    /** Was eine Indexierung dieser Quelle abdeckt (Startpunkte, Tiefe, Höchstzahl). */
    public SourceScope scope() {
        return scope;
    }

    @Override
    public String toString() {
        return "KnowledgeSourceRegistration{" + sourceId() + ", " + scope + "}";
    }
}
