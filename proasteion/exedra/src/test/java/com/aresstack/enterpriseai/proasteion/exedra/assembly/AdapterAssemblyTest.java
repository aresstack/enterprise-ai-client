package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.exedra.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.StartupNotices;
import com.aresstack.enterpriseai.proasteion.application.composition.LoggingKnowledgeSource;
import com.aresstack.enterpriseai.proasteion.application.composition.LiveKnowledgeIndex;

import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingReport;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingStage;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResourceIndexingOutcome;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.ConfluenceKnowledgeSource;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.mediawiki.MediaWikiKnowledgeSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.StringReader;
import java.net.ProxySelector;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    private static final SourceAddress WIKI = SourceAddress.of("wiki://wiki.intern.example/w");
    private static final SourceAddress CONFLUENCE = SourceAddress.of("confluence://confluence.intern.example/confluence");

    /** Die Vorlage mit den auskommentierten Beispielquellen (Wiki, Confluence) aktiviert. */
    static Properties exampleWithSources() throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(AppConfigLoader.exampleConfiguration().replace("\n#source.1.", "\nsource.1.")
                .replace("\n#source.2.", "\nsource.2.")));
        return p;
    }

    private Properties exampleProperties(boolean keePassEnabled) throws Exception {
        Properties p = exampleWithSources();
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toPath().resolve("index").toString());
        p.setProperty("knowledge.indexOnStartup", "false");
        p.setProperty("security.keepass.enabled", String.valueOf(keePassEnabled));
        p.setProperty("security.keepass.pairingKeyStore", "memory");
        p.setProperty("network.proxy.mode", "MANUAL_PROXY");
        p.setProperty("network.proxy.host", "proxy.intern.example");
        p.setProperty("network.proxy.port", "3128");
        return p;
    }

    private AppConfig exampleConfig(boolean keePassEnabled) throws Exception {
        return AppConfigLoader.fromProperties(exampleProperties(keePassEnabled));
    }

    @Test
    public void exampleConfigurationYieldsRealAdaptersWithoutTouchingTheNetwork() throws Exception {
        AppConfig config = exampleConfig(true);
        NetworkServices proxy = NetworkServices.from(config.network());
        ApplicationPorts ports = AdapterAssembly.create(config, proxy, NO_PAIRING, new InMemoryPairingKeyStore());
        try {
            assertTrue("Chat geht über die Ausführung je Katalog", ports.chat().toString().contains("RoutedChat"));
            assertTrue(ports.embeddings().toString().startsWith("LiveEmbeddings"));
            assertEquals(768, ports.embeddingSpace().dimension());
            assertTrue(ports.index() instanceof LiveKnowledgeIndex);
            assertTrue(((AskedSecretProvider) ports.secrets()).vault() instanceof KeePassRpcSecretProvider);
            assertEquals(Arrays.asList(WIKI, CONFLUENCE),
                    new java.util.ArrayList<SourceAddress>(ports.sources().ids()));
            assertTrue(unwrap(ports.sources().find(WIKI).port())
                    instanceof MediaWikiKnowledgeSource);
            assertTrue(unwrap(ports.sources().find(CONFLUENCE).port())
                    instanceof ConfluenceKnowledgeSource);
            assertFalse("Agent-Modus ist in der Beispielkonfiguration aus", ports.hasAgent());
            // "chat-browsers" (#141) only exists where Firefox is installed, as on the CI runners.
            java.util.List<String> resources = new java.util.ArrayList<String>(ports.resourceNames());
            resources.remove("chat-browsers");
            assertEquals(Arrays.asList("local-models", "mcp-server", "knowledge-index", "raw-knowledge-index",
                    "fact-store", "memory-store"),
                    resources);
            assertFalse(ports.toString().toLowerCase().contains("password"));
        } finally {
            ports.close();
            ports.close();
        }
    }

    @Test
    public void withoutKeePassTheAppComposesAndReportsMissingSecrets() throws Exception {
        AppConfig config = exampleConfig(false);
        NetworkServices proxy = NetworkServices.from(config.network());
        ApplicationPorts ports = AdapterAssembly.create(config, proxy, null, null);
        CompositionRoot root = CompositionRoot.compose(config, ports, new DirectExecutor(),
                System::currentTimeMillis, null);
        try {
            assertTrue(((AskedSecretProvider) ports.secrets()).vault() instanceof UnavailableSecretProvider);
            assertTrue(StartupNotices.of(config).get(0).contains("KeePassRPC ist deaktiviert"));
            assertTrue("ohne Agent-Kommando der eingebaute Recherche-Agent", root.hasAgent());
            assertNotNull(root.researchHost());
            try {
                ports.embeddings().embed(Arrays.asList("Probe"));
                fail("expected EmbeddingException");
            } catch (EmbeddingException e) {
                assertEquals("fehlendes Secret ist ein Authentifizierungsfehler des Ports, kein Absturz",
                        EmbeddingFailureKind.AUTHENTICATION, e.kind());
            }
        } finally {
            root.shutdown().run();
        }
        assertEquals(Arrays.asList("stop-prefetch", "cancel-chat-turns", "end-agent-mode", "stop-indexing", "close-ports",
                "stop-executors"), root.shutdown().executedSteps());
    }

    @Test
    public void confluenceClientCertificateWithoutKeePassDoesNotAbortStartup() throws Exception {
        Properties p = exampleProperties(false);
        p.setProperty("source.2.clientCertificate.alias", "client");
        p.setProperty("source.2.clientCertificate.keyStoreFile",
                temp.newFile("client.p12").getAbsolutePath());
        p.setProperty("source.2.clientCertificate.keyStorePasswordRef", "Client-Zertifikat");
        AppConfig config = AppConfigLoader.fromProperties(p);

        ApplicationPorts ports = AdapterAssembly.create(config, NetworkServices.from(config.network()), null, null);
        try {
            KnowledgeSourcePort confluence = ports.sources().find(CONFLUENCE).port();
            assertTrue("Quellen tragen die Protokollhülle", confluence instanceof LoggingKnowledgeSource);
            assertTrue(unwrap(confluence) instanceof ConfluenceKnowledgeSource);
            try {
                confluence.discover(SourceScope.of("space:DEV"));
                fail("expected KnowledgeSourceException");
            } catch (KnowledgeSourceException e) {
                assertTrue("je Anfrage gemeldet, nicht beim Start: " + e.kind(),
                        e.kind() == KnowledgeSourceException.Kind.ACCESS_DENIED
                                || e.kind() == KnowledgeSourceException.Kind.UNAVAILABLE);
            }
        } finally {
            ports.close();
        }
    }

    @Test
    public void indexingWithoutKeePassFinishesWithAuthenticationFailuresInsteadOfHanging()
            throws Exception {
        Properties p = exampleProperties(false);
        AppConfig config = AppConfigLoader.fromProperties(p);
        UnavailableSecretProvider secrets = new UnavailableSecretProvider("Test ohne KeePass");
        OpenAiCompatibleEmbeddingAdapter embeddings = AdapterAssembly.embeddings(config.embedding(), secrets,
                NetworkServices.from(config.network()));
        InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
                .add("Urlaub", "Urlaubsregelung", "Urlaub wird im Portal beantragt.")
                .add("Reisen", "Reisekosten", "Reisekosten werden über das Formular RK-1 abgerechnet.");
        ApplicationPorts ports = ApplicationPorts.builder()
                .chat(new FakeChatCompletionPort(0L))
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(new InMemoryKnowledgeIndex())
                .sources(new KnowledgeSourceCatalog(Arrays.asList(
                        new KnowledgeSourceRegistration(wiki, SourceScope.of("Urlaub", "Reisen")))))
                .secrets(secrets)
                .build();
        final CompositionRoot root = CompositionRoot.compose(config, ports, SwingUtilities::invokeLater,
                System::currentTimeMillis, null);
        try {
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            final AtomicReference<IndexingReport> finished = new AtomicReference<IndexingReport>();
            final InMemoryKnowledgeSource source = wiki;
            // Statuszeile und Vollindexierung gehören zur Schale (ShellAssembly baut sie ebenso).
            final KnowledgeStatusModel knowledgeStatus = new KnowledgeStatusModel();
            final KnowledgeIndexingBinding indexingBinding = new KnowledgeIndexingBinding(root.indexing(),
                    knowledgeStatus, root.uiExecutor(), root.workExecutor(), root.clock(), root.zone());
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    indexingBinding.indexSource(source, SourceScope.of("Urlaub", "Reisen"), r -> {
                        finished.set(r);
                        done.countDown();
                    });
                }
            });
            assertTrue("Indexierung muss ohne Secret abschließen statt als laufend hängen zu bleiben",
                    done.await(10, TimeUnit.SECONDS));
            IndexingReport report = finished.get();
            assertEquals(2, report.discovered());
            assertEquals(2, report.count(IndexingStatus.FAILED));
            for (ResourceIndexingOutcome outcome : report.outcomes()) {
                assertEquals(IndexingStage.EMBEDDING, outcome.stage());
                assertTrue(outcome.message(), outcome.message().startsWith("AUTHENTICATION"));
            }
            final AtomicReference<String> status = new AtomicReference<String>();
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    status.set(knowledgeStatus.getText());
                }
            });
            assertTrue(status.get(), status.get().contains("2 fehlgeschlagen"));
            assertFalse(knowledgeStatus.isRunning());
        } finally {
            root.shutdown().run();
        }
    }

    private static KnowledgeSourcePort unwrap(KnowledgeSourcePort port) {
        return ((LoggingKnowledgeSource) port).delegate();
    }
}
