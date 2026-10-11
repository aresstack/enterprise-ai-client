package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpSessionState;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.acp.api.PromptHandle;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentFailure;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentTurnListener;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.composition.AgentBackend;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;
import com.aresstack.enterpriseai.proasteion.exedra.settings.CatalogToolOverview;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ToolOverviewEntry;
import com.aresstack.enterpriseai.proasteion.katagogion.McpEndpointDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.McpEndpointHandle;
import com.aresstack.enterpriseai.proasteion.katagogion.McpServerRegistry;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.testkit.InProcessMcpServerRegistry;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Der eingebaute Recherche-Agent und der Reiter „Tools“: Pflichtwerkzeuge ({@code research_status},
 * {@code main_model_complete}) bleiben angeboten, auch wenn sie in {@code tools.disabled} stehen (sonst startet der
 * Agent nicht), und ein gespeicherter Schalter erreicht den laufenden Agenten ({@code updateTools}) ebenso wie einen
 * neu gestarteten.
 */
public class ResearchAgentToolActivationTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final List<List<String>> published = new CopyOnWriteArrayList<List<String>>();

    @Test
    public void requiredToolsSurviveTheSwitchAndRunningAndRestartedAgentsSeeTheSavedSet() throws Exception {
        final AtomicReference<Properties> file = new AtomicReference<Properties>(
                TestConfigs.base(temp.getRoot().toPath()));
        file.get().setProperty("tools.disabled",
                "research_status,main_model_complete," + KnowledgeMcpTools.KNOWLEDGE_SEARCH);
        SettingsService settings = new SettingsService(AppConfigLoader.fromProperties(file.get()),
                () -> AppConfigLoader.fromProperties(file.get()));
        CompositionRoot root = CompositionRoot.compose(settings, ports(), Runnable::run, System::currentTimeMillis,
                ZoneId.of("Europe/Berlin"));
        try {
            // Reiter „Tools“: Pflichtwerkzeuge aktiv und nicht abwählbar, die Wissenssuche aus.
            for (ToolOverviewEntry tool : new CatalogToolOverview(root.toolCatalog()).tools()) {
                if ("research_status".equals(tool.name()) || "main_model_complete".equals(tool.name())) {
                    assertTrue(tool.name(), tool.required());
                    assertTrue(tool.name(), tool.enabled());
                }
                if (KnowledgeMcpTools.KNOWLEDGE_SEARCH.equals(tool.name())) {
                    assertFalse(tool.required());
                    assertFalse(tool.enabled());
                }
            }

            send(root);
            List<String> first = last();
            assertTrue(first.toString(), first.contains("research_status"));
            assertTrue(first.toString(), first.contains("main_model_complete"));
            assertFalse(first.toString(), first.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH));

            // Laufender Agent: Wissenssuche eingeschaltet → neu veröffentlicht, ohne Neustart.
            file.get().setProperty("tools.disabled", "research_status,main_model_complete");
            settings.reload();
            assertTrue(last().toString(), last().contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH));
            assertTrue(last().contains("research_status"));

            // Wieder aus, dann ein neu gestarteter Agent: er bekommt den gespeicherten Satz.
            file.get().setProperty("tools.disabled", KnowledgeMcpTools.KNOWLEDGE_SEARCH);
            settings.reload();
            root.agentService().endSession();
            int before = published.size();
            send(root);
            assertTrue("neu gestartet", published.size() > before);
            assertFalse(last().toString(), last().contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH));
            assertTrue(last().contains("research_status"));
            assertTrue(last().contains("main_model_complete"));
        } finally {
            root.agentService().close();
            root.shutdown().run();
        }
    }

    private void send(CompositionRoot root) throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        root.agentService().send("Hallo", new AgentTurnListener() {
            @Override
            public void onMessage(String text) {
            }

            @Override
            public void onThought(String text) {
            }

            @Override
            public void onCompleted() {
                done.countDown();
            }

            @Override
            public void onCancelled() {
                done.countDown();
            }

            @Override
            public void onFailed(AgentFailure failure) {
                done.countDown();
            }
        });
        assertTrue("Auftrag beendet", done.await(10, TimeUnit.SECONDS));
    }

    private List<String> last() {
        assertFalse("nichts veröffentlicht", published.isEmpty());
        return published.get(published.size() - 1);
    }

    private ApplicationPorts ports() {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki").add("Seite", "Seite", "Text.");
        return ApplicationPorts.builder()
                .chat(new FakeChatCompletionPort(0L))
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(new InMemoryKnowledgeIndex())
                .sources(new KnowledgeSourceCatalog(Collections.singletonList(
                        new KnowledgeSourceRegistration(wiki, SourceScope.of("Seite")))))
                .secrets(new UnavailableSecretProvider("Test ohne KeePass"))
                .researchAgent(new AgentBackend(new CompletingAgent(),
                        new AgentLaunchSpec("research-agent", Collections.<String>emptyList(), null),
                        new RecordingRegistry(), new McpEndpointDefinition("research-control", "Recherche")))
                .build();
    }

    private final class RecordingRegistry implements McpServerRegistry {
        private final InProcessMcpServerRegistry delegate = new InProcessMcpServerRegistry();

        @Override
        public McpEndpointHandle registerEndpoint(McpEndpointDefinition definition) {
            return delegate.registerEndpoint(definition);
        }

        @Override
        public void updateTools(McpEndpointHandle handle, Collection<McpToolContribution> tools) {
            List<String> names = new ArrayList<String>();
            for (McpToolContribution tool : tools) {
                names.add(tool.getName());
            }
            published.add(names);
            delegate.updateTools(handle, tools);
        }

        @Override
        public void unregisterEndpoint(McpEndpointHandle handle) {
            delegate.unregisterEndpoint(handle);
        }

        @Override
        public String endpointUrl(McpEndpointHandle handle) {
            return delegate.endpointUrl(handle);
        }

        @Override
        public List<String> toolNames(McpEndpointHandle handle) {
            return delegate.toolNames(handle);
        }

        @Override
        public Map<String, String> toolCatalog(McpEndpointHandle handle) {
            return delegate.toolCatalog(handle);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }
    }

    /** Ein Agent ohne Prozess; jeder Auftrag endet sofort (auf eigenem Thread, wie beim Adapter). */
    private static final class CompletingAgent implements AcpAgentConnector {
        @Override
        public AcpConnection connect(AgentLaunchSpec spec) {
            return new AcpConnection() {
                private volatile boolean closed;

                @Override
                public AcpConnectionState getState() {
                    return closed ? AcpConnectionState.CLOSED : AcpConnectionState.READY;
                }

                @Override
                public AgentProcessHandle getProcess() {
                    return null;
                }

                @Override
                public AcpSession newSession() {
                    return new AcpSession() {
                        @Override
                        public String getSessionId() {
                            return "research-session";
                        }

                        @Override
                        public AcpSessionState getState() {
                            return AcpSessionState.ACTIVE;
                        }

                        @Override
                        public PromptHandle prompt(String text, final AcpUpdateListener listener) {
                            new Thread(() -> listener.onTerminal("p", AcpPromptState.COMPLETED, ""),
                                    "fake-terminal").start();
                            return new PromptHandle() {
                                @Override
                                public String getPromptId() {
                                    return "p";
                                }

                                @Override
                                public AcpPromptState getState() {
                                    return AcpPromptState.COMPLETED;
                                }

                                @Override
                                public void cancel() {
                                }
                            };
                        }

                        @Override
                        public void close() {
                        }
                    };
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }
}
