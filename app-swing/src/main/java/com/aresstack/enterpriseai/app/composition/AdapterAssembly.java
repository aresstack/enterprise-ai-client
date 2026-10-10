package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.config.AgentConfig;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.ChatConfig;
import com.aresstack.enterpriseai.app.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.app.config.EmbeddingConfig;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.security.ClientCertificateFactory;
import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.SecretBackedBearerTokenSource;
import com.aresstack.enterpriseai.app.security.SecretBackedMediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.app.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.app.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.application.modelexecution.ChatModelExecutorRegistry;
import com.aresstack.enterpriseai.application.modelexecution.EmbeddingModelExecutorRegistry;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ResponsesPort;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.document.tika.DocumentExtraction;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.model.kipitz.KipitzModelCatalogAdapter;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarConfig;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarEmbeddingAdapter;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarModelCatalogAdapter;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.confluence.ConfluenceSourceProvider;
import com.aresstack.enterpriseai.source.ftp.FtpSourceProvider;
import com.aresstack.enterpriseai.source.ftp.JesSourceProvider;
import com.aresstack.enterpriseai.source.localfiles.LocalFilesSourceProvider;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSourceProvider;
import com.aresstack.enterpriseai.source.ndv.NdvSourceProvider;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Baut die echten Adapter aus der Konfiguration: der einzige Ort, an dem Adapterkonstruktoren aufgerufen werden.
 * Kein Netzwerkzugriff beim Bauen; Verbindungen entstehen erst bei der ersten Anfrage. Secrets werden nie
 * gelesen, nur als {@code SecretRef} an die Brücken in {@code app.security} weitergereicht. Proxy-Route,
 * TLS-Vertrauensregel und User-Agent bekommt jeder Adapter aus denselben {@link NetworkServices}; nichts davon
 * wird prozessweit gesetzt.
 *
 * <p>Nahtstelle "ohne KeePass": Ist KeePassRPC deaktiviert, bekommt jeder Adapter einen
 * {@link UnavailableSecretProvider}; die Anwendung startet, und jede Anfrage, die ein Secret braucht,
 * scheitert mit einer verständlichen Meldung.
 */
public final class AdapterAssembly {

    private static final Logger LOG = Logger.getLogger(AdapterAssembly.class.getName());

    private AdapterAssembly() {
    }

    /**
     * Produktiv: Pairing-Dialog aus dem Callback, Pairing-Schlüssel in der konfigurierten Datei
     * ({@code security.keepass.pairingKeyStore=file}) oder nur im Speicher ({@code memory}: nach jedem Start
     * erneut pairen).
     */
    public static ApplicationPorts create(AppConfig config, NetworkServices network, KeePassPairingCallback pairing) {
        return createWithLocalModels(config, network, pairing, null);
    }

    /** Produktiv mit lokalem Sidecar ({@code local} darf {@code null} sein: dann nur die Enterprise-API). */
    public static ApplicationPorts createWithLocalModels(AppConfig config, NetworkServices network,
                                                         KeePassPairingCallback pairing, LocalModelRuntime local) {
        KeePassConfig keePass = config.keePass();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        return create(config, network, pairing, keyStore, local);
    }

    /** Mit eigener Schlüsselablage (Tests: {@code InMemoryPairingKeyStore}). */
    public static ApplicationPorts create(AppConfig config, NetworkServices network, KeePassPairingCallback pairing,
                                          KeePassPairingKeyStore keyStore) {
        return create(config, network, pairing, keyStore, null);
    }

