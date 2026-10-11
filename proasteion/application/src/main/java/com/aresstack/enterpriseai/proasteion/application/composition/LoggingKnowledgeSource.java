package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hüllt eine Wissensquelle und protokolliert jeden Fehlschlag mit vollständiger Ursachenkette, bevor er an den
 * Use Case geht. {@code IndexKnowledgeUseCase} reduziert eine {@link KnowledgeSourceException} zu einem Text für
 * den Bericht; ohne diese Hülle stünde im Protokoll bei einem TLS- oder Proxy-Fehler der Quelle nur „Quelle nicht
 * lesbar“ ohne den eigentlichen Grund. Secrets gehören laut Vorgabe in keine Ausnahmemeldung, deshalb darf die
 * Meldung der Quelle unverändert ins Protokoll.
 */
public final class LoggingKnowledgeSource implements SearchableKnowledgeSource {

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
    public KnowledgeSourceId sourceId() {
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
