package com.aresstack.enterpriseai.integration.agent;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.Cancel;
import com.agentclientprotocol.sdk.annotation.Initialize;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.aresstack.enterpriseai.acp.api.AcpEndpointDescriptor;
import com.aresstack.enterpriseai.app.agent.AgentMcpEnvironment;
import com.aresstack.enterpriseai.application.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpToolClientFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testagent für Slice G: ein ACP-Agent (STDIO), der jede Frage über das MCP-Werkzeug {@code search_knowledge}
 * in der Wissensbasis des Hosts nachschlägt und die Treffer als Antwort streamt. Er ist Testcode dieses Moduls
 * und läuft als eigener Kindprozess über den Test-Klassenpfad (siehe {@code SliceSupport.knowledgeAgentLaunchSpec}).
 *
 * <p>Regeln aus AP17/AP19/AP21: STDOUT trägt ausschließlich ACP, Logs gehen auf STDERR. Den MCP-Endpoint erfährt
 * der Agent wie in der Produktion über die Umgebungsvariablen {@code ENTERPRISE_AI_MCP_*}
 * ({@link AgentMcpEnvironment}); MCP spricht er nur über den Port {@link McpToolClientFactory} und den Adapter
 * {@link SolonMcpToolClientFactory}, ohne eigenes Wire-Format. Weder die Endpoint-URL (sie trägt den Token im
 * Pfad) noch der Token werden je ausgegeben, nicht auf STDERR und nicht in Nachrichten an den Host.
 */
@AcpAgent(name = "enterprise-ai-knowledge-test-agent", version = "0.1")
public final class KnowledgeDemoAgentMain {

    /** Antwort, wenn der Host keinen MCP-Endpoint übergeben hat. */
    public static final String NO_KNOWLEDGE_BASE = "Keine Wissensbasis verfügbar.";
    /** Antwort, wenn der Werkzeugaufruf scheitert (bewusst ohne Details der Bibliothek). */
    public static final String KNOWLEDGE_BASE_UNREACHABLE = "Die Wissensbasis ist nicht erreichbar.";
    /** Antwort ohne Treffer. */
    public static final String NOTHING_FOUND = "Dazu habe ich nichts in der Wissensbasis gefunden.";
    /** Präfix der Überlegung, die vor der Suche an den Host geht. */
    public static final String THOUGHT_PREFIX = "Suche in der Wissensbasis nach: ";
    /** Erste Nachricht einer Antwort mit Treffern, gefolgt von der Trefferzahl. */
    public static final String FOUND_PREFIX = "Gefunden: ";

    static final int MAX_RESULTS = 3;

    private final AcpEndpointDescriptor endpoint;
    private final McpToolClientFactory clients;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger sessions = new AtomicInteger();

    KnowledgeDemoAgentMain(AcpEndpointDescriptor endpoint, McpToolClientFactory clients) {
        this.endpoint = endpoint;
        this.clients = clients;
    }

    public static void main(String[] args) {
        // Erst den Transport bauen: er greift sich das echte STDOUT. Danach gehört System.out niemandem mehr,
        // alles, was Bibliotheken dorthin schreiben würden, landet auf STDERR.
        StdioAcpAgentTransport transport = new StdioAcpAgentTransport();
        System.setOut(System.err);
        log("starting");
        AcpEndpointDescriptor endpoint = AgentMcpEnvironment.read(System.getenv());
        log(endpoint == null ? "no MCP endpoint in environment"
                : "MCP endpoint '" + endpoint.getEndpointId() + "' via " + endpoint.getTransport());
        KnowledgeDemoAgentMain agent = new KnowledgeDemoAgentMain(endpoint,
                new SolonMcpToolClientFactory(Duration.ofSeconds(15), Duration.ofSeconds(30)));
        AcpAgentSupport.create(agent).transport(transport).build().run();
        log("terminated");
    }

    @Initialize
    public AcpSchema.InitializeResponse initialize() {
        log("initialize");
        return AcpSchema.InitializeResponse.ok();
    }

    @NewSession
    public AcpSchema.NewSessionResponse newSession() {
        log("new session");
        return new AcpSchema.NewSessionResponse("knowledge-session-" + sessions.incrementAndGet(), null, null);
    }

    @Cancel
    public void cancel() {
        log("cancel received");
        cancelled.set(true);
    }

    @Prompt
    public AcpSchema.PromptResponse prompt(SyncPromptContext ctx, AcpSchema.PromptRequest request) {
        cancelled.set(false);
        String question = request.text() == null ? "" : request.text().trim();
        log("prompt received (" + question.length() + " chars)");
        if (endpoint == null) {
            ctx.sendMessage(NO_KNOWLEDGE_BASE);
            return AcpSchema.PromptResponse.endTurn();
        }
        ctx.sendThought(THOUGHT_PREFIX + question);

        String output;
        McpToolClient client = null;
        try {
            client = clients.connect(endpoint.getUrl(), endpoint.getTransport());
            Map<String, Object> arguments = new LinkedHashMap<String, Object>();
            arguments.put("query", question);
            arguments.put("max_results", MAX_RESULTS);
            output = client.callTool(KnowledgeMcpTools.SEARCH_KNOWLEDGE, arguments);
        } catch (McpToolCallException e) {
            // Nur die Art des Fehlers: Meldungen der Bibliothek könnten die URL samt Token enthalten.
            log("search_knowledge failed" + (e.isEndpointUnavailable() ? " (endpoint unavailable)" : ""));
            ctx.sendMessage(KNOWLEDGE_BASE_UNREACHABLE);
            return AcpSchema.PromptResponse.endTurn();
        } catch (RuntimeException e) {
            log("search_knowledge failed: " + e.getClass().getSimpleName());
            ctx.sendMessage(KNOWLEDGE_BASE_UNREACHABLE);
            return AcpSchema.PromptResponse.endTurn();
        } finally {
            if (client != null) {
                client.close();
            }
        }
        if (cancelled.get()) {
            return new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED);
        }

        List<SearchKnowledgeOutput.Hit> hits = SearchKnowledgeOutput.parse(output);
        log("search_knowledge returned " + hits.size() + " hit(s)");
        if (hits.isEmpty()) {
            ctx.sendMessage(NOTHING_FOUND);
            return AcpSchema.PromptResponse.endTurn();
        }
        ctx.sendMessage(FOUND_PREFIX + hits.size() + (hits.size() == 1 ? " Treffer." : " Treffer."));
        int number = 1;
        for (SearchKnowledgeOutput.Hit hit : hits) {
            if (cancelled.get()) {
                return new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED);
            }
            ctx.sendMessage("\n[" + number++ + "] " + hit.title() + ": " + hit.snippet());
        }
        return AcpSchema.PromptResponse.endTurn();
    }

    private static void log(String message) {
        System.err.println("[knowledge-agent] " + message);
    }
}
