package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.config.AgentConfig;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.ChatConfig;
import com.aresstack.enterpriseai.app.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.app.config.EmbeddingConfig;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.net.LiveNetwork;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.security.AskedSecretProvider;
import com.aresstack.enterpriseai.app.security.ClientCertificateFactory;
import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.SecretBackedBearerTokenSource;
import com.aresstack.enterpriseai.app.security.SecretBackedMediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.app.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.app.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.app.config.VaultLocations;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.document.tika.DocumentExtraction;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.domain.security.SecretRef;
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
import java.util.List;
import java.util.Properties;
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
        return createWith(config, network, secrets(config, pairing));
    }

    /**
     * Der Security-Port der Anwendung (KeePass bzw. „Manuell“) mit der Schlüsselablage der Konfiguration; einmal je
     * Start, damit ein Pairing für Adresse und Secrets gilt.
     */
    public static SecretProvider secrets(AppConfig config, KeePassPairingCallback pairing) {
        KeePassConfig keePass = config.keePass();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        return secrets(keePass, pairing, keyStore);
    }

    /**
     * Ergänzt die Basis-URL des KI-Dienstes aus dem KeePass-Eintrag, wenn sie in der Datei leer ist (Zugang
     * „KeePass“). Liest KeePass nur in diesem Fall.
     *
     * @throws com.aresstack.enterpriseai.app.config.AppConfigException wenn KeePass die URL nicht liefert
     */
    public static AppConfig withVaultLocations(AppConfig config, Properties properties, final SecretProvider secrets) {
        if (config.chat() == null || config.chat().baseUrl() != null) {
            return config;
        }
        return AppConfigLoader.fromProperties(VaultLocations.complete(properties, new VaultLocations.Lookup() {
            @Override
            public String locationOf(SecretRef ref) throws Exception {
                return secrets.locationOf(ref);
            }
        }));
    }

    /** Mit eigener Schlüsselablage (Tests: {@code InMemoryPairingKeyStore}). */
    public static ApplicationPorts create(AppConfig config, NetworkServices network, KeePassPairingCallback pairing,
                                          KeePassPairingKeyStore keyStore) {
        if (config == null || network == null) {
            throw new IllegalArgumentException("config and network must not be null");
        }
        return createWith(config, network, secrets(config.keePass(), pairing, keyStore));
    }

    /** Mit fertigem Security-Port (Start: derselbe, der vorher die Basis-URL aus KeePass gelesen hat). */
    public static ApplicationPorts createWith(AppConfig config, NetworkServices network, SecretProvider secrets) {
        if (network == null) {
            throw new IllegalArgumentException("config, network and secrets must not be null");
        }
        return createWith(config, new LiveNetwork(network), secrets);
    }

    /**
     * Wie {@link #createWith(AppConfig, NetworkServices, SecretProvider)} mit der Netzschicht, die Einstellungen
     * ohne Neustart übernimmt: Quellen-Adapter verbinden sich je Anfrage über die aktuelle Route und TLS-Regel;
     * Modelle und Index übernehmen gespeicherte Einstellungen über {@link ApplicationPorts#apply}.
     */
    public static ApplicationPorts createWith(AppConfig config, LiveNetwork network, SecretProvider secrets) {
        if (config == null || network == null || secrets == null) {
            throw new IllegalArgumentException("config, network and secrets must not be null");
        }
        ApplicationPorts.Builder ports = ApplicationPorts.builder().secrets(secrets);

        // Chat, Werkzeug-Chat und Embeddings je Katalog der Modellauswahl, neu gebunden bei jeder Änderung.
        LiveModelPorts models = new LiveModelPorts(config, network.current(), secrets);
        ports.chat(models.chat());
        ports.responses(models.responses());
        ports.liveEmbeddings(models.embeddings());
        ports.reconfiguring(models);
        ports.closing("local-models", models);

        // Ein Quellen-Port je Quelltyp; die Typverzweigung gibt es nur noch hier, als Liste der Adapter.
        for (KnowledgeSourceProvider provider : sourceProviders(secrets, network)) {
            ports.sourceProvider(new LoggingSourceProvider(new VaultSourceProvider(provider, secrets)));
        }
        ports.sources(startupSources(config.sources(), ports));

        // Agent-Kommando, Argumente und Zeitlimit kommen je Agent-Sitzung aus den aktuellen Einstellungen; der
        // MCP-Server ist derselbe (startet erst mit dem ersten Endpoint).
        final SolonMcpServerRuntime mcp = new SolonMcpServerRuntime();
        ports.agents(new java.util.function.Function<AgentConfig, AgentBackend>() {
            @Override
            public AgentBackend apply(AgentConfig settings) {
                return agent(settings, mcp);
            }
        });
        if (config.agent().enabled()) {
            ports.agent(agent(config.agent(), mcp));
        }
        // Reihenfolge beim Beenden: erst der MCP-Server (Endpoints weg, Token ungültig), dann der Index.
        ports.closing("mcp-server", new Closeable() {
            @Override
            public void close() {
                mcp.shutdown();
                SolonMcpServerRuntime.stopSharedServer();
            }
        });

        final LiveKnowledgeIndex index = new LiveKnowledgeIndex(config.knowledge().indexDirectory());
        ports.index(index);
        ports.reconfiguring(index);
        ports.closing("knowledge-index", index);
        return ports.build();
    }

    static SecretProvider secrets(KeePassConfig keePass, KeePassPairingCallback pairing,
                                  KeePassPairingKeyStore keyStore) {
        // „Manuell“ (ask:…) fragt die Anwendung selbst ab; alles andere geht an KeePass.
        return new AskedSecretProvider(vault(keePass, pairing, keyStore), AskedSecretProvider.dialog());
    }

    private static SecretProvider vault(KeePassConfig keePass, KeePassPairingCallback pairing,
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
    static List<KnowledgeSourceProvider> sourceProviders(final SecretProvider secrets, final LiveNetwork network) {
        List<KnowledgeSourceProvider> providers = new ArrayList<KnowledgeSourceProvider>();
        // Route und TLS als Funktionen des aktuellen Stands: Proxy-Änderungen gelten ab der nächsten Verbindung.
        providers.add(new MediaWikiSourceProvider(ref -> new SecretBackedMediaWikiCredentialsProvider(secrets, ref),
                network.routes(), network.tls(), network.current().userAgent()));
        providers.add(new ConfluenceSourceProvider(secrets, network.routes(), network.tls(),
                network.current().userAgent(),
                (alias, keyStoreFile, passwordRef) -> ClientCertificateFactory.deferred(
                        new ClientCertificateConfig(alias, keyStoreFile, passwordRef), secrets, network.current())));
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
