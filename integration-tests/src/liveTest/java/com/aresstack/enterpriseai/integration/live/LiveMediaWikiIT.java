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
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentials;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiKnowledgeSource;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Slice C gegen eine echte MediaWiki. Parameter: {@code -Dlive.wiki.apiUrl=https://…/w -Dlive.wiki.startPoint=Titel}
 * (optional {@code -Dlive.wiki.siteKey=… -Dlive.wiki.user=…}); das Passwort in {@code ENTERPRISE_AI_LIVE_WIKI_PASSWORD},
 * nur nötig, wenn ein Benutzer angegeben ist.
 */
public class LiveMediaWikiIT {

    @Test
    public void startPageIsDiscoveredLoadedAndIndexed() throws Exception {
        String apiUrl = LiveSettings.required("live.wiki.apiUrl");
        String startPoint = LiveSettings.required("live.wiki.startPoint");
        String siteKey = LiveSettings.optional("live.wiki.siteKey") == null ? "live" : LiveSettings.optional("live.wiki.siteKey");
        final String user = LiveSettings.optional("live.wiki.user");
        final char[] password = user == null ? null : LiveSettings.secret(LiveSettings.WIKI_PASSWORD_ENV);
        MediaWikiCredentialsProvider credentials = user == null
                ? MediaWikiCredentialsProvider.anonymous()
                : site -> new MediaWikiCredentials(user, password.clone());
        MediaWikiSiteConfig site = MediaWikiSiteConfig.builder(siteKey, apiUrl).requiresLogin(user != null).build();
        MediaWikiKnowledgeSource source = new MediaWikiKnowledgeSource(KnowledgeSourceId.of("live-wiki"), site, credentials);

        SourceScope scope = SourceScope.builder().startPoint(startPoint).maxDepth(0).build();
        List<KnowledgeResource> resources = source.discover(scope);
        assertFalse("Startseite nicht gefunden", resources.isEmpty());
        KnowledgeDocument document = source.load(resources.get(0).id());
        assertFalse("Seite ohne Text", document.text().trim().isEmpty());

        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        IndexingReport report = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults())).indexSource(source, scope, IndexingListener.none());
        assertTrue(report.toString(), report.count(IndexingStatus.INDEXED) >= 1);
        RetrievalResult result = new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null)
                .retrieve(resources.get(0).title());
        assertFalse(result.isEmpty());
        System.out.println("[live] wiki: " + report.discovered() + " Seite(n), " + report.chunkCount() + " Abschnitte");
    }
}
