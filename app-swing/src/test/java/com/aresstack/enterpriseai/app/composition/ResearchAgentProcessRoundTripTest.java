package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.app.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.app.agent.ChatPortMainModelChat;
import com.aresstack.enterpriseai.app.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.app.agent.ResearchControlTools;
import com.aresstack.enterpriseai.application.agent.AgentFailure;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.agent.AgentTurnListener;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Der eingebaute Recherche-Agent läuft als eigener Prozess über ACP (askai-java8 arch: ResearchAgentMain): der
 * echte Kindprozess verbindet den Research-Control-Endpoint auf dem echten Solon-Server, holt seine Antwort über
 * {@code main_model_complete} vom Chat-Port des Hosts und schickt sie über ACP zurück.
 */
public class ResearchAgentProcessRoundTripTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SolonMcpServerRuntime mcp;
    private ExecutorService starts;
    private AgentService service;

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
        if (starts != null) {
            starts.shutdownNow();
        }
        if (mcp != null) {
            mcp.shutdown();
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    @Test
    public void builtInAgentAnswersThroughTheHostModelOverAcp() throws Exception {
        String classPath = System.getProperty("research.agent.classpath");
        assumeTrue("research agent class path not set (run via Gradle)", classPath != null);
        mcp = new SolonMcpServerRuntime();
        AgentBackend backend = AdapterAssembly.researchAgent(mcp);
        List<String> args = new ArrayList<String>(backend.launchSpec().getArgs());
        args.set(args.indexOf("-cp") + 1, classPath);
        assertEquals(AdapterAssembly.RESEARCH_AGENT_MAIN, args.get(args.size() - 1));

        ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("research"),
                FileResearchScopeDraftStore::new, directory -> new ConceptBranchService(new FileConceptStore(directory)));
        FakeChatCompletionPort chat = new FakeChatCompletionPort();
        for (int i = 0; i < 8; i++) {
            chat.enqueueAnswer("{\"assistantMessage\":\"Hallo! Worum soll es in der Recherche gehen?\"}");
        }
        AcpAgentLauncher launcher = new AcpAgentLauncher(backend.connector(),
                new AgentLaunchSpec(backend.launchSpec().getCommand(), args, null), backend.registry(),
                backend.endpoint(), ResearchControlTools.toolsFor(host, new ChatPortMainModelChat(chat,
                ChatOptions.defaults())));
        starts = Executors.newSingleThreadExecutor();
        service = new AgentService(launcher, starts);

        final StringBuilder reply = new StringBuilder();
        final AtomicReference<String> end = new AtomicReference<String>();
        final CountDownLatch done = new CountDownLatch(1);
        service.send("Ich möchte über Batch-Verarbeitung recherchieren.", new AgentTurnListener() {
            @Override
            public void onMessage(String text) {
                synchronized (reply) {
                    reply.append(text);
                }
            }

            @Override
            public void onThought(String text) {
            }

            @Override
            public void onCompleted() {
                end.set("completed");
                done.countDown();
            }

            @Override
            public void onCancelled() {
                end.set("cancelled");
                done.countDown();
            }

            @Override
            public void onFailed(AgentFailure failure) {
                end.set("failed " + failure);
                done.countDown();
            }
        });
        assertTrue("agent turn did not end", done.await(120, TimeUnit.SECONDS));
        assertEquals("completed", end.get());
        assertFalse("the agent process asked the host model", chat.requests().isEmpty());
        assertNotNull(reply.toString());
        assertFalse("the agent sent a visible message", reply.toString().trim().isEmpty());
        assertTrue("the greeting is recorded by the host", host.isGreeted());
    }
}
