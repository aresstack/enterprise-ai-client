package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.config.AgentConfig;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.ChatConfig;
import com.aresstack.enterpriseai.app.config.ConfluenceSourceConfig;
import com.aresstack.enterpriseai.app.config.EmbeddingConfig;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.config.LocalFilesSourceConfig;
import com.aresstack.enterpriseai.app.config.MediaWikiSourceConfig;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.security.ClientCertificateFactory;
import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.SecretBackedBearerTokenSource;
import com.aresstack.enterpriseai.app.security.SecretBackedMediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.app.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.app.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.document.tika.DocumentExtraction;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.confluence.ConfluenceKnowledgeSource;
import com.aresstack.enterpriseai.source.confluence.UrlConnectionConfluenceTransport;
import com.aresstack.enterpriseai.source.localfiles.LocalFilesKnowledgeSource;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiKnowledgeSource;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
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
        KeePassConfig keePass = config.keePass();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        return create(config, network, pairing, keyStore);
    }

    /** Mit eigener Schlüsselablage (Tests: {@code InMemoryPairingKeyStore}). */
    public static ApplicationPorts create(AppConfig config, NetworkServices network, KeePassPairingCallback pairing,
                                          KeePassPairingKeyStore keyStore) {
        if (config == null || network == null) {
            throw new IllegalArgumentException("config and network must not be null");
        }
        SecretProvider secrets = secrets(config.keePass(), pairing, keyStore);
        ApplicationPorts.Builder ports = ApplicationPorts.builder().secrets(secrets);

        OpenAiCompatibleChatAdapter chatAdapter = chat(config.chat(), secrets, network);
        ports.chat(chatAdapter);
        ports.responses(chatAdapter); // derselbe Adapter: /responses mit Route, TLS und Token des Chats
        OpenAiCompatibleEmbeddingAdapter embeddings = embeddings(config.embedding(), secrets, network);
        ports.embeddings(embeddings, embeddings.modelIdentity());

        List<KnowledgeSourceRegistration> registrations = new ArrayList<KnowledgeSourceRegistration>();
        for (SourceConfig source : config.sources()) {
            // Mit Protokollhülle: Fehler der Quelle landen samt Ursachenkette im Protokoll, der Bericht an die UI
            // enthält nur den Text.
            registrations.add(new KnowledgeSourceRegistration(
                    new LoggingKnowledgeSource(source(source, secrets, network)), source.scope()));
        }
        ports.sources(new KnowledgeSourceCatalog(registrations));
        final SecretProvider sourceSecrets = secrets;
        final NetworkServices sourceNetwork = network;
        ports.sourceFactory(source -> new LoggingKnowledgeSource(source(source, sourceSecrets, sourceNetwork)));

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

    static com.aresstack.enterpriseai.source.api.KnowledgeSourcePort source(SourceConfig source,
                                                                              SecretProvider secrets,
                                                                              NetworkServices network) {
        if (source instanceof MediaWikiSourceConfig) {
            MediaWikiSourceConfig wiki = (MediaWikiSourceConfig) source;
            MediaWikiCredentialsProvider credentials = wiki.credentialRef() == null
                    ? MediaWikiCredentialsProvider.anonymous()
                    : new SecretBackedMediaWikiCredentialsProvider(secrets, wiki.credentialRef());
            return new MediaWikiKnowledgeSource(wiki.sourceId(), wiki.site(), credentials, network.routes(),
                    network.tls());
        }
        if (source instanceof ConfluenceSourceConfig) {
            ConfluenceSourceConfig confluence = (ConfluenceSourceConfig) source;
            UrlConnectionConfluenceTransport.Builder transport = UrlConnectionConfluenceTransport.builder()
                    .routes(network.routes())
                    .userAgent(network.userAgent())
                    .connectTimeoutMillis(confluence.connectTimeoutMillis())
                    .readTimeoutMillis(confluence.readTimeoutMillis());
            if (confluence.clientCertificate() != null) {
                // Erst beim ersten Verbindungsaufbau geladen: Das KeyStore-Passwort wird dann je Versuch über den
                // Security-Port geholt, die Vertrauensregel der Anwendung kommt in denselben TLS-Kontext. Ohne
                // erreichbaren Tresor startet die Anwendung trotzdem; die Quelle meldet je Anfrage UNAVAILABLE, bis
                // das Zertifikat ladbar ist.
                transport.sslSocketFactory(ClientCertificateFactory.deferred(confluence.clientCertificate(), secrets,
                        network));
            } else {
                transport.sslSocketFactory(network.tls());
            }
            return new ConfluenceKnowledgeSource(confluence.sourceId(), confluence.confluence(), transport.build(),
                    secrets);
        }
        if (source instanceof LocalFilesSourceConfig) {
            LocalFilesSourceConfig files = (LocalFilesSourceConfig) source;
            // Markdown und Klartext ohne Tika, alles andere (PDF, Office, HTML, Mail) über den Tika-Adapter.
            return new LocalFilesKnowledgeSource(files.sourceId(), files.directory(), DocumentExtraction.detector(),
                    DocumentExtraction.registry(), files.maxFileBytes());
        }
        throw new IllegalArgumentException("unsupported source type: " + source.type());
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
