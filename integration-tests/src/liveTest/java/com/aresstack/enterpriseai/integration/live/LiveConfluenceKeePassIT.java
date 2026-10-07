package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.confluence.ConfluenceConfig;
import com.aresstack.enterpriseai.source.confluence.ConfluenceKnowledgeSource;
import com.aresstack.enterpriseai.source.confluence.UrlConnectionConfluenceTransport;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;

/**
 * Stufe 7 der Live-Verifikation: Slice D gegen ein echtes Confluence Data Center mit Zugangsdaten aus einem echten
 * KeePass (Pairing und Schlüsseldatei wie in {@link LiveKeePassIT}). Parameter:
 * {@code -Dlive.confluence.baseUrl=https://…/wiki -Dlive.confluence.startPoint=space:KEY|pageId
 * -Dlive.confluence.credentialRef=KeePass-Titel} (optional {@code -Dlive.confluence.allowInsecureHttp=true},
 * {@code -Dlive.confluence.maxDepth=0}, {@code -Dlive.confluence.maxResources=20},
 * {@code -Dlive.confluence.searchSpaceKey=KEY}, {@code -Dlive.confluence.query=Suchwort}).
 */
public class LiveConfluenceKeePassIT {

    private static final int STAGE = 7;

    private static ConfluenceKnowledgeSource source(KeePassRpcSecretProvider provider) {
        URI baseUrl = URI.create(LiveSettings.required("live.confluence.baseUrl"));
        String credentialRef = LiveSettings.required("live.confluence.credentialRef");
        ConfluenceConfig.Builder config = ConfluenceConfig.builder(baseUrl)
                .credentialRef(SecretRef.of("keepass:" + credentialRef))
                .allowInsecureHttp(LiveSettings.flag("live.confluence.allowInsecureHttp"));
        if (LiveSettings.optional("live.confluence.searchSpaceKey") != null) {
            config.searchSpaceKey(LiveSettings.optional("live.confluence.searchSpaceKey"));
        }
        return new ConfluenceKnowledgeSource(KnowledgeSourceId.of("live-confluence"), config.build(),
                UrlConnectionConfluenceTransport.builder().build(), provider);
    }

    private static SourceScope scope() {
        return SourceScope.builder()
                .startPoint(LiveSettings.required("live.confluence.startPoint"))
                .maxDepth(LiveSettings.integer("live.confluence.maxDepth", 0))
                .maxResources(LiveSettings.integer("live.confluence.maxResources", 20))
                .build();
    }

    /** Welche Autorisierung der Adapter aus dem KeePass-Eintrag ableitet (Basic mit Benutzer, sonst Bearer). */
    private static String authorizationMode(KeePassRpcSecretProvider provider, String credentialRef) throws Exception {
        try (SecretMaterial material = provider.resolve(SecretRef.of("keepass:" + credentialRef))) {
            return material.hasPrincipal() ? "Basic (Benutzername und Passwort)"
                    : "Bearer (Personal Access Token aus dem Passwortfeld, bisher UNVERIFIED)";
        }
    }

    @Test
    public void startPointIsDiscoveredAndLoadedWithKeePassCredentials() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            AtomicInteger prompts = new AtomicInteger();
            KeePassRpcSecretProvider provider = LiveKeePassIT.provider(prompts);
            String credentialRef = LiveSettings.required("live.confluence.credentialRef");
            String mode = authorizationMode(provider, credentialRef);
            ConfluenceKnowledgeSource source = source(provider);
            SourceScope scope = scope();

            List<KnowledgeResource> resources = source.discover(scope);
            assertFalse("Startpunkt nicht gefunden", resources.isEmpty());
            KnowledgeDocument document = source.load(resources.get(0).id());
            assertFalse("Seite ohne Text", document.text().trim().isEmpty());
            LiveSettings.report(STAGE, "Autorisierung " + mode + ", KeePass-Pairing-Rückfragen in diesem Lauf: " + prompts.get());
            LiveSettings.report(STAGE, "discover (Tiefe " + scope.maxDepth() + "): " + resources.size()
                    + " Ressource(n) am Startpunkt, erste Seite " + document.text().length() + " Zeichen Text, Revision "
                    + (resources.get(0).revision().isKnown() ? "bekannt" : "unbekannt"));
        }, LiveSettings.KEEPASS_PAIRING_ENV);
    }

    @Test
    public void searchOfConfluenceFindsPages() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            KeePassRpcSecretProvider provider = LiveKeePassIT.provider(new AtomicInteger());
            ConfluenceKnowledgeSource source = source(provider);
            String query = LiveSettings.optional("live.confluence.query");
            if (query == null) {
                List<KnowledgeResource> resources = source.discover(scope());
                assertFalse("Startpunkt nicht gefunden", resources.isEmpty());
                query = resources.get(0).title();
            }
            List<SourceSearchHit> hits = source.search(SourceQuery.of(query));
            LiveSettings.report(STAGE, "CQL-Suche" + (LiveSettings.optional("live.confluence.searchSpaceKey") != null
                    ? " im Space" : " ohne Space-Einschränkung") + ": " + hits.size() + " Treffer");
            assertFalse("Confluence-Suche liefert keinen Treffer", hits.isEmpty());
        }, LiveSettings.KEEPASS_PAIRING_ENV);
    }
}
