package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.rag.RetrievalResult;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentials;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiKnowledgeSource;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Stufe 5 der Live-Verifikation: Slice C gegen eine echte MediaWiki. Parameter:
 * {@code -Dlive.wiki.apiUrl=https://…/w -Dlive.wiki.startPoint=Titel} (optional {@code -Dlive.wiki.siteKey=…},
 * {@code -Dlive.wiki.user=…}, {@code -Dlive.wiki.maxDepth=0}, {@code -Dlive.wiki.maxResources=20}); das Passwort in
 * {@code ENTERPRISE_AI_LIVE_WIKI_PASSWORD}, nur nötig, wenn ein Benutzer angegeben ist.
 */
public class LiveMediaWikiIT {

    private static final int STAGE = 5;

    private static MediaWikiKnowledgeSource source() {
        String apiUrl = LiveSettings.required("live.wiki.apiUrl");
        String siteKey = LiveSettings.optional("live.wiki.siteKey") == null ? "live" : LiveSettings.optional("live.wiki.siteKey");
        final String user = LiveSettings.optional("live.wiki.user");
        // Passwort vorab aus der Umgebung lesen: fehlt es, wird übersprungen statt beim Login zu scheitern.
        final char[] password = user == null ? null : LiveSettings.secret(LiveSettings.WIKI_PASSWORD_ENV);
        MediaWikiCredentialsProvider credentials = user == null
                ? MediaWikiCredentialsProvider.anonymous()
                : site -> new MediaWikiCredentials(user, password.clone());
        MediaWikiSiteConfig site = MediaWikiSiteConfig.builder(siteKey, apiUrl).requiresLogin(user != null).build();
        return new MediaWikiKnowledgeSource(KnowledgeSourceId.of("live-wiki"), site, credentials);
    }

    private static SourceScope scope() {
        return SourceScope.builder()
                .startPoint(LiveSettings.required("live.wiki.startPoint"))
                .maxDepth(LiveSettings.integer("live.wiki.maxDepth", 0))
                .maxResources(LiveSettings.integer("live.wiki.maxResources", 20))
                .build();
    }

    @Test
    public void startPageIsDiscoveredLoadedAndIndexed() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            MediaWikiKnowledgeSource source = source();
            SourceScope scope = scope();
            boolean login = LiveSettings.optional("live.wiki.user") != null;

            List<KnowledgeResource> resources = source.discover(scope);
            assertFalse("Startseite nicht gefunden", resources.isEmpty());
            KnowledgeDocument document = source.load(resources.get(0).id());
            assertFalse("Seite ohne Text", document.text().trim().isEmpty());
            LiveSettings.report(STAGE, (login ? "mit Login" : "anonym") + ": discover (Tiefe " + scope.maxDepth() + ") "
                    + resources.size() + " Seite(n), erste Seite " + document.text().length() + " Zeichen Text, Revision "
                    + (resources.get(0).revision().isKnown() ? "bekannt" : "unbekannt"));

            DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
            InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
            IndexingReport report = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                    new KnowledgeChunker(KnowledgeChunkingPolicy.defaults())).indexSource(source, scope, IndexingListener.none());
            assertTrue(report.toString(), report.count(IndexingStatus.INDEXED) >= 1);
            RetrievalResult result = new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null)
                    .retrieve(resources.get(0).title());
            assertFalse("kein Treffer im Index für den Seitentitel", result.isEmpty());
            LiveSettings.report(STAGE, "indexiert: " + report.count(IndexingStatus.INDEXED) + " von " + report.discovered()
                    + " Seite(n), " + report.chunkCount() + " Abschnitte, fehlgeschlagen "
                    + report.count(IndexingStatus.FAILED) + "; Retrieval nach Seitentitel liefert Treffer");
        }, LiveSettings.WIKI_PASSWORD_ENV);
    }

    @Test
    public void searchOfTheWikiFindsTheStartPage() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            MediaWikiKnowledgeSource source = source();
            String startPoint = LiveSettings.required("live.wiki.startPoint");
            List<SourceSearchHit> hits = source.search(SourceQuery.of(startPoint));
            boolean startPageAmongHits = false;
            for (SourceSearchHit hit : hits) {
                if (hit.title() != null && hit.title().equalsIgnoreCase(startPoint)) {
                    startPageAmongHits = true;
                }
            }
            LiveSettings.report(STAGE, "Wiki-Volltextsuche nach dem Startseitentitel: " + hits.size() + " Treffer, Startseite "
                    + (startPageAmongHits ? "darunter" : "NICHT darunter"));
            assertTrue("Wiki-Suche nach dem Startseitentitel nennt die Startseite nicht (" + hits.size() + " Treffer)",
                    startPageAmongHits);
        }, LiveSettings.WIKI_PASSWORD_ENV);
    }
}
