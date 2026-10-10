package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.KnowledgeConfig;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.app.knowledge.StartupIndexing;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.RefreshKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.application.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.application.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.application.rag.RagChatUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Verbindet Ports mit Use Cases, den Bindings aus AP22 (RAG, Indexierung mit Statuszeile), dem Agent-Modus
 * (AP21 mit den Wissenswerkzeugen aus AP20) und der Hintergrundarbeit und besitzt die {@link ShutdownSequence}.
 * Enthält keine Swing-Typen, damit der Kompositionstest headless läuft; die Oberfläche hängt
 * {@link ShellAssembly} an. Der UI-Executor ist produktiv {@code SwingUtilities::invokeLater}, der
 * Arbeits-Executor ein eigener Pool (Suche und Indexierung blockieren, nie auf dem EDT).
 *
 * <p>Shutdown-Reihenfolge (Auftrag AP23): laufende Turns abbrechen, Agent-Modus beenden (Endpoint abgemeldet,
 * Token ungültig), Hintergrund-Indexierung stoppen, MCP-Server und Index schließen (über
 * {@link ApplicationPorts#close()} in dieser Reihenfolge), zuletzt die Executor. Jeder Schritt läuft genau
 * einmal, auch wenn Fenster-Schließen und JVM-Shutdown-Hook beide zugreifen.
 */
public final class CompositionRoot {

    private static final Logger LOG = Logger.getLogger(CompositionRoot.class.getName());
    /** Wartezeit beim Beenden auf eine laufende Indexierung (danach wird der Index geschlossen). */
    static final int INDEXING_SHUTDOWN_WAIT_SECONDS = 10;

    private final AppConfig config;
    private final ApplicationPorts ports;
    private final ChatService chatService;
    private final RetrieveKnowledgeUseCase retrieval;
    private final PromptContextAssembler contextAssembler;
    private final RagChatUseCase ragChat;
    private final IndexKnowledgeUseCase indexing;
    private final LoadKnowledgeDocumentUseCase documents;
    private final RefreshKnowledgeSourceUseCase refresh;
    private final KnowledgeMcpTools knowledgeTools;
    private final AgentService agentService;
    private final ExecutorService agentStarts;
    private final ExecutorService workExecutor;
    private final Executor uiExecutor;
    private final LongSupplier clock;
    private final ZoneId zone;
    private final KnowledgeStatusModel knowledgeStatus;
    private final KnowledgeIndexingBinding indexingBinding;
    private final KnowledgeSourceSelection sourceSelection;
    private final StartupIndexing startupIndexing;
    private final ShutdownSequence shutdown;

    private CompositionRoot(AppConfig config, ApplicationPorts ports, Executor uiExecutor, LongSupplier clock,
                            ZoneId zone) {
        this.config = config;
        this.ports = ports;
        this.uiExecutor = uiExecutor;
        this.clock = clock;
        this.zone = zone;
        KnowledgeConfig knowledge = config.knowledge();

        this.chatService = new ChatService(ports.chat(), config.chat().defaultOptions());
        this.retrieval = new RetrieveKnowledgeUseCase(ports.index(), ports.embeddings(), ports.embeddingSpace(),
                knowledge.retrieval());
        this.contextAssembler = new PromptContextAssembler(knowledge.context());
        this.ragChat = new RagChatUseCase(chatService, retrieval, contextAssembler);
        this.indexing = new IndexKnowledgeUseCase(ports.index(), ports.embeddings(), ports.embeddingSpace(),
                new KnowledgeChunker(knowledge.chunking()), knowledge.embeddingBatchSize());
        // Der Index führt (AP20-Folge #32): get_knowledge_document liefert nur Dokumente, die im konfigurierten
        // Namespace indexiert sind; der Agent sieht ausschließlich den freigegebenen Korpus.
        this.documents = new LoadKnowledgeDocumentUseCase(ports.sources(), ports.index(), ports.embeddingSpace());
        this.refresh = new RefreshKnowledgeSourceUseCase(indexing, ports.sources());
        this.knowledgeTools = new KnowledgeMcpTools(retrieval, documents, refresh, config.agent().toolSettings());
        this.workExecutor = Executors.newCachedThreadPool(daemonThreads("enterprise-ai-work"));
        this.knowledgeStatus = new KnowledgeStatusModel();
        this.indexingBinding = new KnowledgeIndexingBinding(indexing, knowledgeStatus, uiExecutor, workExecutor,
                clock, zone);
        this.sourceSelection = new KnowledgeSourceSelection();
        for (KnowledgeSourceRegistration registration : ports.sources().registrations()) {
            boolean enabled = true; // eine angebundene Quelle ohne Eintrag in der Konfiguration bleibt angehakt
            for (SourceConfig source : config.sources()) {
                if (source.sourceId().equals(registration.sourceId())) {
                    enabled = source.enabled();
                }
            }
            sourceSelection.register(registration.sourceId(), enabled);
        }
        this.startupIndexing = new StartupIndexing(indexingBinding, knowledgeStatus, ports.sources(), uiExecutor,
                sourceSelection);

        if (ports.hasAgent()) {
            AgentBackend agent = ports.agent();
            this.agentStarts = Executors.newSingleThreadExecutor(daemonThreads("enterprise-ai-agent-start"));
            this.agentService = new AgentService(new AcpAgentLauncher(agent.connector(), agent.launchSpec(),
                    agent.registry(), agent.endpoint(), agentTools()), agentStarts);
        } else {
            this.agentStarts = null;
            this.agentService = null;
        }
        this.shutdown = buildShutdown();
    }

    /**
     * Baut den Graphen.
     *
     * @param uiExecutor führt Änderungen an den Shell-Modellen aus (produktiv {@code SwingUtilities::invokeLater})
     * @param clock      Epoch-Millisekunden für Zeitstempel in der Shell
     * @param zone       Zeitzone der Zeitstempel ({@code null}: Systemzone)
     */
    public static CompositionRoot compose(AppConfig config, ApplicationPorts ports, Executor uiExecutor,
                                          LongSupplier clock, ZoneId zone) {
        if (config == null || ports == null || uiExecutor == null || clock == null) {
            throw new IllegalArgumentException("config, ports, uiExecutor and clock must not be null");
        }
        return new CompositionRoot(config, ports, uiExecutor, clock, zone == null ? ZoneId.systemDefault() : zone);
    }

    private static ThreadFactory daemonThreads(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            }
        };
    }

    /** Die Werkzeuge am MCP-Endpoint des Agenten: AP20-Wissenswerkzeuge; weitere Beiträge hier ergänzen. */
    List<McpToolContribution> agentTools() {
        return new ArrayList<McpToolContribution>(knowledgeTools.contributions());
    }

    /** Startet die Hintergrundarbeit (Indexierung, falls konfiguriert). Idempotent. */
    public void startBackgroundWork() {
        if (config.knowledge().indexOnStartup() && !ports.sources().isEmpty()) {
            startupIndexing.start();
        }
    }

    public AppConfig config() {
        return config;
    }

    public ApplicationPorts ports() {
        return ports;
    }

    public ChatService chatService() {
        return chatService;
    }

    public RetrieveKnowledgeUseCase retrieval() {
        return retrieval;
    }

    public PromptContextAssembler contextAssembler() {
        return contextAssembler;
    }

    /** Die RAG-Variante des Chats; {@link ShellAssembly} hängt die {@code RagChatBinding} (AP22) daran. */
    public RagChatUseCase ragChat() {
        return ragChat;
    }

    public Executor uiExecutor() {
        return uiExecutor;
    }

    /** Arbeits-Executor für Suche und Indexierung (nie der UI-Thread); wird beim Beenden gestoppt. */
    public Executor workExecutor() {
        return workExecutor;
    }

    public LongSupplier clock() {
        return clock;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Statuszeile der Wissensbasis (AP22), von {@link ShellAssembly} in die Chat-Shell gehängt. */
    public KnowledgeStatusModel knowledgeStatus() {
        return knowledgeStatus;
    }

    public KnowledgeIndexingBinding indexingBinding() {
        return indexingBinding;
    }

    public IndexKnowledgeUseCase indexing() {
        return indexing;
    }

    public LoadKnowledgeDocumentUseCase documents() {
        return documents;
    }

    public RefreshKnowledgeSourceUseCase refresh() {
        return refresh;
    }

    public KnowledgeMcpTools knowledgeTools() {
        return knowledgeTools;
    }

    /** {@code null}, wenn kein Agent-Modus konfiguriert ist. */
    public AgentService agentService() {
        return agentService;
    }

    public boolean hasAgent() {
        return agentService != null;
    }

    /** Die Häkchen der Wissensquellen: Start-Indexierung und RAG richten sich danach. */
    public KnowledgeSourceSelection sourceSelection() {
        return sourceSelection;
    }

    public StartupIndexing startupIndexing() {
        return startupIndexing;
    }

    public ShutdownSequence shutdown() {
        return shutdown;
    }

    private ShutdownSequence buildShutdown() {
        ShutdownSequence sequence = new ShutdownSequence();
        sequence.then("cancel-chat-turns", new Runnable() {
            @Override
            public void run() {
                for (ChatConversationId id : chatService.conversationIds()) {
                    chatService.cancel(id);
                }
            }
        });
        sequence.then("end-agent-mode", new Runnable() {
            @Override
            public void run() {
                knowledgeTools.shutdown();
                if (agentService != null) {
                    agentService.cancel();
                    agentService.close();
                }
            }
        });
        sequence.then("stop-indexing", new Runnable() {
            @Override
            public void run() {
                startupIndexing.cancel();
                try {
                    if (!startupIndexing.awaitTermination(INDEXING_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                        // Bewusst begrenzt, damit das Beenden nicht an einem blockierten Quell- oder
                        // Embedding-Aufruf hängt (Abbruch greift nur zwischen zwei Ressourcen). Der Index wird
                        // danach geschlossen; er ist vollständig synchronisiert und weist jeden weiteren
                        // Schreibzugriff mit IllegalStateException ab, ein Schreibvorgang wird also nie halb
                        // ausgeführt, nur der noch laufende Schritt scheitert.
                        LOG.warning("Indexierung läuft beim Beenden noch nach " + INDEXING_SHUTDOWN_WAIT_SECONDS
                                + " s; der Wissensindex wird geschlossen, der laufende Schritt bricht ab");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        sequence.then("close-ports", new Runnable() {
            @Override
            public void run() {
                ports.close();
            }
        });
        sequence.then("stop-executors", new Runnable() {
            @Override
            public void run() {
                workExecutor.shutdownNow();
                if (agentStarts != null) {
                    agentStarts.shutdownNow();
                }
            }
        });
        return sequence;
    }
}