    private static ApplicationPorts create(AppConfig config, NetworkServices network, KeePassPairingCallback pairing,
                                           KeePassPairingKeyStore keyStore, LocalModelRuntime local) {
        if (config == null || network == null) {
            throw new IllegalArgumentException("config and network must not be null");
        }
        SecretProvider secrets = secrets(config.keePass(), pairing, keyStore);
        ApplicationPorts.Builder ports = ApplicationPorts.builder().secrets(secrets);

        // Ausführung je Katalog der Modellauswahl: Enterprise-API immer, der lokale Sidecar, wenn konfiguriert.
        LocalSidecarConfig sidecar = local == null ? null : config.models().localSidecar();
        OpenAiCompatibleChatAdapter chatAdapter = chat(config.chat(), secrets, network);
        Map<String, ChatCompletionPort> chats = new LinkedHashMap<String, ChatCompletionPort>();
        chats.put(KipitzModelCatalogAdapter.CATALOG_ID, chatAdapter);
        Map<String, ResponsesPort> responses = new LinkedHashMap<String, ResponsesPort>();
        // derselbe Adapter: /responses mit Route, TLS und Token des Chats
        responses.put(KipitzModelCatalogAdapter.CATALOG_ID, chatAdapter);
        if (sidecar != null) {
            chats.put(LocalSidecarModelCatalogAdapter.CATALOG_ID, local.chat(sidecar));
        }
        ChatModelExecutorRegistry chatModels = new ChatModelExecutorRegistry(chats, responses,
                ModelsConfig.catalogIds(), ModelsConfig.defaultCatalogId(),
                config.models().selections().get(ModelCategory.CHAT));
        ports.chat(chatModels.chat());
        ports.responses(chatModels.responses());

        Map<String, EmbeddingPort> embeddingPorts = new LinkedHashMap<String, EmbeddingPort>();
        embeddingPorts.put(KipitzModelCatalogAdapter.CATALOG_ID, embeddings(config.embedding(), secrets, network));
        ModelReference embeddingModel = config.models().selections().get(ModelCategory.EMBEDDING);
        if (sidecar != null && embeddingModel != null
                && LocalSidecarModelCatalogAdapter.CATALOG_ID.equals(embeddingModel.catalogId())) {
            embeddingPorts.put(embeddingModel.catalogId(), local.embeddings(sidecar, embeddingModel.modelId(),
                    config.embedding().dimension()));
        }
        EmbeddingModelExecutorRegistry embeddingModels = new EmbeddingModelExecutorRegistry(embeddingPorts);
        String embeddingCatalog = embeddingModel == null ? KipitzModelCatalogAdapter.CATALOG_ID
                : embeddingModel.catalogId();
        // Ein lokal gewähltes Modell ohne Sidecar scheitert (Start-Hinweis), statt Text an die Enterprise-API zu geben.
        EmbeddingPort embeddings = embeddingModels.supports(embeddingCatalog)
                ? embeddingModels.require(embeddingCatalog)
                : unavailableEmbeddings(LocalSidecarEmbeddingAdapter.identity(embeddingModel.modelId(),
                config.embedding().dimension()));
        ports.embeddings(embeddings, embeddings.modelIdentity());

        // Ein Quellen-Port je Quelltyp; die Typverzweigung gibt es nur noch hier, als Liste der Adapter.
        for (KnowledgeSourceProvider provider : sourceProviders(secrets, network)) {
            ports.sourceProvider(new LoggingSourceProvider(provider));
        }
        ports.sources(startupSources(config.sources(), ports));

        if (config.agent().enabled()) {
            final SolonMcpServerRuntime mcp = new SolonMcpServerRuntime();
            ports.agent(agent(config.agent(), mcp));
            // Reihenfolge beim Beenden: erst der MCP-Server (Endpoints weg, Token ungültig), dann der Index.
            ports.closing("mcp-server", new Closeable() {
                @Override
                public void close() {
                    mcp.shutdown();
                    SolonMcpServerRuntime.stopSharedServer();
                }
            });
        }

        final LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(config.knowledge().indexDirectory());
        ports.index(index);
        ports.closing("knowledge-index", index);
        return ports.build();
    }

