package com.aresstack.enterpriseai.acp.research;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.Cancel;
import com.agentclientprotocol.sdk.annotation.Initialize;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpToolClientFactory;
import com.aresstack.enterpriseai.research.runtime.team.PhaseAssistantProfileRegistry;
import com.aresstack.enterpriseai.research.runtime.team.PhaseContextAssembler;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Der eingebaute Recherche-Agent als eigener Java-Prozess (askai-java8 arch: {@code ResearchAgentMain}). Er
 * spiegelt den Zustand des Hosts und besitzt nie eine eigene Zustandsmaschine: der Host ist die einzige
 * Übergangsautorität, dieser Prozess ruft nur die angebotenen MCP-Werkzeuge.
 *
 * <p>Bereitschaft ist echt: {@code session/new} verbindet den Research-Control-Endpoint, listet die Werkzeuge
 * und ruft {@code research_status}; nur wenn das gelingt, entsteht die Sitzung. ACP trägt Prompt, Streaming,
 * Status und Fehler; MCP trägt die Research-Werkzeuge und das Hauptmodell ({@code main_model_complete}).
 * STDOUT gehört ACP, Logs gehen auf STDERR.
 */
@AcpAgent(name = "enterprise-ai-research-agent", version = "0.1")
public final class ResearchAgentMain {

    private static final Logger LOG = Logger.getLogger(ResearchAgentMain.class.getName());
    /** Hauptmodell-Antworten dauern; das Werkzeug wartet so lange wie eine Modellanfrage des Clients. */
    private static final Duration MCP_REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private ResearchAgentEnvironment environment;
    private ResearchHostControl host;
    private ResearchAgentTurns turns;

    public static void main(String[] args) {
        LOG.info("[research-agent] starting");
        AcpAgentSupport.create(new ResearchAgentMain())
                .transport(new StdioAcpAgentTransport())
                .build().run();
        LOG.info("[research-agent] terminated");
        // Die STDIO-Schleife ist zu Ende (Host weg oder sauber geschlossen); nicht-daemon Reste (MCP-Client)
        // dürfen die JVM nicht als Zombie am Leben halten. Exit führt die Shutdown-Hooks aus; halt ist das Netz.
        Thread reaper = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                Runtime.getRuntime().halt(0);
            }
        }, "research-agent-halt-fallback");
        reaper.setDaemon(true);
        reaper.start();
        System.exit(0);
    }

    @Initialize
    public AcpSchema.InitializeResponse initialize() {
        environment = ResearchAgentEnvironment.from(System.getenv());
        LOG.info("[research-agent] initialize " + environment); // toString zeigt nie den Token
        return AcpSchema.InitializeResponse.ok();
    }

    @NewSession
    public synchronized AcpSchema.NewSessionResponse newSession() {
        if (environment == null || !environment.hasResearchEndpoint()) {
            throw new IllegalStateException("research control endpoint is not configured");
        }
        if (host != null) {
            host.close();
        }
        McpToolClient client = new SolonMcpToolClientFactory(Duration.ofSeconds(15), MCP_REQUEST_TIMEOUT)
                .connect(environment.researchUrl(), environment.researchTransport());
        ResearchHostControl control = new ResearchHostControl(client);
        ResearchHostControl.Status status;
        boolean conceptTools;
        try {
            Map<String, String> tools = control.tools();
            if (!tools.containsKey("research_status") || !tools.containsKey("main_model_complete")) {
                throw new IllegalStateException("research control tools are not offered by the host endpoint");
            }
            conceptTools = tools.containsKey("concept_read");
            status = control.status();
        } catch (Exception unavailable) {
            control.close();
            throw new IllegalStateException("research control endpoint is not ready: " + unavailable.getMessage());
        }
        McpMainModelChat mainModel = new McpMainModelChat(control);
        mainModel.setModelName(status.model);
        final ResearchAgentTurns[] holder = new ResearchAgentTurns[1];
        final ResearchHostControl fence = control;
        ResearchTeamAgent teamAgent = new ResearchTeamAgent(mainModel,
                PhaseAssistantProfileRegistry.defaults(false, conceptTools),
                new PhaseContextAssembler(new PhaseContextAssembler.CurrentLanguage() {
                    @Override
                    public String displayName() {
                        return "German";
                    }
                }).withCurrentConcept(new PhaseContextAssembler.CurrentConcept() {
                    @Override
                    public String rendered() {
                        ResearchAgentTurns current = holder[0];
                        return current == null ? "" : current.currentConceptRendered();
                    }
                }).withCurrentScope(new PhaseContextAssembler.CurrentScope() {
                    @Override
                    public String rendered() {
                        return fence.scopeFence();
                    }
                }));
        teamAgent.setDiagnostics(new Consumer<String>() {
            @Override
            public void accept(String line) {
                LOG.info("[research-agent] " + line);
            }
        });
        holder[0] = new ResearchAgentTurns(control, teamAgent, mainModel, conceptTools);
        this.host = control;
        this.turns = holder[0];
        LOG.info("[research-agent] ready: phase=" + status.phase + " state=" + status.state
                + " model=" + status.model + " conceptTools=" + conceptTools);
        return new AcpSchema.NewSessionResponse("research-acp-" + UUID.randomUUID(), null, null);
    }

    @Cancel
    public void cancel() {
        LOG.info("[research-agent] cancel");
        cancelled.set(true);
    }

    @Prompt
    public AcpSchema.PromptResponse prompt(final SyncPromptContext ctx, AcpSchema.PromptRequest request) {
        cancelled.set(false);
        String text = request.text() == null ? "" : request.text();
        ResearchAgentTurns current;
        synchronized (this) {
            current = turns;
        }
        if (current == null) {
            ctx.sendMessage("Der Recherche-Agent hat keine Sitzung.");
            return AcpSchema.PromptResponse.endTurn();
        }
        current.run(text, new ResearchAgentTurns.Output() {
            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public void message(String message) {
                if (!cancelled.get() && message != null && !message.isEmpty()) {
                    ctx.sendMessage(message);
                }
            }
        });
        return cancelled.get()
                ? new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED)
                : AcpSchema.PromptResponse.endTurn();
    }
}
