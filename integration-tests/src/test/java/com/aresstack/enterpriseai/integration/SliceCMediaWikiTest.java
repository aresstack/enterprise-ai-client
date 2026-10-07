package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.rag.RetrievalResult;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.mediawiki.FakeMediaWikiServer;
import com.aresstack.enterpriseai.source.mediawiki.FakeMediaWikiTransport;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentials;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiKnowledgeSource;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecret;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Slice C – MediaWiki: echter MediaWiki-Adapter (HTTP, Login mit Sitzungs-Cookie) gegen eine Fake-Wiki hinter
 * einem lokalen HTTP-Server → Knowledge-Pipeline (Chunking, Embedding-Fixture, Lucene) → Suche im Index und
 * Suche in der Quelle ({@code list=search}).
 */
public class SliceCMediaWikiTest {

    private static final String USER = "wikibot";
    private static final String PASSWORD = "wiki-pw-3b1c-geheim";
    private static final KnowledgeSourceId SOURCE_ID = KnowledgeSourceId.of("intranet-wiki");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeMediaWikiTransport wiki;
    private FakeMediaWikiServer server;
    private LuceneKnowledgeIndex index;
    private DeterministicEmbeddingPort embeddings;
    private IndexKnowledgeUseCase indexing;
    private RetrieveKnowledgeUseCase retrieval;

    @Before
    public void setUp() throws Exception {
        wiki = new FakeMediaWikiTransport();
        wiki.page("Hauptseite", "<p>Willkommen im <a href=\"/wiki/Handbuch\">Handbuch</a> und im Glossar.</p>", 1, 101,
                "Handbuch", "Glossar");
        wiki.page("Handbuch", "<h2>Installation</h2><p>Die Installation erfolgt per Gradle.</p>"
                + "<h2>Betrieb</h2><p>Der Betrieb läuft unter Java 8.</p>", 2, 102);
        wiki.page("Glossar", "<dl><dt>RAG</dt><dd>Retrieval Augmented Generation</dd></dl>", 3, 103);
        wiki.requiredUser = USER;
        wiki.requiredPassword = PASSWORD;
        server = new FakeMediaWikiServer(wiki);

        embeddings = DeterministicEmbeddingPort.withDimension(8);
        index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());
        indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        retrieval = new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null);
    }

    @After
    public void tearDown() {
        if (index != null) {
            index.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private MediaWikiKnowledgeSource source(final String password) {
        MediaWikiSiteConfig site = MediaWikiSiteConfig.builder("intranet", server.apiUrl())
                .displayName("Intranet-Wiki")
                .requiresLogin(true)
                .build();
        return new MediaWikiKnowledgeSource(SOURCE_ID, site,
                config -> new MediaWikiCredentials(USER, password.toCharArray()));
    }

    private static SourceScope fromMainPage() {
        return SourceScope.builder().startPoint("Hauptseite").maxDepth(1).build();
    }

    private int logins() {
        synchronized (wiki) {
            return wiki.logins;
        }
    }

    @Test
    public void loginProtectedWikiIsCrawledIndexedAndSearchable() throws Exception {
        MediaWikiKnowledgeSource source = source(PASSWORD);

        IndexingReport report = indexing.indexSource(source, fromMainPage(), IndexingListener.none());
        assertTrue(report.toString(), report.isComplete());
        assertEquals(3, report.discovered());
        assertEquals(3, report.count(IndexingStatus.INDEXED));
        assertEquals("genau ein Login für den ganzen Lauf", 1, logins());
        assertNoSecret(report.toString(), PASSWORD);

        RetrievalResult result = retrieval.retrieve("Installation Gradle");
        assertFalse(result.isEmpty());
        assertEquals("Handbuch", result.hits().get(0).resource().title());
        assertEquals("wiki:intranet/Handbuch", result.hits().get(0).resource().id().value());
        assertEquals(SOURCE_ID, result.hits().get(0).resource().sourceId());
        assertTrue(result.hits().get(0).chunk().text().contains("Gradle"));

        // Suche in der Quelle selbst (SearchableKnowledgeSource) über dieselbe Sitzung.
        List<SourceSearchHit> hits = source.search(SourceQuery.of("Gradle"));
        assertEquals(1, hits.size());
        assertEquals("Handbuch", hits.get(0).title());
        assertEquals(1, logins());
    }

    @Test
    public void wrongPasswordIsAccessDeniedAndNothingIsIndexed() throws Exception {
        MediaWikiKnowledgeSource source = source("falsch-" + PASSWORD);
        try {
            source.discover(fromMainPage());
            fail("falsches Passwort muss ACCESS_DENIED liefern");
        } catch (KnowledgeSourceException expected) {
            assertEquals(KnowledgeSourceException.Kind.ACCESS_DENIED, expected.kind());
            assertNoSecret(expected.getMessage(), PASSWORD, "falsch-" + PASSWORD);
        }

        IndexingReport report = indexing.indexSource(source, fromMainPage(), IndexingListener.none());
        assertTrue(report.discoveryFailed());
        assertEquals(0, report.count(IndexingStatus.INDEXED));
        assertNoSecret(report.discoveryFailure(), PASSWORD, "falsch-" + PASSWORD);
        assertTrue(retrieval.retrieve("Installation").isEmpty());
    }
}