    private static EmbeddingPort unavailableEmbeddings(final EmbeddingModelIdentity identity) {
        return new EmbeddingPort() {
            @Override
            public EmbeddingModelIdentity modelIdentity() {
                return identity;
            }

            @Override
            public EmbeddingBatch embed(List<String> texts) {
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Lokales Embedding-Modell gewählt, "
                        + "aber Java 21 und das Sidecar-Jar fehlen (Einstellungen → Lokale Modelle)");
            }
        };
    }

    static SecretProvider secrets(KeePassConfig keePass, KeePassPairingCallback pairing,
                                  KeePassPairingKeyStore keyStore) {
        if (!keePass.enabled()) {
            return new UnavailableSecretProvider(
                    "KeePassRPC ist deaktiviert (security.keepass.enabled=false); Secrets können nicht gelesen werden");
        }
        if (pairing == null || keyStore == null) {
            throw new IllegalArgumentException("pairing callback and key store are required when KeePass is enabled");
        }
        return new KeePassRpcSecretProvider(keePass.rpc(), keyStore, pairing);
    }

    static OpenAiCompatibleChatAdapter chat(ChatConfig chat, SecretProvider secrets, NetworkServices network) {
        OpenAiCompatibleChatConfig adapterConfig = OpenAiCompatibleChatConfig.builder(chat.baseUrl(), chat.model())
                .bearerToken(new SecretBackedTokenSource(secrets, chat.apiKeyRef()))
                .connectTimeoutMillis(chat.connectTimeoutMillis())
                .readTimeoutMillis(chat.readTimeoutMillis())
                .developerRolePolicy(chat.developerRolePolicy())
                .routes(network.routes())
                .sslSocketFactory(network.tls())
                .userAgent(network.userAgent())
                .build();
        return new OpenAiCompatibleChatAdapter(adapterConfig);
    }

    static OpenAiCompatibleEmbeddingAdapter embeddings(EmbeddingConfig embedding, SecretProvider secrets,
                                                       NetworkServices network) {
        OpenAiCompatibleEmbeddingConfiguration adapterConfig = OpenAiCompatibleEmbeddingConfiguration
                .builder(embedding.baseUrl().toString(), embedding.model(), embedding.dimension())
                .inputMode(embedding.inputMode())
                .maxBatchSize(embedding.maxBatchSize())
                .connectTimeoutMillis(embedding.connectTimeoutMillis())
                .readTimeoutMillis(embedding.readTimeoutMillis())
                .routes(network.routes())
                .sslSocketFactory(network.tls())
                .userAgent(network.userAgent())
                .build();
        return new OpenAiCompatibleEmbeddingAdapter(adapterConfig,
                new SecretBackedBearerTokenSource(secrets, embedding.apiKeyRef()));
    }

    /**
     * Die Quelltypen der Anwendung in der Reihenfolge des Dialogs „+ Quelle“. Ein neuer Adapter (SharePoint, Mail,
     * FTP ...) kommt hier als weiterer Provider hinzu; Kern, Use Cases und Oberfläche bleiben unverändert.
     */
    static List<KnowledgeSourceProvider> sourceProviders(final SecretProvider secrets, final NetworkServices network) {
        List<KnowledgeSourceProvider> providers = new ArrayList<KnowledgeSourceProvider>();
        providers.add(new MediaWikiSourceProvider(ref -> new SecretBackedMediaWikiCredentialsProvider(secrets, ref),
                network.routes(), network.tls(), network.userAgent()));
        providers.add(new ConfluenceSourceProvider(secrets, network.routes(), network.tls(), network.userAgent(),
                (alias, keyStoreFile, passwordRef) -> ClientCertificateFactory.deferred(
                        new ClientCertificateConfig(alias, keyStoreFile, passwordRef), secrets, network)));
        // Markdown und Klartext ohne Tika, alles andere (PDF, Office, HTML, Mail) über den Tika-Adapter.
        providers.add(new LocalFilesSourceProvider(DocumentExtraction.detector(), DocumentExtraction.registry()));
        providers.add(new FtpSourceProvider(secrets));
        providers.add(new NdvSourceProvider(secrets));
        providers.add(new JesSourceProvider(secrets));
        return providers;
    }

    /** Die Quellen der Konfiguration, die sich öffnen lassen; die übrigen werden mit einem Hinweis übersprungen. */
    static KnowledgeSourceCatalog startupSources(List<SourceDefinition> definitions, ApplicationPorts.Builder ports) {
        KnowledgeSourceManagement sources = new KnowledgeSourceManagement(ports.sourceProviders(), null, null);
        List<KnowledgeSourceRegistration> registrations = new ArrayList<KnowledgeSourceRegistration>();
        for (SourceDefinition definition : definitions) {
            List<String> problems = sources.validate(definition, definition.id());
            if (!problems.isEmpty()) {
                ports.sourceWarning("Wissensquelle „" + definition.id() + "“ wird übersprungen, bis sie im Reiter "
                        + "„Wissensquellen“ korrigiert ist: " + String.join("; ", problems));
                continue;
            }
            try {
                registrations.add(sources.open(definition));
            } catch (RuntimeException e) {
                ports.sourceWarning("Wissensquelle „" + definition.id() + "“ wird übersprungen: nicht anbindbar ("
                        + e.getClass().getSimpleName() + ")");
            }
        }
        return new KnowledgeSourceCatalog(registrations);
    }

    static AgentBackend agent(AgentConfig agent, SolonMcpServerRuntime mcp) {
        Consumer<String> hostLog = new Consumer<String>() {
            @Override
            public void accept(String line) {
                LOG.log(Level.FINE, "agent: " + line);
            }
        };
        return new AgentBackend(new SolonAcpAgentConnector(agent.requestTimeout(), hostLog),
                new AgentLaunchSpec(agent.command(), agent.args(), null), mcp,
                new McpEndpointDefinition(agent.mcpEndpointId(), agent.mcpDisplayName()));
    }
}
