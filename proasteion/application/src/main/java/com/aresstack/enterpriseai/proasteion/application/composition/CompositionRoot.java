package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.knowledge.RetryingSourceAccess;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentReader;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ConcurrencyLimitedEmbeddingPort;

import com.aresstack.enterpriseai.astu.acropolis.navigation.BuildDocumentNavigationUseCase;
import com.aresstack.enterpriseai.astu.acropolis.navigation.ChatSectionLabeler;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.proasteion.application.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.proasteion.application.agent.AgentModelChoice;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ChatPortMainModelChat;
import com.aresstack.enterpriseai.proasteion.application.agent.PhaseToolSetController;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
import com.aresstack.enterpriseai.proasteion.application.agent.KnowledgeTermLookupTool;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDelegationTool;
import com.aresstack.enterpriseai.proasteion.application.config.AgentConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.config.KnowledgeConfig;
import com.aresstack.enterpriseai.proasteion.application.chat.DocumentAttachmentTextExtractor;
import com.aresstack.enterpriseai.proasteion.application.knowledge.FileDocumentNavigationStore;
import com.aresstack.enterpriseai.proasteion.application.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.proasteion.application.speech.IndexSentenceSegmenter;
import com.aresstack.enterpriseai.proasteion.application.speech.NlpModels;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.tika.DocumentExtraction;
import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;
import com.aresstack.enterpriseai.proasteion.application.knowledge.FactCatalogTextFindings;
import com.aresstack.enterpriseai.proasteion.application.knowledge.FactCatalogStatements;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymExtractor;
import com.aresstack.enterpriseai.astu.acropolis.textmining.DocumentAcronymsUseCase;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ChatStatementExtractor;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ChatStatementVerifier;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.DocumentStatementsUseCase;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.PosPredicateCheck;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementAnalyzer;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentLauncher;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeRelations;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.RefreshKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTools;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.OneTimeCodePrompt;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SourceAttachmentReader;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.UnlockingDocumentReader;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeToolSettings;
import com.aresstack.enterpriseai.proasteion.application.knowledge.DocumentSourceFileTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.astu.acropolis.rag.FollowUpResearch;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalTriage;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagChatUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.MediatedKnowledgeDocuments;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.tamias.ClientResourceAccessPolicy;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.proasteion.help.ClasspathHelpDocuments;
import com.aresstack.enterpriseai.proasteion.help.HelpCatalog;
import com.aresstack.enterpriseai.astu.acropolis.speech.ModelLinguisticAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.HolkasAcquisitionPort;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.KnowledgeSourceConnectorRegistry;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Der Kompositionspunkt (corenth ADR-0001, {@code proasteion:application}): verbindet Ports mit Use Cases, dem
 * Agent-Modus (AP21 mit den Wissenswerkzeugen aus AP20) und der Hintergrundarbeit und besitzt die
 * {@link ShutdownSequence}. Kennt keine Oberfläche; eine Schale (exedra) baut ihre Bindings und Ansichten auf den
 * Use Cases, die er herausgibt, und meldet ihre eigene Abbruchaktion über {@link #onStopIndexing}. Der UI-Executor
 * ist produktiv {@code SwingUtilities::invokeLater}, der Arbeits-Executor ein eigener Pool (Suche und Indexierung
 * blockieren, nie auf dem UI-Thread).
 *
 * <p>Shutdown-Reihenfolge (Auftrag AP23): laufende Turns abbrechen, Agent-Modus beenden (Endpoint abgemeldet,
 * Token ungültig), Hintergrund-Indexierung stoppen, MCP-Server und Index schließen (über
 * {@link ApplicationPorts#close()} in dieser Reihenfolge), zuletzt die Executor. Jeder Schritt läuft genau
 * einmal, auch wenn Fenster-Schließen und JVM-Shutdown-Hook beide zugreifen.
 */
public final class CompositionRoot {

    /** Herkunft der Werkzeuge im {@link ToolCatalog} (Anzeige im Einstellungen-Reiter „Tools“). */
    static final String ORIGIN_KNOWLEDGE = "Wissenswerkzeuge (KnowledgeMcpTools)";
    static final String ORIGIN_TERM_LOOKUP = "Begriffe im Metadatenkatalog (KnowledgeTermLookupTool)";
    static final String ORIGIN_RESEARCH_DELEGATION = "Recherche-Agent für den Chat (ResearchDelegationTool)";
    static final String ORIGIN_RESEARCH = "Recherche-Agent, je Phase (ResearchControlTools)";
    static final String ORIGIN_ATTACHMENTS = "Dateianhänge (AttachmentTools)";
    static final String ORIGIN_HELP = "Produkthilfe (AppHelpMcpTools)";

    /** Analysierte Dokumentnavigation je Fassung, neben der Konfiguration. */
    static final String NAVIGATION_DIRECTORY = "navigation";
    /** Zwischenspeicher der Bildbeschreibungen, derselbe Ordner wie im Lesebereich. */
    static final String IMAGE_DESCRIPTIONS_DIRECTORY = "image-descriptions";

    private static final Logger LOG = Logger.getLogger(CompositionRoot.class.getName());
    /** Wartezeit beim Beenden auf eine laufende Indexierung (danach wird der Index geschlossen). */
    static final int INDEXING_SHUTDOWN_WAIT_SECONDS = 10;
    /** Kanal der Vorlade-Serie des Chats. */
    static final String CHAT_PREFETCH_CHANNEL = "chat";
    /** Ordner der Research-Projekte des eingebauten Recherche-Agenten, neben der Konfigurationsdatei. */
    static final String RESEARCH_DIRECTORY = "research";

    private final SettingsService settings;
    private final ApplicationPorts ports;
    private final ChatService chatService;
    private final RetrieveKnowledgeUseCase retrieval;
    private final PromptContextAssembler contextAssembler;
    private final RagChatUseCase ragChat;
    private final TurnDiagnosticsLog turnDiagnostics;
    private final IndexKnowledgeUseCase indexing;
    private final MediatedKnowledgeDocuments resourceDocuments;
    private final LoadKnowledgeDocumentUseCase documents;
    private final RefreshKnowledgeSourceUseCase refresh;
    private final KnowledgeMcpTools knowledgeTools;
    private volatile OneTimeCodePrompt oneTimeCodePrompt;
    private final Object attachmentImagesLock = new Object();
    private com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase attachmentImages;
    private final ToolCatalog toolCatalog;
    private final AgentService agentService;
    private final ResearchAgentHost researchHost;
    private final AgentModelChoice agentModelChoice;
    private final ExecutorService agentStarts;
    private final ExecutorService workExecutor;
    private final Executor uiExecutor;
    private final LongSupplier clock;
    private final ZoneId zone;
    /** Abbruch einer laufenden Vollindexierung durch die Schale (Statuszeile), beim Beenden in Schritt stop-indexing. */
    private final List<Runnable> indexingStops = new CopyOnWriteArrayList<Runnable>();
    private final AttachmentTextExtractor attachmentText;
    private final KnowledgeSourceSelection sourceSelection;
    /** Die angebundenen Quellen von jetzt; ersetzt, nie verändert (siehe {@link #publishSources}). */
    private volatile KnowledgeSourceCatalog liveSources;
    private final SearchDrivenIndexingUseCase searchIndexing;
    private final KnowledgePrefetchService prefetch;
    private final BuildDocumentNavigationUseCase documentNavigation;
    private final DocumentAcronymsUseCase documentAcronyms;
    private final DocumentStatementsUseCase documentStatements;
    /** One analysis at a time: model requests stay sequential and a page opened twice is analysed once. */
    private final ExecutorService statementAnalysis;
    private final ShutdownSequence shutdown;
    private java.util.concurrent.ExecutorService imagePool;

    private CompositionRoot(final SettingsService settings, final ApplicationPorts ports, Executor uiExecutor,
                            LongSupplier clock, ZoneId zone) {
        this.settings = settings;
        // Aktiv oder abgeschaltet: tools.disabled der aktuellen Einstellungen, bei jedem Aufruf frisch.
        this.toolCatalog = new ToolCatalog(name -> settings.current().isToolEnabled(name));
        AppConfig config = settings.current();
        this.ports = ports;
        this.uiExecutor = uiExecutor;
        this.clock = clock;
        this.zone = zone;
        KnowledgeConfig knowledge = config.knowledge();

        this.chatService = new ChatService(ports.chat(), config.chat().defaultOptions());
        // Die Embedding-Welt als Funktion: ein anderes Embedding-Modell gilt ohne Neustart (eigener Namensraum).
        // Nachbarschaft (ähnliche Absätze, Nachbarabsätze, Abschnitt, Elternseite) aus dem Index abgeleitet;
        // Schwelle, Anzahl und retrieval.maxResults gelten ohne Neustart.
        this.retrieval = new RetrieveKnowledgeUseCase(ports.index(), ports.embeddings(), ports.embeddingSpaceSource(),
                knowledge.retrieval()).withRelations(new IndexKnowledgeRelations(ports.index()),
                settings.get(c -> c.knowledge().retrieval())).withObserver(new LoggingRetrievalObserver());
        // Quellen- und Token-Budget des Antwortkontexts gelten ohne Neustart ab dem nächsten Turn.
        this.contextAssembler = PromptContextAssembler.live(settings.get(c -> c.knowledge().context()));
        // Indexiert wird wie askai arch in semantische Passagen: Struktur zuerst, Satzgrenzen aus dem OpenNLP-Modell
        // der eingestellten Sprache, Themenwechsel über Satzfenster, eigenes Embedding je Passage. Ändert sich Text,
        // Zerlegung oder Embedding-Modell, wird neu verarbeitet, sonst nicht.
        final NlpModels nlpModels = NlpModels.inApplicationDirectory();
        final String passageLanguage = knowledge.passageLanguage();
        // Die Aufbereitung läuft mit mehreren Workern im Hintergrund; wie viele Embedding-Aufrufe dabei gleichzeitig
        // laufen, begrenzt embedding.maxConcurrency (je Aufruf gelesen). Das Anfrage-Embedding des Chats geht am
        // Limit vorbei direkt an den Port und wartet nie hinter der Hintergrundarbeit.
        final EmbeddingPort enrichmentEmbeddings = new ConcurrencyLimitedEmbeddingPort(ports.embeddings(),
                () -> settings.current().knowledge().prefetch().embeddingConcurrency());
        this.indexing = new IndexKnowledgeUseCase(ports.index(), enrichmentEmbeddings, ports.embeddingSpaceSource(),
                new KnowledgeChunker(knowledge.chunking()), knowledge.embeddingBatchSize())
                .withPassages(new SemanticPassageSplitter(new com.aresstack.enterpriseai.astu.acropolis.passage.PassageSegmentation(
                        enrichmentEmbeddings, new IndexSentenceSegmenter(
                                nlpModels,
                                NlpModels.textAnalysis(),
                                knowledge.passageLanguage()),
                        () -> settings.current().knowledge().embeddingBatchSize())));
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
        // Suche und Seitenabruf wiederholen vorübergehende Quellenfehler (knowledge.sources.retry*, ohne Neustart);
        // scheitert eine Quelle trotzdem, gelten wie bisher die Treffer der übrigen.
        RetryingSourceAccess sourceRetry = new RetryingSourceAccess(settings.get(c -> c.knowledge().sourceRetry()));
        // Dateianhänge (PDF, Office, Bilder) liest der Dateiweg der Quelle; der Text kommt aus der Dokumentextraktion
        // bzw. der Bildbeschreibung und geht mit der Herkunft der Quelle wie eine Seite in Roh- und Wissensindex.
        KnowledgeDocumentReader attachmentDocuments = new SourceAttachmentReader(resourceDocuments,
                new DocumentSourceFileTextExtractor(DocumentExtraction.detector(), DocumentExtraction.registry(),
                        this::attachmentImageDescriptions));
        KnowledgeDocumentReader retryingDocuments = sourceRetry.reader(attachmentDocuments);
        // Lesen auf Anforderung (knowledge_read, Klick) fragt nach dem Einmalcode einer Quelle und liest dann neu;
        // das Vorladen im Hintergrund bekommt den Leser ohne Abfrage.
        this.documents = new LoadKnowledgeDocumentUseCase(ports.sources())
                .withReader(new UnlockingDocumentReader(retryingDocuments, () -> oneTimeCodePrompt))
                .withCatalog(() -> liveSources);
        this.refresh = new RefreshKnowledgeSourceUseCase(indexing, ports.sources());
        // Suche füllt den Index (MainframeMate) in zwei Ebenen: Quellen live durchsuchen, die Treffer lädt der
        // Vorlader (Lade-Pool, Priority-Thread) und schreibt sie sofort in den Rohindex (Ebene A, durchsuchbar ohne
        // Passagen und Embeddings); die Aufbereitungs-Worker zerlegen und betten nur neue oder geänderte ein
        // (Revision, dann Inhalts-Hash; Ebene B im Hintergrund). Kein Vollindex beim Start. Alle Pools gelten ohne
        // Neustart.
        this.prefetch = new KnowledgePrefetchService(indexing, ports.rawIndex(), retryingDocuments,
                () -> settings.current().knowledge().prefetch());
        this.searchIndexing = new SearchDrivenIndexingUseCase(prefetch, sourceRetry);
        // Adaptiv (ein Evidenz-Bewerter je Kandidatenrunde, gestufte Live-Suche). Alle Grenzen (Quellen, Mindestzahl,
        // Nachsuchen, Live-Suche, Kandidaten) gelten ohne Neustart ab dem nächsten Turn; FollowUpResearch prüft hier
        // nur den Wissenskontext früherer Nachrichten, die Zahl der Nachsuchen kommt aus retrieval.maxFollowUps.
        StagedLiveSearch liveSearch = new StagedLiveSearch(() -> settings.current().knowledge().liveSearch(),
                (sources, query, hitsPerSource) -> {
                    List<KnowledgeSourcePort> chosen = new ArrayList<KnowledgeSourcePort>();
                    for (KnowledgeSourceRegistration registration : liveSources.registrations()) {
                        if (sources.isEmpty() || sources.contains(registration.sourceId())) {
                            chosen.add(registration.port());
                        }
                    }
                    final SearchDrivenIndexingUseCase.Result result = searchIndexing.search(
                            CHAT_PREFETCH_CHANNEL, chosen, query, hitsPerSource, true);
                    if (!result.failures().isEmpty()) {
                        LOG.info("Live-Suche für den Chat: " + result.failures());
                    }
                    return new StagedLiveSearch.Found() {
                        @Override
                        public int hits() {
                            return result.hits().size();
                        }

                        @Override
                        public void awaitAvailable(int count, java.util.function.IntConsumer listener) {
                            result.series().awaitAvailable(count, SearchDrivenIndexingUseCase.INDEX_WAIT_MILLIS,
                                    listener);
                        }
                    };
                });
        // Der Chat sucht zusätzlich im Rohindex: eine gerade geladene Seite ist mit ihren passendsten Textfenstern
        // Kandidat für die Evidenzbewertung, bevor Passagen und Embeddings fertig sind.
        this.turnDiagnostics = new TurnDiagnosticsLog(() -> settings.current().chat().logsTurnAnalysis());
        this.ragChat = new RagChatUseCase(chatService, retrieval.withRawDocuments(ports.rawIndex()), contextAssembler,
                liveSearch,
                FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS),
                settings.get(c -> c.knowledge().evidence()), RetrievalTriage.off(),
                new ModelLinguisticAnalyzer(NlpModels.textAnalysis(), passageLanguage), turnDiagnostics,
                () -> settings.current().chat().answerPlanning());
        // Cross-encoder (model.rerank) between fusion and the evidence check; without a selected model, or when it
        // fails, the candidates reach the evaluator unchanged. Model and top-K apply without restart.
        this.ragChat.rankCandidatesWith(new com.aresstack.enterpriseai.astu.acropolis.rag.CrossEncoderRanking(
                ports.reranker() == null ? null : ports.reranker()::rerank, () -> {
                    com.aresstack.enterpriseai.astu.modelcatalog.ModelReference selected = config().models()
                            .selections().get(com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.RERANK);
                    return selected == null ? null : selected.modelId();
                }, () -> settings.current().knowledge().evidence().rerankTopK()));
        this.sourceSelection = new KnowledgeSourceSelection();
        for (KnowledgeSourceRegistration registration : ports.sources().registrations()) {
            boolean enabled = true; // eine angebundene Quelle ohne Eintrag in der Konfiguration bleibt angehakt
            for (SourceDefinition source : config.sources()) {
                if (source.address().equals(registration.sourceId().value())) {
                    enabled = source.enabled();
                }
            }
            sourceSelection.register(registration.sourceId(), enabled);
        }
        // Wissenswerkzeuge (knowledge_search, knowledge_tree, knowledge_read, knowledge_attachments); durchsucht
        // werden nur die angehakten Quellen. Die Kinderzahl von knowledge_tree gilt ohne Neustart.
        final KnowledgeSourceSelection selection = sourceSelection;
        this.knowledgeTools = new KnowledgeMcpTools(retrieval, documents, searchIndexing,
                selection::isSearchable, config.agent().toolSettings(),
                () -> settings.current().agent().toolSettings().treeChildLimit());
        // Die zentrale Werkzeug-Registry: Chat, Agenten und der Einstellungen-Reiter „Tools“ lesen nur hier. Der
        // Wissens-Werkzeugsatz (knowledge_search, knowledge_read) steht als ein Satz für den Agenten darin.
        final KnowledgeMcpTools offered = knowledgeTools;
        // Der normale Chat recherchiert über die Research-Grenze: knowledge_research(task) übergibt den Auftrag in
        // natürlicher Sprache dem Recherche-Agenten, der die Suchanfragen formuliert und knowledge_search/
        // knowledge_read selbst nutzt. Das direkte knowledge_search bekommt der Chat nur, solange
        // knowledge_research fehlt oder abgeschaltet ist; Lesen, Seitenbaum und Anhänge bleiben im Chat.
        final boolean delegated = ports.chat() != null;
        final ToolCatalog chatCatalog = toolCatalog;
        // Ohne knowledge_search oder knowledge_read (abgeschaltet) kann der Recherche-Agent nicht recherchieren; dann
        // bietet der Chat knowledge_research nicht an und bekommt, soweit aktiv, die Low-Level-Werkzeuge zurück.
        final java.util.function.BooleanSupplier researchUsable = () -> delegated
                && chatCatalog.isEnabled(ResearchDelegationTool.NAME)
                && chatCatalog.isEnabled(KnowledgeMcpTools.KNOWLEDGE_SEARCH)
                && chatCatalog.isEnabled(KnowledgeMcpTools.KNOWLEDGE_READ);
        if (delegated) {
            final ToolCatalog catalog = toolCatalog;
            final ResearchDelegationTool research = new ResearchDelegationTool(
                    name -> catalog.find(name, ToolChannel.AGENT),
                    new ChatPortMainModelChat(ports.chat(), config.chat().defaultOptions()));
            toolCatalog.register(ORIGIN_RESEARCH_DELEGATION, EnumSet.of(ToolChannel.CHAT),
                    () -> chatCatalog.isEnabled(KnowledgeMcpTools.KNOWLEDGE_SEARCH)
                            && chatCatalog.isEnabled(KnowledgeMcpTools.KNOWLEDGE_READ)
                            ? Collections.singletonList(research.contribution())
                            : Collections.<McpToolContribution>emptyList());
        }
        toolCatalog.register(ORIGIN_KNOWLEDGE, EnumSet.of(ToolChannel.AGENT), offered::contributions);
        // First, cheap stage of the Research Agent: acronyms from the metadata catalog (agent only, not in the chat).
        final KnowledgeTermLookupTool termLookup = new KnowledgeTermLookupTool(ports.facts());
        toolCatalog.register(ORIGIN_TERM_LOOKUP, EnumSet.of(ToolChannel.AGENT),
                () -> Collections.singletonList(termLookup.contribution()));
        toolCatalog.register(ORIGIN_KNOWLEDGE, EnumSet.of(ToolChannel.CHAT), () -> {
            boolean viaResearch = researchUsable.getAsBoolean();
            List<McpToolContribution> tools = new ArrayList<McpToolContribution>();
            for (McpToolContribution tool : offered.contributions()) {
                if (!viaResearch || !KnowledgeMcpTools.KNOWLEDGE_SEARCH.equals(tool.getName())) {
                    tools.add(tool);
                }
            }
            return tools;
        });
        toolCatalog.registerDefinitions(ORIGIN_ATTACHMENTS, EnumSet.of(ToolChannel.CHAT),
                AttachmentTools::definitions);
        registerHelpTools(toolCatalog, config.agent().toolSettings());
        this.workExecutor = Executors.newCachedThreadPool(daemonThreads("enterprise-ai-work"));
        // Dokumentnavigation im Lesebereich: Passagen der Index-Pipeline (askai arch PassageSegmentation) + TopicClusterer,
        // ohne Vorrang in der Index-Warteschlange; ohne OpenNLP-Satzmodell nur Originalüberschriften. Analysierte Fassungen
        // bleiben über den Neustart in <Konfigurationsverzeichnis>/navigation. Themenabschnitte
        // benennt das gewählte Chat-Modell (austauschbar), sonst bleibt die Bezeichnung aus der Analyse.
        this.documentNavigation = new BuildDocumentNavigationUseCase(prefetch,
                () -> nlpModels.hasSentenceModel(passageLanguage),
                new ChatSectionLabeler(ports.chat(), modelKey(
                        com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.CHAT)),
                workExecutor, new FileDocumentNavigationStore(AppPaths.configFile().toAbsolutePath().getParent()
                        .resolve(NAVIGATION_DIRECTORY)));
        // Acronyms in the reader (#107): sentence boundaries as for indexing (OpenNLP sentence model of the language if
        // installed, else the rule-based splitter), findings as facts in the metadata catalog (#106); switch and
        // thresholds apply without restart.
        this.documentAcronyms = new DocumentAcronymsUseCase(new AcronymExtractor(new IndexSentenceSegmenter(nlpModels,
                NlpModels.textAnalysis(), passageLanguage)), () -> settings.current().textMining().acronyms(),
                new FactCatalogTextFindings(ports.facts()), () -> settings.current().textMining().acronymsEnabled());
        // Sentence analysis (#108): the selected chat model proposes statements per paragraph with word ranges, the
        // validator keeps only what is in the sentence, an optional yes/no question checks each claim; the local
        // part-of-speech model (if installed) confirms predicates. Off by default; everything applies without restart.
        final java.util.function.Supplier<String> chatModel = modelKey(
                com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.CHAT);
        this.documentStatements = new DocumentStatementsUseCase(new StatementAnalyzer(
                new IndexSentenceSegmenter(nlpModels, NlpModels.textAnalysis(), passageLanguage),
                new ChatStatementExtractor(ports.chat(), chatModel),
                new PosPredicateCheck(NlpModels.textAnalysis(), passageLanguage,
                        () -> nlpModels.hasModel(NlpModels.TOKENIZE, passageLanguage)
                                && nlpModels.hasModel(NlpModels.POS, passageLanguage)),
                new ChatStatementVerifier(ports.chat(), chatModel,
                        () -> settings.current().textMining().statements().verify())),
                new FactCatalogStatements(ports.facts()), () -> settings.current().textMining().statements());
        this.statementAnalysis = Executors.newSingleThreadExecutor(daemonThreads("enterprise-ai-statements"));
        // Anhänge (Chat und Agent): Text über den Dokument-Port, Markdown/Klartext direkt, sonst Apache Tika.
        this.attachmentText = new DocumentAttachmentTextExtractor(DocumentExtraction.detector(),
                DocumentExtraction.registry());

        this.agentStarts = Executors.newSingleThreadExecutor(daemonThreads("enterprise-ai-agent-start"));
        // Der eingebaute Recherche-Agent aus askai-java8 (arch) samt Weidezaun, Projekte neben der Konfiguration.
        // Ist ein eigenes Agent-Kommando eingeschaltet, startet stattdessen dieses; entschieden wird je
        // Agent-Sitzung aus den aktuellen Einstellungen, ein Neustart ist nicht nötig.
        this.researchHost = new ResearchAgentHost(AppPaths.configFile().toAbsolutePath().getParent()
                .resolve(RESEARCH_DIRECTORY).toFile(), FileResearchScopeDraftStore::new,
                directory -> new ConceptBranchService(new FileConceptStore(directory)));
        this.agentModelChoice = new AgentModelChoice();
        wireSourceProcessing(catalogForImages(ports));
        wireResearchCorpus();
        final ChatPortMainModelChat researchModel = new ChatPortMainModelChat(ports.chat(),
                config.chat().defaultOptions(), agentModelChoice);
        toolCatalog.register(ORIGIN_RESEARCH, EnumSet.of(ToolChannel.RESEARCH_AGENT),
                () -> ResearchControlTools.toolsFor(researchHost, researchModel), ResearchControlTools::isRequired);
        // Der Werkzeugsatz des Agenten folgt der Phase (askai arch ResearchToolPolicy): neu berechnet und per
        // updateTools veröffentlicht, sobald der Host seinen Zustand ändert.
        final PhaseToolSetController researchToolSet = new PhaseToolSetController(
                new java.util.function.Supplier<List<McpToolContribution>>() {
                    @Override
                    public List<McpToolContribution> get() {
                        List<McpToolContribution> tools = new ArrayList<McpToolContribution>(
                                toolCatalog.tools(EnumSet.of(ToolChannel.RESEARCH_AGENT)));
                        tools.addAll(agentTools());
                        return tools;
                    }
                });
        researchHost.addStateListener(new Runnable() {
            @Override
            public void run() {
                researchToolSet.refresh();
            }
        });
        // Ein eigenes Agent-Kommando bekommt die Werkzeuge des Kanals Agent; ebenfalls neu veröffentlicht, sobald
        // sich der Satz ändert.
        final PhaseToolSetController externalToolSet = new PhaseToolSetController(this::agentTools);
        // Werkzeug ein- oder ausgeschaltet (Reiter „Tools“): laufende Agenten bekommen den neuen Satz sofort
        // (tools/list_changed); der Chat fragt den Katalog ohnehin je Anfrage.
        settings.addListener((previous, current) -> {
            if (!previous.disabledTools().equals(current.disabledTools())) {
                researchToolSet.refresh();
                externalToolSet.refresh();
            }
        });
        this.agentService = new AgentService(new AgentLauncher() {
            @Override
            public AcpConnection launch() throws AcpException {
                AgentBackend external = ports.agentFor(settings.current().agent());
                if (external != null) {
                    if (external.registry() == null) {
                        return new AcpAgentLauncher(external.connector(), external.launchSpec()).launch();
                    }
                    return AcpAgentLauncher.withPhaseToolSet(external.connector(), external.launchSpec(),
                            external.registry(), external.endpoint(), externalToolSet).launch();
                }
                // Der eingebaute Recherche-Agent wie in askai-java8 arch: eigener Prozess über ACP, die
                // Research-Steuerung, das Konzeptpapier und das Hauptmodell über seinen MCP-Endpoint.
                AgentBackend research = ports.researchAgent();
                if (research == null) {
                    throw new AcpException(AcpException.Phase.SPAWN, "research agent is not available", null);
                }
                // Recherche-Budget wie in arch: höchstens so viele geöffnete Seiten (knowledge_read) pro Lauf
                // (agent.research.maxPages, 0 = unbegrenzt); Vorladen zählt nicht. Der Agentenprozess liest
                // es aus seiner Umgebung.
                java.util.Map<String, String> env = new java.util.LinkedHashMap<String, String>(
                        research.launchSpec().getEnv());
                env.put(com.aresstack.enterpriseai.research.runtime.loop.EnvironmentRunBudget.ENV_MAX_PAGES,
                        Integer.toString(settings.current().agent().researchMaxPages()));
                // Wissenssuche des Agenten: dieselbe Wiederholungsregel wie die Quellenzugriffe des Hosts, für den
                // Fall, dass der Aufruf über MCP selbst kurz scheitert.
                RetryPolicy searchRetry = settings.current().knowledge().sourceRetry();
                env.put(com.aresstack.enterpriseai.research.runtime.search.provider.RetryingSearchProvider.ENV_RETRY_COUNT,
                        Integer.toString(searchRetry.retries()));
                env.put(com.aresstack.enterpriseai.research.runtime.search.provider.RetryingSearchProvider.ENV_RETRY_DELAY_MILLIS,
                        Long.toString(searchRetry.initialDelayMillis()));
                com.aresstack.enterpriseai.acp.api.AgentLaunchSpec spec =
                        new com.aresstack.enterpriseai.acp.api.AgentLaunchSpec(research.launchSpec().getCommand(),
                                research.launchSpec().getArgs(), env);
                return AcpAgentLauncher.withPhaseToolSet(research.connector(), spec,
                        research.registry(), research.endpoint(), researchToolSet).launch();
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

    /** Die Werkzeuge am MCP-Endpoint jedes Agenten: Kanal {@link ToolChannel#AGENT} im {@link #toolCatalog()}. */
    public List<McpToolContribution> agentTools() {
        return toolCatalog.tools(EnumSet.of(ToolChannel.AGENT));
    }

    /** Die zentrale Werkzeug-Registry; neue Werkzeuge werden hier registriert, nicht in Chat oder Oberfläche. */
    public ToolCatalog toolCatalog() {
        return toolCatalog;
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

    /** Die RAG-Variante des Chats; die Schale hängt die {@code RagChatBinding} (AP22) daran. */
    /** The diagnostic log of the tool choice; reports tool calls when {@code chat.diagnostics.turnAnalysis} is on. */
    public ToolActivityListener turnDiagnostics() {
        return turnDiagnostics;
    }

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

    /** Textextraktion für Anhänge im Chat und im Agent-Modus (Dokument-Port deigma). */
    public AttachmentTextExtractor attachmentText() {
        return attachmentText;
    }

    /**
     * Die Schale meldet, wie sie eine laufende Vollindexierung anhält (Statuszeile); beim Beenden läuft das im
     * Schritt {@code stop-indexing}, vor dem Schließen des Index.
     */
    public void onStopIndexing(Runnable stop) {
        if (stop == null) {
            throw new IllegalArgumentException("stop must not be null");
        }
        indexingStops.add(stop);
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

    /**
     * Offer the bundled product help ({@code app_help_search}, {@code app_help_read}) to chat and agent. Use the
     * response limits of the knowledge tools. Skip the tools with a warning if the help cannot be loaded; the
     * application keeps working without them.
     */
    private static void registerHelpTools(ToolCatalog catalog, KnowledgeToolSettings limits) {
        final AppHelpMcpTools help;
        try {
            help = AppHelpMcpTools.over(HelpCatalog.load(ClasspathHelpDocuments.bundled()),
                    new AppHelpToolLimits(limits.maxResponseChars(), limits.snippetChars(), limits.defaultMaxResults()),
                    CompositionRoot.class.getPackage() == null ? null
                            : CompositionRoot.class.getPackage().getImplementationVersion());
        } catch (java.io.IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Produkthilfe nicht geladen, app_help_* fehlen: " + e.getMessage(), e);
            return;
        }
        catalog.register(ORIGIN_HELP, EnumSet.of(ToolChannel.CHAT, ToolChannel.AGENT), help::contributions);
    }

    public KnowledgeMcpTools knowledgeTools() {
        return knowledgeTools;
    }

    /**
     * Die ausführbaren Werkzeuge des Kanals {@link ToolChannel#CHAT} aus dem {@link #toolCatalog()}, heute der
     * Wissens-Werkzeugsatz ({@code knowledge_search}, {@code knowledge_read}): dieselben Handler, Beschreibungen und
     * Schemas wie über MCP für den Agenten; {@code knowledge_search} durchsucht nur die
     * angehakten Quellen von jetzt. Der Chat-Pfad sieht nur {@link com.aresstack.enterpriseai.astu.acropolis.tool.AiTool}.
     * Bei jedem Aufruf neu und nur aktive: ein im Reiter „Tools“ abgeschaltetes Werkzeug fehlt ab der nächsten Anfrage.
     */
    public List<com.aresstack.enterpriseai.astu.acropolis.tool.AiTool> knowledgeChatTools() {
        return McpChatTool.all(toolCatalog.tools(EnumSet.of(ToolChannel.CHAT)));
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
    /** Vorlader der Suchtreffer (Cache, Priority-Lesen, Indexierung als Nebenwirkung). */
    public KnowledgePrefetchService prefetch() {
        return prefetch;
    }

    /** Baut die Dokumentnavigation des Lesebereichs (Wolkenbanner). */
    public BuildDocumentNavigationUseCase documentNavigation() {
        return documentNavigation;
    }

    /** Acronyms, identifiers and stated long forms of an opened page (#107); blocks, call off the UI thread. */
    public DocumentAcronymsUseCase documentAcronyms() {
        return documentAcronyms;
    }

    /** Statements of an opened page (#108); blocks on model calls, run on {@link #statementAnalysis()}. */
    public DocumentStatementsUseCase documentStatements() {
        return documentStatements;
    }

    /** The single background thread of the sentence analysis. */
    public Executor statementAnalysis() {
        return statementAnalysis;
    }

    public SearchDrivenIndexingUseCase searchIndexing() {
        return searchIndexing;
    }

    /**
     * Bildbeschreibungen auf Abruf für den Lesebereich: genau ein Bild, Modell der Kategorie VISION (bei jedem Aufruf
     * aus den Einstellungen, KIPITZ oder lokal über den Chat-Port), Ergebnis je Bild und Version als Datei in
     * {@code directory}.
     */
    public com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase readerImageDescriptions(
            java.nio.file.Path directory) {
        return new com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase(
                () -> java.util.Collections.<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>emptyList(),
                ports.chat(), modelKey(com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.DOCUMENT_OCR),
                modelKey(com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.VISION),
                new FileImageAnalysisCache(directory));
    }

    /**
     * Die Oberfläche für Einmalcodes (etwa Confluence-Dateien hinter einem Zwei-Faktor-Plugin): ein Lesezugriff auf
     * Anforderung wartet, bis der Benutzer den Code eingegeben oder abgebrochen hat. {@code null}: keine Abfrage.
     */
    public void askOneTimeCodesWith(OneTimeCodePrompt prompt) {
        this.oneTimeCodePrompt = prompt;
    }

    /** Bildbeschreibung für Bildanhänge, beim ersten Bild angelegt; derselbe Zwischenspeicher wie im Lesebereich. */
    private com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase attachmentImageDescriptions() {
        synchronized (attachmentImagesLock) {
            if (attachmentImages == null) {
                attachmentImages = readerImageDescriptions(AppPaths.configFile().toAbsolutePath().getParent()
                        .resolve(IMAGE_DESCRIPTIONS_DIRECTORY));
            }
            return attachmentImages;
        }
    }

    public ShutdownSequence shutdown() {
        return shutdown;
    }

    /** Bilder je Quelle: höchstens so viele werden je übernommener Seite ausgewertet. */
    private static final int MAX_IMAGES_PER_SOURCE = 8;

    private static java.util.function.Supplier<java.util.Collection<
            com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>> catalogForImages(final ApplicationPorts ports) {
        return new java.util.function.Supplier<java.util.Collection<
                com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>>() {
            @Override
            public java.util.Collection<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort> get() {
                return ports.sources().ports();
            }
        };
    }

    /** Bookmark, Revision und Inhalts-Hash einer Ressource aus dem Index; leere Felder, solange sie fehlen. */
    private String[] resourceReference(String id) {
        com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId resource;
        try {
            resource = com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId.of(id);
        } catch (IllegalArgumentException notAResource) {
            return new String[]{"", "", ""};
        }
        String bookmark;
        try {
            bookmark = com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks.of(resource).toString();
        } catch (IllegalArgumentException noBookmark) {
            bookmark = "";
        }
        java.util.Optional<com.aresstack.enterpriseai.astu.acropolis.chalcotheca.IndexedResourceState> state =
                indexing.stateOf(resource);
        if (!state.isPresent()) {
            return new String[]{bookmark, "", ""};
        }
        com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision revision = state.get().revision();
        String version = !revision.version().isEmpty() ? revision.version()
                : revision.modifiedAt().isPresent() ? revision.modifiedAt().get().toString() : "";
        return new String[]{bookmark, version, state.get().hasContentHash() ? state.get().contentHash() : ""};
    }

    /** Die indexierten Passagen einer Ressource mit ihrer ID (für Belege), Abschnitt und Text. */
    private String indexedPassages(String id) {
        java.util.List<com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry> entries;
        try {
            entries = ports.index().entriesOf(indexing.space(),
                    com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId.of(id));
        } catch (IllegalArgumentException notAResource) {
            return "";
        } catch (com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexException unreadable) {
            java.util.logging.Logger.getLogger(CompositionRoot.class.getName()).warning(
                    "[research-passages] " + id + ": " + unreadable.getMessage());
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry entry : entries) {
            java.util.Optional<com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId> passage =
                    indexing.passageId(entry);
            if (passage.isPresent()) {
                text.append("passage: ").append(passage.get().value()).append('\n');
            }
            String heading = entry.chunk().headingLine();
            if (!heading.isEmpty()) {
                text.append(heading).append(": ");
            }
            text.append(entry.chunk().text()).append("\n\n");
        }
        return text.toString().trim();
    }

    /**
     * Quellen des Recherche-Projekts sind Referenzen wie in corenth: der Host speichert je Quelle nur
     * {@link com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId}, Bookmark, Revision und Inhalts-Hash
     * aus dem Wissensindex, nie den Seitentext; die Passagen liest {@code source_passages} aus dem Index.
     * Nach jeder übernommenen Quelle wertet ein Pool aus vier Threads die Bilder aus, OCR (Modell DOCUMENT_OCR)
     * und Beschreibung (Modell VISION) getrennt über KIPITZ, je Bild und Version einmal ({@code source_images}).
     */
    private void wireSourceProcessing(
            java.util.function.Supplier<java.util.Collection<com.aresstack.enterpriseai.source.api.KnowledgeSourcePort>>
                    sources) {
        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4,
                daemonThreads("research-images"));
        imagePool = pool;
        final com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase images =
                new com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase(sources, ports.chat(),
                        modelKey(com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.DOCUMENT_OCR),
                        modelKey(com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory.VISION),
                        researchHost.imageCache());
        researchHost.setResourceReferences(new java.util.function.Function<String, String[]>() {
            @Override
            public String[] apply(String id) {
                return resourceReference(id);
            }
        });
        researchHost.setPassageReader(new java.util.function.Function<String, String>() {
            @Override
            public String apply(String id) {
                return indexedPassages(id);
            }
        });
        researchHost.setSourceAcceptedListener(new java.util.function.Consumer<String>() {
            @Override
            public void accept(final String sourceId) {
                pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            researchHost.saveImageAnalyses(sourceId, images.analyze(
                                    com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId.of(sourceId),
                                    MAX_IMAGES_PER_SOURCE));
                        } catch (RuntimeException failed) {
                            java.util.logging.Logger.getLogger(CompositionRoot.class.getName()).warning(
                                    "[research-images] " + sourceId + ": " + failed.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * Weidezaun-Filter und Projektsuche des Recherche-Agenten über den Embedding-Port und den Wissensindex: der Zaun
     * misst Trefferauszüge gegen die Posten aus Konzept und Ausschlüssen, die Projektsuche durchsucht nur die
     * Passagen der übernommenen Quellen (Nachfragen nach der Recherche, Belege des Dokuments).
     */
    private void wireResearchCorpus() {
        final com.aresstack.enterpriseai.embedding.api.EmbeddingPort embeddings = ports.embeddings();
        if (embeddings != null) {
            researchHost.setScopeEmbedder(new com.aresstack.enterpriseai.proasteion.application.agent
                    .ResearchScopeControl.Embedder() {
                @Override
                public String fingerprint() {
                    try {
                        return embeddings.modelIdentity().fingerprint();
                    } catch (RuntimeException unavailable) {
                        return null;
                    }
                }

                @Override
                public List<float[]> embed(List<String> texts) {
                    List<float[]> vectors = new ArrayList<float[]>();
                    for (com.aresstack.enterpriseai.astu.embedding.EmbeddingVector vector
                            : embeddings.embed(texts).vectors()) {
                        vectors.add(vector.values());
                    }
                    return vectors;
                }
            });
        }
        final com.aresstack.enterpriseai.astu.acropolis.rag.SearchResourceCorpusUseCase corpus =
                new com.aresstack.enterpriseai.astu.acropolis.rag.SearchResourceCorpusUseCase(ports.index(),
                        embeddings, ports.embeddingSpaceSource());
        researchHost.setCorpusSearch(new ResearchAgentHost.CorpusSearch() {
            @Override
            public String search(List<String> resourceIds, String query, int limit) {
                List<com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId> resources =
                        new ArrayList<com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId>();
                for (String id : resourceIds) {
                    try {
                        resources.add(com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId.of(id));
                    } catch (IllegalArgumentException notAResource) {
                        // keine Ressource des Index
                    }
                }
                StringBuilder text = new StringBuilder();
                for (com.aresstack.enterpriseai.astu.acropolis.rag.SearchResourceCorpusUseCase.Hit hit
                        : corpus.search(resources, query, limit)) {
                    com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry entry = hit.entry();
                    java.util.Optional<com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId> passage =
                            indexing.passageId(entry);
                    text.append("passage: ").append(passage.isPresent() ? passage.get().value()
                            : entry.chunkId().value()).append('\n');
                    text.append("resource: ").append(entry.chunk().resourceId().value()).append('\n');
                    text.append("title: ").append(entry.resource().title()).append('\n');
                    String heading = entry.chunk().headingLine();
                    if (!heading.isEmpty()) {
                        text.append(heading).append(": ");
                    }
                    text.append(entry.chunk().text().replace('\n', ' ')).append("\n\n");
                }
                return text.toString().trim();
            }
        });
    }

    /** Die gewählte Modellkennung einer Kategorie ({@code katalog:id}), bei jedem Aufruf aus den Einstellungen. */
    private java.util.function.Supplier<String> modelKey(
            final com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory category) {
        return new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                com.aresstack.enterpriseai.astu.modelcatalog.ModelReference selected =
                        config().models().selections().get(category);
                return selected == null ? null : selected.key();
            }
        };
    }

    private ShutdownSequence buildShutdown() {
        ShutdownSequence sequence = new ShutdownSequence();
        sequence.then("stop-prefetch", new Runnable() {
            @Override
            public void run() {
                prefetch.shutdown();
            }
        });
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
                if (imagePool != null) {
                    imagePool.shutdownNow(); // Absatzbildung und Bilder der Quellen
                }
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
                for (Runnable stop : indexingStops) {
                    try {
                        stop.run();
                    } catch (RuntimeException e) {
                        // UI schon fort: nichts anzuhalten
                    }
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
                statementAnalysis.shutdownNow();
                if (agentStarts != null) {
                    agentStarts.shutdownNow();
                }
            }
        });
        return sequence;
    }
}
