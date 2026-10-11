package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.app.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.app.agent.AgentModelChoice;
import com.aresstack.enterpriseai.app.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.app.agent.ChatPortMainModelChat;
import com.aresstack.enterpriseai.app.agent.ResearchControlTools;
import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.config.AgentConfig;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.config.KnowledgeConfig;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.app.settings.SettingsService;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.application.agent.AgentLauncher;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeRelations;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.RefreshKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.application.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.application.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.application.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.application.rag.RagChatUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.application.resource.InMemoryResourceArchive;
import com.aresstack.enterpriseai.application.resource.MediatedKnowledgeDocuments;
import com.aresstack.enterpriseai.application.resource.MediatedResourceService;
import com.aresstack.enterpriseai.application.resource.policy.ActorIdentity;
import com.aresstack.enterpriseai.application.resource.policy.ActorType;
import com.aresstack.enterpriseai.application.resource.policy.ClientResourceAccessPolicy;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import com.aresstack.enterpriseai.resource.holkas.HolkasAcquisitionPort;
import com.aresstack.enterpriseai.resource.holkas.KnowledgeSourceConnectorRegistry;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
    /** Chat: so viele Treffer der Live-Suche je Frage höchstens laden und indexieren (MainframeMate-Vorabladen). */
    static final int LIVE_SEARCH_PAGES = 4;
    /** Ordner der Research-Projekte des eingebauten Recherche-Agenten, neben der Konfigurationsdatei. */
    static final String RESEARCH_DIRECTORY = "research";

    private final SettingsService settings;
    private final ApplicationPorts ports;
    private final ChatService chatService;
    private final RetrieveKnowledgeUseCase retrieval;
    private final PromptContextAssembler contextAssembler;
    private final RagChatUseCase ragChat;
    private final IndexKnowledgeUseCase indexing;
    private final MediatedKnowledgeDocuments resourceDocuments;
    private final LoadKnowledgeDocumentUseCase documents;
    private final RefreshKnowledgeSourceUseCase refresh;
    private final KnowledgeMcpTools knowledgeTools;
    private final AgentService agentService;
    private final ResearchAgentHost researchHost;
    private final AgentModelChoice agentModelChoice;
    private final ExecutorService agentStarts;
    private final ExecutorService workExecutor;
    private final Executor uiExecutor;
    private final LongSupplier clock;
    private final ZoneId zone;
    private final KnowledgeStatusModel knowledgeStatus;
    private final KnowledgeIndexingBinding indexingBinding;
    private final KnowledgeSourceSelection sourceSelection;
    /** Die angebundenen Quellen von jetzt; ersetzt, nie verändert (siehe {@link #publishSources}). */
    private volatile KnowledgeSourceCatalog liveSources;
    private final SearchDrivenIndexingUseCase searchIndexing;
    private final ShutdownSequence shutdown;

    private CompositionRoot(final SettingsService settings, final ApplicationPorts ports, Executor uiExecutor,
                            LongSupplier clock, ZoneId zone) {
        this.settings = settings;
        AppConfig config = settings.current();
        this.ports = ports;
        this.uiExecutor = uiExecutor;
        this.clock = clock;
        this.zone = zone;
        KnowledgeConfig knowledge = config.knowledge();

        this.chatService = new ChatService(ports.chat(), config.chat().defaultOptions());
        // Die Embedding-Welt als Funktion: ein anderes Embedding-Modell gilt ohne Neustart (eigener Namensraum).
        // Nachbarschaft (ähnliche Absätze, Nachbarabsätze, Abschnitt, Elternseite) aus dem Index abgeleitet;
        // Schwelle und Anzahl gelten ohne Neustart.
        this.retrieval = new RetrieveKnowledgeUseCase(ports.index(), ports.embeddings(), ports.embeddingSpaceSource(),
                knowledge.retrieval()).withRelations(new IndexKnowledgeRelations(ports.index()),
                settings.get(c -> c.knowledge().retrieval()));
        this.contextAssembler = new PromptContextAssembler(knowledge.context());
        this.indexing = new IndexKnowledgeUseCase(ports.index(), ports.embeddings(), ports.embeddingSpaceSource(),
                new KnowledgeChunker(knowledge.chunking()), knowledge.embeddingBatchSize());
        // Lesen lädt aus der Quelle, die die ID kennt (knowledge_read); das Gelesene kommt danach in den Index.
        // Gelesen wird nach corenth vermittelt: Tamias (Regel) → Chalcotheca (Archiv) → AcquisitionPort → Holkas-
        // Connector der Quelle; der Use Case fragt den Quellen-Port nicht mehr direkt.
        // Die angebundenen Quellen von jetzt: zuerst die aus der Konfiguration, danach meldet der Reiter
        // „Wissensquellen“ jede Änderung (publishSources); Lesen, Chat und knowledge_search fragen je Aufruf.
        this.liveSources = ports.sources();
        MediatedResourceService resourceAccess = new MediatedResourceService(new ClientResourceAccessPolicy(),
                new HolkasAcquisitionPort(new KnowledgeSourceConnectorRegistry(() -> liveSources.ports(), clock)),
                new InMemoryResourceArchive());
        this.resourceDocuments = new MediatedKnowledgeDocuments(resourceAccess,
                new ActorIdentity("enterprise-ai-client-user", ActorType.HUMAN));
        this.documents = new LoadKnowledgeDocumentUseCase(ports.sources())
                .withReader(resourceDocuments).withCatalog(() -> liveSources);
        this.refresh = new RefreshKnowledgeSourceUseCase(indexing, ports.sources());
        // Suche füllt den Index (MainframeMate): Quellen live durchsuchen, nur neue oder geänderte Treffer laden,
        // zerlegen und einbetten (Revision, dann Inhalts-Hash). Kein Vollindex beim Start.
        this.searchIndexing = new SearchDrivenIndexingUseCase(indexing);
        this.ragChat = new RagChatUseCase(chatService, retrieval, contextAssembler, (query, sources) -> {
            List<KnowledgeSourcePort> chosen = new ArrayList<KnowledgeSourcePort>();
            for (KnowledgeSourceRegistration registration : liveSources.registrations()) {
                if (sources.isEmpty() || sources.contains(registration.sourceId())) {
                    chosen.add(registration.port());
                }
            }
            SearchDrivenIndexingUseCase.Result result = searchIndexing.searchAndIndex(chosen, query,
                    LIVE_SEARCH_PAGES, LIVE_SEARCH_PAGES);
            if (!result.failures().isEmpty()) {
                LOG.info("Live-Suche für den Chat: " + result.failures());
            }
        });
        this.sourceSelection = new KnowledgeSourceSelection();
        for (KnowledgeSourceRegistration registration : ports.sources().registrations()) {
            boolean enabled = true; // eine angebundene Quelle ohne Eintrag in der Konfiguration bleibt angehakt
            for (SourceDefinition source : config.sources()) {
                if (source.id().equals(registration.sourceId().value())) {
                    enabled = source.enabled();
                }
            }
            sourceSelection.register(registration.sourceId(), enabled);
        }
        // Nur zwei Werkzeuge (knowledge_search, knowledge_read); durchsucht werden nur die angehakten Quellen.
        final KnowledgeSourceSelection selection = sourceSelection;
        this.knowledgeTools = new KnowledgeMcpTools(retrieval, documents, searchIndexing,
                id -> !selection.isRestricted() || selection.isEnabled(id), config.agent().toolSettings());
        this.workExecutor = Executors.newCachedThreadPool(daemonThreads("enterprise-ai-work"));
        this.knowledgeStatus = new KnowledgeStatusModel();
        this.indexingBinding = new KnowledgeIndexingBinding(indexing, knowledgeStatus, uiExecutor, workExecutor,
                clock, zone);

        this.agentStarts = Executors.newSingleThreadExecutor(daemonThreads("enterprise-ai-agent-start"));
        // Der eingebaute Recherche-Agent aus askai-java8 (arch) samt Weidezaun, Projekte neben der Konfiguration.
        // Ist ein eigenes Agent-Kommando eingeschaltet, startet stattdessen dieses; entschieden wird je
        // Agent-Sitzung aus den aktuellen Einstellungen, ein Neustart ist nicht nötig.
        this.researchHost = new ResearchAgentHost(AppPaths.configFile().toAbsolutePath().getParent()
                .resolve(RESEARCH_DIRECTORY).toFile(), FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        this.agentModelChoice = new AgentModelChoice();
        final ChatPortMainModelChat researchModel = new ChatPortMainModelChat(ports.chat(),
                config.chat().defaultOptions(), agentModelChoice);
        this.agentService = new AgentService(new AgentLauncher() {
            @Override
            public AcpConnection launch() throws AcpException {
                AgentBackend external = ports.agentFor(settings.current().agent());
                if (external != null) {
                    return new AcpAgentLauncher(external.connector(), external.launchSpec(), external.registry(),
                            external.endpoint(), agentTools()).launch();
                }
                // Der eingebaute Recherche-Agent wie in askai-java8 arch: eigener Prozess über ACP, die
                // Research-Steuerung, das Konzeptpapier und das Hauptmodell über seinen MCP-Endpoint.
                AgentBackend research = ports.researchAgent();
                if (research == null) {
                    throw new AcpException(AcpException.Phase.SPAWN, "research agent is not available", null);
                }
                List<McpToolContribution> tools = new ArrayList<McpToolContribution>(
                        ResearchControlTools.toolsFor(researchHost, researchModel));
                tools.addAll(agentTools());
                return new AcpAgentLauncher(research.connector(), research.launchSpec(), research.registry(),
                        research.endpoint(), tools).launch();
            }
        }, agentStarts);
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
        if (config == null) {
            throw new IllegalArgumentException("config, ports, uiExecutor and clock must not be null");
        }
        return compose(SettingsService.fixed(config), ports, uiExecutor, clock, zone);
    }

    /** Wie oben mit dem Settings-Service der Anwendung: {@link #config()} liefert dann immer den aktuellen Stand. */
    public static CompositionRoot compose(SettingsService settings, ApplicationPorts ports, Executor uiExecutor,
                                          LongSupplier clock, ZoneId zone) {
        if (settings == null || ports == null || uiExecutor == null || clock == null) {
            throw new IllegalArgumentException("settings, ports, uiExecutor and clock must not be null");
        }
        return new CompositionRoot(settings, ports, uiExecutor, clock, zone == null ? ZoneId.systemDefault() : zone);
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

    /**
     * Startet die Hintergrundarbeit. Eine Indexierung beim Start gibt es nicht mehr: Die Suche (Drawer, Chat,
     * {@code knowledge_search}) füllt den Index mit ihren Treffern; ganze Quellen nur auf Knopfdruck. Idempotent.
     */
    public void startBackgroundWork() {
        // bewusst leer
    }

    /** Der aktuelle Stand der Einstellungen, bei jedem Aufruf frisch (nicht in Feldern ablegen). */
    public AppConfig config() {
        return settings.current();
    }

    /** Der Settings-Service der Anwendung: frisch lesen über {@code current()}, Änderungen über Beobachter. */
    public SettingsService settings() {
        return settings;
    }

    /** {@code true}, solange kein eigenes Agent-Kommando eingeschaltet ist (eingebauter Recherche-Agent). */
    public boolean usesBuiltInAgent() {
        AgentConfig agent = config().agent();
        return ports.agentFor(agent) == null;
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

    /** Der vermittelte Lesezugriff auf Wissensdokumente (corenth-Ressourcenschicht) mit seinem Archiv. */
    public MediatedKnowledgeDocuments resourceDocuments() {
        return resourceDocuments;
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

    /** Der Agent-Modus: das konfigurierte Agent-Kommando, sonst der eingebaute Recherche-Agent. */
    public AgentService agentService() {
        return agentService;
    }

    public boolean hasAgent() {
        return agentService != null;
    }

    /** Weidezaun des eingebauten Recherche-Agenten. */
    public ResearchAgentHost researchHost() {
        return researchHost;
    }

    /** Modell und Denkaufwand aus dem Composer des eingebauten Recherche-Agenten. */
    public AgentModelChoice agentModelChoice() {
        return agentModelChoice;
    }

    /** Die Häkchen der Wissensquellen: Start-Indexierung und RAG richten sich danach. */
    public KnowledgeSourceSelection sourceSelection() {
        return sourceSelection;
    }

    /**
     * Die angebundenen Quellen haben sich geändert (hinzugefügt, entfernt, neu verbunden): Lesen, Chat-RAG und
     * {@code knowledge_search} nutzen ab dem nächsten Aufruf diese Liste, ohne Neustart. Welche davon durchsucht
     * werden, sagen weiter die Häkchen ({@link #sourceSelection()}).
     */
    public void publishSources(Collection<KnowledgeSourceRegistration> registrations) {
        liveSources = new KnowledgeSourceCatalog(registrations == null
                ? Collections.<KnowledgeSourceRegistration>emptyList() : registrations);
    }

    /** Die angebundenen Quellen von jetzt. */
    public KnowledgeSourceCatalog liveSources() {
        return liveSources;
    }

    /** Live-Suche, die ihre Treffer indexiert (Drawer, Chat, Agent). */
    public SearchDrivenIndexingUseCase searchIndexing() {
        return searchIndexing;
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
                // Eine vom Nutzer gestartete Vollindexierung zwischen zwei Ressourcen anhalten; der Index wird danach
                // geschlossen und weist weitere Schreibzugriffe ab.
                try {
                    uiExecutor.execute(knowledgeStatus::requestCancel);
                } catch (RuntimeException e) {
                    // UI schon fort: nichts anzuhalten
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
