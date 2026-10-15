package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.agent.KnowledgeTermLookupTool;
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
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reiter „Tools“ → laufender Agent: ein in den Einstellungen abgeschaltetes Werkzeug verschwindet nach dem Speichern
 * aus dem Werkzeugsatz des Agenten-Endpoints ({@code updateTools}, beim Agenten {@code tools/list_changed}), ohne
 * Neustart des Agenten, und kommt nach dem Wiedereinschalten zurück.
 */
public class AgentToolSettingsTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** Jeder veröffentlichte Werkzeugsatz in Reihenfolge. */
    private final List<List<String>> published = new CopyOnWriteArrayList<List<String>>();

    @Test
    public void savedToolSwitchReachesTheRunningAgentEndpoint() throws Exception {
        final AtomicReference<Properties> file = new AtomicReference<Properties>(
                TestConfigs.base(temp.getRoot().toPath()));
        SettingsService settings = new SettingsService(AppConfigLoader.fromProperties(file.get()),
                () -> AppConfigLoader.fromProperties(file.get()));
        CompositionRoot root = CompositionRoot.compose(settings, ports(), Runnable::run, System::currentTimeMillis,
                ZoneId.of("Europe/Berlin"));
        try {
            root.agentService().send("Hallo", new SilentTurn());
            awaitPublished(1);
            assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_TREE, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, KnowledgeMcpTools.KNOWLEDGE_RESOLVE_URL,
                KnowledgeTermLookupTool.NAME, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ),
                    last());

            file.get().setProperty("tools.disabled", KnowledgeMcpTools.KNOWLEDGE_SEARCH);
            settings.reload();
            assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_TREE, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, KnowledgeMcpTools.KNOWLEDGE_RESOLVE_URL,
                KnowledgeTermLookupTool.NAME, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ), last());

            int before = published.size();
            file.get().remove("tools.disabled");
            settings.reload();
            assertTrue("neu veröffentlicht", published.size() > before);
            assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_TREE, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, KnowledgeMcpTools.KNOWLEDGE_RESOLVE_URL,
                KnowledgeTermLookupTool.NAME, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ),
                    last());
        } finally {
            root.agentService().close();
            root.shutdown().run();
        }
    }

    private List<String> last() {
        return published.get(published.size() - 1);
    }

    private void awaitPublished(int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (published.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue("Agent gestartet und Werkzeuge veröffentlicht", published.size() >= count);
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
                .agent(new AgentBackend(new RunningAgent(),
                        new AgentLaunchSpec("fake-agent", Collections.<String>emptyList(), null),
                        new RecordingRegistry(), new McpEndpointDefinition("agent-tools", "Wissenswerkzeuge")))
                .build();
    }

    /** Merkt sich jeden Werkzeugsatz, den der Launcher oder ein Phasenwechsel veröffentlicht. */
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

    /** Ein Agent ohne Prozess, dessen Auftrag läuft, bis der Test den Agent-Modus schließt. */
    private static final class RunningAgent implements AcpAgentConnector {
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
                            return "session-1";
                        }

                        @Override
                        public AcpSessionState getState() {
                            return AcpSessionState.ACTIVE;
                        }

                        @Override
                        public PromptHandle prompt(String text, AcpUpdateListener listener) {
                            return new PromptHandle() {
                                @Override
                                public String getPromptId() {
                                    return "prompt-1";
                                }

                                @Override
                                public AcpPromptState getState() {
                                    return AcpPromptState.RUNNING;
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

    private static final class SilentTurn implements AgentTurnListener {
        @Override
        public void onMessage(String text) {
        }

        @Override
        public void onThought(String text) {
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void onCancelled() {
        }

        @Override
        public void onFailed(AgentFailure failure) {
        }
    }
}
