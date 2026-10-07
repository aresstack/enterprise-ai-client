package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.confluence.ConfluenceConfig;
import com.aresstack.enterpriseai.source.confluence.ConfluenceKnowledgeSource;
import com.aresstack.enterpriseai.source.confluence.UrlConnectionConfluenceTransport;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertFalse;

/**
 * Slice D gegen ein echtes Confluence mit Zugangsdaten aus einem echten KeePass. Parameter:
 * {@code -Dlive.confluence.baseUrl=https://…/wiki -Dlive.confluence.startPoint=space:KEY|pageId
 * -Dlive.confluence.credentialRef=KeePass-Titel} (optional {@code -Dlive.confluence.allowInsecureHttp=true}),
 * KeePass wie in {@link LiveKeePassIT}.
 */
public class LiveConfluenceKeePassIT {

    @Test
    public void startPointIsDiscoveredAndLoadedWithKeePassCredentials() throws Exception {
        URI baseUrl = URI.create(LiveSettings.required("live.confluence.baseUrl"));
        String startPoint = LiveSettings.required("live.confluence.startPoint");
        String credentialRef = LiveSettings.required("live.confluence.credentialRef");
        ConfluenceConfig config = ConfluenceConfig.builder(baseUrl)
                .credentialRef(SecretRef.of("keepass:" + credentialRef))
                .allowInsecureHttp(LiveSettings.flag("live.confluence.allowInsecureHttp"))
                .build();
        ConfluenceKnowledgeSource source = new ConfluenceKnowledgeSource(KnowledgeSourceId.of("live-confluence"),
                config, UrlConnectionConfluenceTransport.builder().build(), LiveKeePassIT.provider());

        List<KnowledgeResource> resources = source.discover(SourceScope.builder().startPoint(startPoint).maxDepth(0).build());
        assertFalse("Startpunkt nicht gefunden", resources.isEmpty());
        KnowledgeDocument document = source.load(resources.get(0).id());
        assertFalse("Seite ohne Text", document.text().trim().isEmpty());
        System.out.println("[live] confluence: " + resources.size() + " Ressource(n) am Startpunkt");
    }
}
