package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.proasteion.application.config.AgentConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.ChatConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.proasteion.application.config.EmbeddingConfig;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.proasteion.application.net.LiveNetwork;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.application.security.ClientCertificateFactory;
import com.aresstack.enterpriseai.proasteion.application.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.proasteion.application.security.SecretBackedBearerTokenSource;
import com.aresstack.enterpriseai.proasteion.application.security.SecretBackedMediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.proasteion.application.security.SecretBackedTokenSource;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.proasteion.application.config.VaultLocations;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatAdapter;
import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.tika.DocumentExtraction;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.proasteion.katagogion.McpEndpointDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassPairingKeyStore;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.ConfluenceSourceProvider;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.ftp.FtpSourceProvider;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.ftp.JesSourceProvider;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.localfiles.LocalFilesSourceProvider;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.mediawiki.MediaWikiSourceProvider;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.ndv.NdvSourceProvider;

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
        return createWith(config, network, secrets(config, pairing, new RefusingPrompts().credentials()));
    }

    /**
     * Der Security-Port der Anwendung (KeePass bzw. „Manuell“) mit der Schlüsselablage der Konfiguration; einmal je
     * Start, damit ein Pairing für Adresse und Secrets gilt.
     */
    public static SecretProvider secrets(AppConfig config, KeePassPairingCallback pairing,
                                         AskedSecretProvider.CredentialPrompt prompt) {
        KeePassConfig keePass = config.keePass();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        return secrets(keePass, pairing, keyStore, prompt);
    }

    /**
     * Wie {@link #secrets(AppConfig, KeePassPairingCallback, AskedSecretProvider.CredentialPrompt)} mit der Eingabe
     * einer Oberfläche: die Schale kennt den KeePass-Adapter nicht, nur {@link PairingPrompt}.
     */
    public static SecretProvider secrets(AppConfig config, PairingPrompt pairing,
                                         AskedSecretProvider.CredentialPrompt prompt) {
        return secrets(config, callback(pairing), prompt);
    }

    /**
     * Der Security-Port für eine KeePass-Einstellung (Proben des Einstellungen-Dialogs, Proxy-Anmeldung der
     * Stimmen): Schlüsselablage wie konfiguriert ({@code file} oder {@code memory}), Pairing über die Oberfläche.
     */
    public static SecretProvider secrets(KeePassConfig keePass, PairingPrompt pairing,
                                         AskedSecretProvider.CredentialPrompt prompt) {
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        return secrets(keePass, callback(pairing), keyStore, prompt);
    }

    /**
     * Der Tresorzugang der Einstellungen-Proben für eine KeePass-Einstellung (Entwurf des Dialogs): derselbe
     * Security-Port wie {@link #secrets(KeePassConfig, PairingPrompt, AskedSecretProvider.CredentialPrompt)}, für die
     * Schale ohne adyton-Typen; ausgeschaltetes KeePass lehnt jeden Zugriff ab.
     */
    public static KeePassAccess keePassAccess(KeePassConfig keePass, PairingPrompt pairing,
                                              AskedSecretProvider.CredentialPrompt prompt) {
        if (keePass == null || !keePass.enabled()) {
            return KeePassAccess.unavailable();
        }
        return new KeePassAccess(secrets(keePass, pairing, prompt), keePass.address());
    }

    /** Die Eingabe der Oberfläche als Pairing-Callback des KeePassRPC-Adapters. */
    private static KeePassPairingCallback callback(final PairingPrompt pairing) {
        if (pairing == null) {
            throw new IllegalArgumentException("pairing must not be null");
        }
        return new KeePassPairingCallback() {
            @Override
            public char[] requestPairingPassword(String clientDisplayName) {
                return pairing.requestPairingPassword(clientDisplayName);
            }

            @Override
            public String toString() {
                return pairing.toString();
            }
        };
    }

    /**
     * Ergänzt die Basis-URL des KI-Dienstes aus dem KeePass-Eintrag, wenn sie in der Datei leer ist (Zugang
     * „KeePass“). Liest KeePass nur in diesem Fall.
     *
     * @throws com.aresstack.enterpriseai.proasteion.application.config.AppConfigException wenn KeePass die URL nicht liefert
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
        return createWith(config, network, secrets(config.keePass(), pairing, keyStore,
                new RefusingPrompts().credentials()));
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
        return createWith(config, network, secrets, null);
    }

    /**
     * Wie {@link #createWith(AppConfig, LiveNetwork, SecretProvider)}; Quellen mit Bereichen (Confluence-Spaces)
     * lassen bei jedem Lauf die in {@code partitions} abgehakten aus ({@code null}: keine Auswahl).
     */
    public static ApplicationPorts createWith(AppConfig config, LiveNetwork network, SecretProvider secrets,
                                              SourcePartitionSelection partitions) {
        if (config == null || network == null || secrets == null) {
            throw new IllegalArgumentException("config, network and secrets must not be null");
        }
        ApplicationPorts.Builder ports = ApplicationPorts.builder().secrets(secrets);

        // Chat und Embeddings je Katalog der Modellauswahl, neu gebunden bei jeder Änderung.
        LiveModelPorts models = new LiveModelPorts(config, network.current(), secrets);
        ports.chat(models.chat());
        ports.liveEmbeddings(models.embeddings());
        ports.reconfiguring(models);
        ports.closing("local-models", models);

        // Ein Quellen-Port je Quelltyp; die Typverzweigung gibt es nur noch hier, als Liste der Adapter.
        for (KnowledgeSourceProvider provider : sourceProviders(secrets, network)) {
            KnowledgeSourceProvider wrapped = new LoggingSourceProvider(new VaultSourceProvider(provider, secrets));
            ports.sourceProvider(partitions == null ? wrapped
                    : new PartitionSelectingSourceProvider(wrapped, partitions));
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
        ports.researchAgent(researchAgent(mcp));
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

    public static SecretProvider secrets(KeePassConfig keePass, KeePassPairingCallback pairing,
                                         KeePassPairingKeyStore keyStore, AskedSecretProvider.CredentialPrompt prompt) {
        // „Manuell“ (ask:…) fragt die Oberfläche ab (prompt); alles andere geht an KeePass.
        return new AskedSecretProvider(vault(keePass, pairing, keyStore), prompt);
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

    public static OpenAiCompatibleEmbeddingAdapter embeddings(EmbeddingConfig embedding, SecretProvider secrets,
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

    /** Obergrenze der Messung im Einrichtungsassistenten; begrenzt nur die Antwortgröße, nie das Ergebnis. */
    static final int DIMENSION_PROBE_LIMIT = 16384;
    /** Der Text des Embedding-Tests im Einrichtungsassistenten. */
    static final String DIMENSION_PROBE_TEXT = "Einrichtungstest";

    /**
     * Der Embedding-Test des Einrichtungsassistenten: ein echter kleiner Aufruf von {@code POST /embeddings} über
     * denselben Adapter, dieselbe Route und dieselben TLS-Quellen wie die Anwendung; die Dimension ist die Länge des
     * zurückgegebenen Vektors. Die konfigurierte Dimension spielt keine Rolle (sie ist ja gerade unbekannt).
     *
     * @param token API-Key oder {@code null} (dann ohne Authorization)
     */
    public static int embeddingDimension(EmbeddingConfig embedding, final String token, NetworkServices network) {
        OpenAiCompatibleEmbeddingConfiguration adapterConfig = OpenAiCompatibleEmbeddingConfiguration
                .builder(embedding.baseUrl().toString(), embedding.model(), DIMENSION_PROBE_LIMIT)
                .inputMode(embedding.inputMode())
                .maxBatchSize(embedding.maxBatchSize())
                .connectTimeoutMillis(embedding.connectTimeoutMillis())
                .readTimeoutMillis(embedding.readTimeoutMillis())
                .routes(network.routes())
                .sslSocketFactory(network.tls())
                .userAgent(network.userAgent())
                .build();
        return new OpenAiCompatibleEmbeddingAdapter(adapterConfig,
                () -> token == null ? null : token.toCharArray()).measureDimension(DIMENSION_PROBE_TEXT);
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

    private static String label(SourceDefinition definition) {
        return definition.address().isEmpty() ? definition.typeId() : definition.address();
    }

    /** Die Quellen der Konfiguration, die sich öffnen lassen; die übrigen werden mit einem Hinweis übersprungen. */
    static KnowledgeSourceCatalog startupSources(List<SourceDefinition> definitions, ApplicationPorts.Builder ports) {
        KnowledgeSourceManagement sources = new KnowledgeSourceManagement(ports.sourceProviders(), null, null);
        List<KnowledgeSourceRegistration> registrations = new ArrayList<KnowledgeSourceRegistration>();
        for (SourceDefinition definition : definitions) {
            List<String> problems = sources.validate(definition, definition.address());
            if (!problems.isEmpty()) {
                ports.sourceWarning("Wissensquelle „" + label(definition) + "“ wird übersprungen, bis sie im Reiter "
                        + "„Wissensquellen“ korrigiert ist: " + String.join("; ", problems));
                continue;
            }
            try {
                registrations.add(sources.open(definition));
            } catch (RuntimeException e) {
                ports.sourceWarning("Wissensquelle „" + label(definition) + "“ wird übersprungen: nicht anbindbar ("
                        + e.getClass().getSimpleName() + ")");
            }
        }
        return new KnowledgeSourceCatalog(registrations);
    }

    /**
     * Der eingebaute Recherche-Agent (askai-java8 arch: eigener Prozess über ACP): dieselbe Java-Laufzeit
     * ({@code java.home}), derselbe Klassenpfad (das Fat Jar bzw. der Klassenpfad des Starts), Hauptklasse
     * {@link #RESEARCH_AGENT_MAIN}. Keine fremde EXE, kein zweites Artefakt. Ein Turn darf so lange dauern wie
     * Modell und Recherche brauchen; abgebrochen wird über den Stopp-Knopf.
     */
    public static AgentBackend researchAgent(SolonMcpServerRuntime mcp) {
        Consumer<String> hostLog = new Consumer<String>() {
            @Override
            public void accept(String line) {
                LOG.log(Level.INFO, "research-agent: " + line);
            }
        };
        List<String> args = new ArrayList<String>();
        args.add("-Dfile.encoding=UTF-8");
        args.add("-Djava.awt.headless=true");
        args.add("-cp");
        args.add(ownClassPath());
        args.add(RESEARCH_AGENT_MAIN);
        return new AgentBackend(new SolonAcpAgentConnector(RESEARCH_AGENT_TURN_TIMEOUT, hostLog),
                new AgentLaunchSpec(javaExecutable(), args, null), mcp,
                new McpEndpointDefinition(RESEARCH_ENDPOINT_ID, "Recherche-Steuerung"));
    }

    /** Hauptklasse des eingebauten Recherche-Agenten (Modul research-agent-acp, nur zur Laufzeit im Jar). */
    public static final String RESEARCH_AGENT_MAIN = "com.aresstack.enterpriseai.acp.research.ResearchAgentMain";
    static final String RESEARCH_ENDPOINT_ID = "research-control";
    private static final java.time.Duration RESEARCH_AGENT_TURN_TIMEOUT = java.time.Duration.ofMinutes(30);

    private static String javaExecutable() {
        java.io.File bin = new java.io.File(System.getProperty("java.home"), "bin");
        java.io.File exe = new java.io.File(bin, "java.exe");
        return (exe.isFile() ? exe : new java.io.File(bin, "java")).getAbsolutePath();
    }

    /** Das Jar, aus dem der Client läuft; ohne Jar (Start aus Klassenverzeichnissen) der Klassenpfad des Starts. */
    private static String ownClassPath() {
        try {
            java.security.CodeSource source = AdapterAssembly.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                java.io.File location = new java.io.File(source.getLocation().toURI());
                if (location.isFile() && location.getName().endsWith(".jar")) {
                    return location.getAbsolutePath();
                }
            }
        } catch (Exception unknown) {
            // Klassenpfad des Starts
        }
        return System.getProperty("java.class.path");
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
