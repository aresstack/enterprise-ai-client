package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.RefreshKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.application.rag.RetrievalSettings;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Gemeinsamer Aufbau der Werkzeug-Tests: Fake-Embedding, In-Memory-Index, zwei Fake-Quellen ("wiki" mit drei
 * Seiten, "docs" mit einer), die AP10-Use-Cases und die Werkzeuge. Nichts davon braucht Netzwerk oder Dateien.
 */
final class KnowledgeToolFixture {

    final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    final EmbeddingModelIdentity space = embeddings.modelIdentity();
    final FailingIndex index = new FailingIndex(new InMemoryKnowledgeIndex());
    final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Java", "Java installieren", "# Linux\n\nJava installiert man mit apt install openjdk-8-jdk.",
                    "Drucker")
            .add("Drucker", "Drucker einrichten", "Den Drucker richtet man über CUPS ein.")
            .add("Kaffee", "Kaffeemaschine", "Die Kaffeemaschine wird monatlich entkalkt.");
    final InMemoryKnowledgeSource docs = new InMemoryKnowledgeSource("docs")
            .add("Urlaub", "Urlaubsantrag", "Urlaub beantragt man im Portal, der Drucker ist dafür nicht nötig.");
    final SourceScope wikiScope = SourceScope.of("Java", "Drucker", "Kaffee");
    final SourceScope docsScope = SourceScope.of("Urlaub");

    final KnowledgeSourceCatalog catalog;
    final IndexKnowledgeUseCase indexing;
    final RetrieveKnowledgeUseCase retrieval;
    final LoadKnowledgeDocumentUseCase documents;
    final RefreshKnowledgeSourceUseCase refresh;
    final KnowledgeToolSettings settings;
    final KnowledgeMcpTools tools;

    KnowledgeToolFixture() {
        this(KnowledgeToolSettings.defaults(), null);
    }

    KnowledgeToolFixture(KnowledgeToolSettings settings, RetrievalSettings retrievalSettings) {
        this(settings, retrievalSettings, null);
    }

    /** @param extraSources weitere Quellen im Katalog (nicht indexiert), z. B. blockierende oder scheiternde */
    KnowledgeToolFixture(KnowledgeToolSettings settings, RetrievalSettings retrievalSettings,
                         Collection<KnowledgeSourceRegistration> extraSources) {
        List<KnowledgeSourceRegistration> registrations = new ArrayList<KnowledgeSourceRegistration>();
        registrations.add(new KnowledgeSourceRegistration(wiki, wikiScope));
        registrations.add(new KnowledgeSourceRegistration(docs, docsScope));
        if (extraSources != null) {
            registrations.addAll(extraSources);
        }
        this.catalog = new KnowledgeSourceCatalog(registrations);
        this.indexing = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        this.retrieval = new RetrieveKnowledgeUseCase(index, embeddings, space, retrievalSettings);
        this.documents = new LoadKnowledgeDocumentUseCase(catalog);
        this.refresh = new RefreshKnowledgeSourceUseCase(indexing, catalog);
        this.settings = settings;
        this.tools = new KnowledgeMcpTools(retrieval, documents, refresh, settings);
    }

    /** Indexiert beide Quellen vollständig. */
    KnowledgeToolFixture indexed() {
        IndexingReport wikiReport = indexing.indexSource(wiki, wikiScope, null);
        IndexingReport docsReport = indexing.indexSource(docs, docsScope, null);
        assertTrue(wikiReport.toString(), wikiReport.isComplete());
        assertTrue(docsReport.toString(), docsReport.isComplete());
        return this;
    }

    KnowledgeResourceId javaId() {
        return wiki.idOf("Java");
    }

    McpToolContribution tool(String name) {
        for (McpToolContribution contribution : tools.contributions()) {
            if (contribution.getName().equals(name)) {
                return contribution;
            }
        }
        throw new IllegalArgumentException("kein Werkzeug " + name);
    }

    /** Ruft den Handler direkt auf (ohne Registry), mit Argumenten als Name/Wert-Paare. */
    McpToolResult call(String toolName, Object... nameValuePairs) {
        return tool(toolName).getHandler().invoke(new McpToolCall(toolName, arguments(nameValuePairs)));
    }

    String ok(String toolName, Object... nameValuePairs) {
        McpToolResult result = call(toolName, nameValuePairs);
        if (result.isError()) {
            fail("unerwarteter Fehler: " + result.getText());
        }
        return result.getText();
    }

    String error(String toolName, Object... nameValuePairs) {
        McpToolResult result = call(toolName, nameValuePairs);
        if (!result.isError()) {
            fail("Fehler erwartet, aber ok: " + result.getText());
        }
        return result.getText();
    }

    static Map<String, Object> arguments(Object... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Name/Wert-Paare erwartet");
        }
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            arguments.put(String.valueOf(nameValuePairs[i]), nameValuePairs[i + 1]);
        }
        return arguments;
    }

    static KnowledgeSourceId source(String value) {
        return KnowledgeSourceId.of(value);
    }

    /** Leitet an einen Index weiter und lässt auf Wunsch einen Suchpfad oder das Schreiben ausfallen. */
    static final class FailingIndex implements KnowledgeIndexPort {

        private final KnowledgeIndexPort delegate;
        volatile boolean failKeyword;
        volatile boolean failSemantic;
        /** Meldung, mit der {@link #replace} scheitert; {@code null} für normales Verhalten. */
        volatile String failReplaceWith;

        FailingIndex(KnowledgeIndexPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void index(Collection<KnowledgeIndexEntry> entries) {
            delegate.index(entries);
        }

        @Override
        public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                            Collection<KnowledgeIndexEntry> entries) {
            if (failReplaceWith != null) {
                throw new KnowledgeIndexException(failReplaceWith);
            }
            delegate.replace(space, resourceId, entries);
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
            if (failKeyword) {
                throw new KnowledgeIndexException("Volltextindex beschädigt");
            }
            return delegate.keywordSearch(query);
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            if (failSemantic) {
                throw new KnowledgeIndexException("Vektorindex nicht lesbar");
            }
            return delegate.semanticSearch(query);
        }

        @Override
        public void remove(KnowledgeResourceId resourceId) {
            delegate.remove(resourceId);
        }

        @Override
        public void removeSource(KnowledgeSourceId sourceId) {
            delegate.removeSource(sourceId);
        }

        @Override
        public void rebuild(Collection<KnowledgeIndexEntry> entries) {
            delegate.rebuild(entries);
        }
    }

    /** Hüllt eine Quelle ein und erlaubt, einzelne Aufrufe zu beobachten oder zu blockieren. */
    static class DelegatingSource implements KnowledgeSourcePort {

        protected final KnowledgeSourcePort delegate;

        DelegatingSource(KnowledgeSourcePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public KnowledgeSourceId sourceId() {
            return delegate.sourceId();
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            return delegate.discover(scope);
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return delegate.load(resourceId);
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return delegate.discoverLinks(resourceId);
        }
    }
}
