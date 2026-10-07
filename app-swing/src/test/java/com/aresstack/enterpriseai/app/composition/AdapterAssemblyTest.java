package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.net.ProxyPolicy;
import com.aresstack.enterpriseai.app.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.confluence.ConfluenceKnowledgeSource;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiKnowledgeSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.StringReader;
import java.net.ProxySelector;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die echten Adapter entstehen aus der Beispielkonfiguration ohne Netzwerkzugriff und lassen sich mit dem Graphen
 * komponieren und wieder schließen. Agent-Modus bleibt hier aus (Solon ist prozessglobal; siehe Roundtrip-Test).
 */
public class AdapterAssemblyTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final ProxySelector originalSelector = ProxySelector.getDefault();

    private static final KeePassPairingCallback NO_PAIRING = new KeePassPairingCallback() {
        @Override
        public char[] requestPairingPassword(String clientDisplayName) {
            return null;
        }
    };

    /** Führt sofort im aufrufenden Thread aus (kein EDT nötig). */
    private static final class DirectExecutor implements java.util.concurrent.Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    @After
    public void restoreSelector() {
        ProxySelector.setDefault(originalSelector);
    }

    private AppConfig exampleConfig(boolean keePassEnabled) throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toPath().resolve("index").toString());
        p.setProperty("knowledge.indexOnStartup", "false");
        p.setProperty("security.keepass.enabled", String.valueOf(keePassEnabled));
        p.setProperty("security.keepass.pairingKeyStore", "memory");
        p.setProperty("network.proxy.mode", "MANUAL");
        p.setProperty("network.proxy.host", "proxy.intern.example");
        p.setProperty("network.proxy.port", "3128");
        return AppConfigLoader.fromProperties(p);
    }

    @Test
    public void exampleConfigurationYieldsRealAdaptersWithoutTouchingTheNetwork() throws Exception {
        AppConfig config = exampleConfig(true);
        ProxyPolicy proxy = new ProxyPolicy(config.network());
        ApplicationPorts ports = AdapterAssembly.create(config, proxy, NO_PAIRING, new InMemoryPairingKeyStore());
        try {
            assertTrue(ports.chat() instanceof OpenAiCompatibleChatAdapter);
            assertTrue(ports.embeddings() instanceof OpenAiCompatibleEmbeddingAdapter);
            assertEquals(768, ports.embeddingSpace().dimension());
            assertTrue(ports.index() instanceof LuceneKnowledgeIndex);
            assertTrue(ports.secrets() instanceof KeePassRpcSecretProvider);
            assertEquals(Arrays.asList(KnowledgeSourceId.of("wiki"), KnowledgeSourceId.of("confluence")),
                    new java.util.ArrayList<KnowledgeSourceId>(ports.sources().ids()));
            assertTrue(ports.sources().find(KnowledgeSourceId.of("wiki")).port() instanceof MediaWikiKnowledgeSource);
            assertTrue(ports.sources().find(KnowledgeSourceId.of("confluence")).port()
                    instanceof ConfluenceKnowledgeSource);
            assertFalse("Agent-Modus ist in der Beispielkonfiguration aus", ports.hasAgent());
            assertEquals(Arrays.asList("knowledge-index"), ports.resourceNames());
            assertFalse(ports.toString().toLowerCase().contains("password"));
        } finally {
            ports.close();
            ports.close();
        }
    }

    @Test
    public void withoutKeePassTheAppComposesAndReportsMissingSecrets() throws Exception {
        AppConfig config = exampleConfig(false);
        ProxyPolicy proxy = new ProxyPolicy(config.network());
        ApplicationPorts ports = AdapterAssembly.create(config, proxy, null, null);
        CompositionRoot root = CompositionRoot.compose(config, ports, new DirectExecutor(),
                System::currentTimeMillis, null);
        try {
            assertTrue(ports.secrets() instanceof UnavailableSecretProvider);
            assertTrue(StartupNotices.of(config).get(0).contains("KeePassRPC ist deaktiviert"));
            assertFalse(root.hasAgent());
            assertNull(root.agentService());
        } finally {
            root.shutdown().run();
        }
        assertEquals(Arrays.asList("cancel-chat-turns", "end-agent-mode", "stop-indexing", "close-ports",
                "stop-executors"), root.shutdown().executedSteps());
    }
}
