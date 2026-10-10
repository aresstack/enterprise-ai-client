package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.application.mcp.KnowledgeToolSettings;
import com.aresstack.enterpriseai.application.rag.ContextSettings;
import com.aresstack.enterpriseai.application.rag.RetrievalSettings;
import com.aresstack.enterpriseai.chat.openai.DeveloperRolePolicy;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelSelections;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.embedding.openai.EmbeddingInputMode;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.model.sidecar.LocalSidecarConfig;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcConfig;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.confluence.ConfluenceConfig;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;
import com.aresstack.winproxy.ProxyMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liest {@link AppConfig} aus einer Properties-Datei (UTF-8). Alle Schlüssel sind in
 * {@code enterprise-ai-client.example.properties} (Ressource dieses Pakets) dokumentiert.
 *
 * <p>Regeln: Pflicht sind nur Chat-Basis-URL, -Modell und -API-Key-Referenz ({@code chat.apiKeyRef}, Titel des
 * KeePass-Eintrags) sowie Embedding-Modell und -Dimension; alles andere hat Defaults (AP3/AP6/AP10/AP14). Probleme
 * werden gesammelt und als eine {@link AppConfigException} gemeldet, die nur Schlüssel und Erwartung nennt, nie
 * den abgelehnten Wert (auch nicht aus verschachtelten Ausnahmen der Adapter-Konfigurationen). Unbekannte Schlüssel
 * sind kein Fehler, erscheinen aber als Hinweis in {@link AppConfig#warnings()}. Eine fehlerhafte Wissensquelle
 * hält den Start nicht auf: sie wird mit einem Hinweis übersprungen und lässt sich im Drawer-Reiter
 * „Wissensquellen“ korrigieren ({@link #sourceSection} prüft eine einzelne Quelle streng).
 */
public final class AppConfigLoader {

    public static final String EXAMPLE_RESOURCE = "enterprise-ai-client.example.properties";

    private static final Pattern SOURCE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final String SOURCE_ID_RULE = "Quell-ID muss dem Muster [A-Za-z0-9][A-Za-z0-9._-]* entsprechen";
    private static final int MAX_TIMEOUT = 3600000;

    private AppConfigLoader() {
    }

    /** Lädt die Datei; fehlt sie, ist das ein Konfigurationsfehler, der den Pfad nennt. */
    public static AppConfig load(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (!Files.isRegularFile(file)) {
            throw new AppConfigException("Konfigurationsdatei fehlt: " + file.toAbsolutePath());
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new AppConfigException("Konfigurationsdatei nicht lesbar: " + file.toAbsolutePath() + " ("
                    + e.getClass().getSimpleName() + ")");
        }
        return fromProperties(properties);
    }

    /** Baut den Snapshot aus bereits gelesenen Properties (Tests, eingebettete Konfiguration). */
    public static AppConfig fromProperties(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        ConfigReader reader = new ConfigReader(properties);
        List<String> warnings = new ArrayList<String>();

        String windowTitle = reader.text("ui.windowTitle", "Enterprise AI Client");
        ChatConfig chat = chat(reader);
        EmbeddingConfig embedding = embedding(reader, chat);
        KnowledgeConfig knowledge = knowledge(reader);
        List<SourceConfig> sources = sources(reader, warnings);
        KeePassConfig keePass = keePass(reader);
        NetworkConfig network = network(reader, chat == null ? null : chat.baseUrl(), warnings);
        AgentConfig agent = agent(reader);
        ModelsConfig models = models(reader, warnings);

        for (String unread : reader.unreadKeys()) {
            warnings.add("Unbekannter Konfigurationsschlüssel wird ignoriert: " + unread);
        }
        if (!keePass.enabled()) {
            List<String> refs = new ArrayList<String>();
            if (chat != null && chat.apiKeyRef() != null) {
                refs.add("chat.apiKeyRef");
            }
            if (embedding != null && embedding.apiKeyRef() != null && !reader.text("embedding.apiKeyRef", "").isEmpty()) {
                refs.add("embedding.apiKeyRef");
            }
            for (SourceConfig source : sources) {
                if (source.credentialRef() != null) {
                    refs.add("source." + source.sourceId().value() + ".credentialRef");
                }
            }
            if (!refs.isEmpty()) {
                warnings.add("KeePassRPC ist deaktiviert (security.keepass.enabled=false), aber Secret-Referenzen "
                        + "sind konfiguriert: " + refs + ". Diese Zugriffe scheitern, bis KeePass aktiviert ist.");
            }
        }
        if (reader.hasProblems()) {
            throw new AppConfigException(reader.problems());
        }
        return new AppConfig(windowTitle, chat, embedding, knowledge, sources, keePass, network, agent, models,
                warnings);
    }

    /**
     * Nur der Abschnitt {@code security.keepass.*}, z. B. für die KeePass-Probe des Einstellungen-Dialogs, bevor
     * die übrige Konfiguration vollständig ist. Probleme dieses Abschnitts kommen als {@link AppConfigException}.
     */
    public static KeePassConfig keePassSection(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        ConfigReader reader = new ConfigReader(properties);
        KeePassConfig keePass = keePass(reader);
        if (reader.hasProblems()) {
            throw new AppConfigException(reader.problems());
        }
        return keePass;
    }

    /**
     * Nur der Abschnitt {@code network.*} (plus {@code chat.baseUrl} für die Standard-Test-URL), z. B. für
     * „Proxy auflösen“ und den HTTPS-Test des Einstellungen-Dialogs, bevor die übrige Konfiguration vollständig ist.
     * Hinweise zu alten Schlüsseln gehen verloren; Probleme kommen als {@link AppConfigException}.
     */
    public static NetworkConfig networkSection(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        // Die Chat-URL zählt hier nur als Vorgabe der Test-URL; ist sie ungültig, fehlt die Vorgabe, mehr nicht.
        URI chatBaseUrl = new ConfigReader(properties).httpUri("chat.baseUrl", false);
        ConfigReader reader = new ConfigReader(properties);
        NetworkConfig network = network(reader, chatBaseUrl, new ArrayList<String>());
        if (reader.hasProblems()) {
            throw new AppConfigException(reader.problems());
        }
        return network;
    }

    /** Die mitgelieferte Beispielkonfiguration (ohne Secrets) als Text, z. B. um sie als Vorlage abzulegen. */
    public static String exampleConfiguration() {
        try (InputStream in = AppConfigLoader.class.getResourceAsStream(EXAMPLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Beispielkonfiguration nicht im Klassenpfad: " + EXAMPLE_RESOURCE);
            }
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Beispielkonfiguration nicht lesbar", e);
        }
    }

    // ------------------------------------------------------------------ Abschnitte

    private static ChatConfig chat(ConfigReader r) {
        URI baseUrl = baseUrl(r, "chat.baseUrl", true);
        String model = modelId(r.required("chat.model"));
        SecretRef apiKeyRef = r.secretRef("chat.apiKeyRef");
        if (apiKeyRef == null && !r.has("chat.apiKeyRef")) {
            r.problem("chat.apiKeyRef", "fehlt (Titel des KeePass-Eintrags mit dem API-Key)");
        }
        String systemPrompt = r.text("chat.systemPrompt", null);
        int connect = r.integer("chat.connectTimeoutMillis", 10000, 0, MAX_TIMEOUT);
        int read = r.integer("chat.readTimeoutMillis", 120000, 0, MAX_TIMEOUT);
        DeveloperRolePolicy policy = r.enumValue("chat.developerRolePolicy", DeveloperRolePolicy.class,
                DeveloperRolePolicy.REJECT);
        ChatOptions defaults;
        try {
            ChatOptions.Builder options = ChatOptions.builder()
                    .temperature(r.optionalDecimal("chat.temperature", 0.0, 2.0))
                    .topP(r.optionalDecimal("chat.topP", 0.0, 1.0))
                    .topK(r.optionalInteger("chat.topK", 1, Integer.MAX_VALUE))
                    .maxTokens(r.optionalInteger("chat.maxTokens", 1, Integer.MAX_VALUE))
                    .presencePenalty(r.optionalDecimal("chat.presencePenalty", -2.0, 2.0))
                    .frequencyPenalty(r.optionalDecimal("chat.frequencyPenalty", -2.0, 2.0))
                    .endUserId(r.text("chat.user", null));
            List<String> stop = r.list("chat.stop");
            if (!stop.isEmpty()) {
                options.stop(stop);
            }
            defaults = options.build();
        } catch (IllegalArgumentException e) {
            r.problem("chat.*", "Chat-Parameter ungültig (siehe Beispielkonfiguration)");
            defaults = ChatOptions.defaults();
        }
        if (baseUrl == null || model == null) {
            return null;
        }
        boolean tools = r.bool("chat.tools.enabled", false);
        return new ChatConfig(baseUrl, model, apiKeyRef, systemPrompt, connect, read, policy, defaults, tools);
    }

    /**
     * Basis-URL ohne Endpunktpfad: Die Adapter hängen {@code /chat/completions}, {@code /embeddings} und
     * {@code /models} selbst an; eine URL, die schon so endet, ist ein Konfigurationsfehler (die Meldung nennt
     * nie den Wert).
     */
    private static URI baseUrl(ConfigReader r, String key, boolean required) {
        URI uri = r.httpUri(key, required);
        if (uri != null && OpenAiCompatibleEmbeddingConfiguration.endpointSuffix(uri.toString()) != null) {
            r.problem(key, "darf nicht auf einen Endpunktpfad enden (/chat/completions, /embeddings, /models); "
                    + "nur die Basis, meist bis /v1");
            return null;
        }
        return uri;
    }

    private static EmbeddingConfig embedding(ConfigReader r, ChatConfig chat) {
        URI baseUrl = baseUrl(r, "embedding.baseUrl", false);
        if (baseUrl == null && chat != null && !r.has("embedding.baseUrl")) {
            baseUrl = chat.baseUrl();
        }
        String model = modelId(r.required("embedding.model"));
        int dimension = r.integer("embedding.dimension", 0, 1, 65536);
        if (dimension == 0) {
            r.problem("embedding.dimension", "fehlt (Pflichtangabe, z. B. 768)");
        }
        SecretRef apiKeyRef = r.has("embedding.apiKeyRef") ? r.secretRef("embedding.apiKeyRef")
                : chat == null ? null : chat.apiKeyRef();
        EmbeddingInputMode inputMode = r.enumValue("embedding.inputMode", EmbeddingInputMode.class,
                EmbeddingInputMode.SINGLE_STRING);
        int batch = r.integer("embedding.maxBatchSize", OpenAiCompatibleEmbeddingConfiguration.DEFAULT_MAX_BATCH_SIZE,
                1, 1000);
        int connect = r.integer("embedding.connectTimeoutMillis",
                OpenAiCompatibleEmbeddingConfiguration.DEFAULT_CONNECT_TIMEOUT_MILLIS, 1, MAX_TIMEOUT);
        int read = r.integer("embedding.readTimeoutMillis",
                OpenAiCompatibleEmbeddingConfiguration.DEFAULT_READ_TIMEOUT_MILLIS, 1, MAX_TIMEOUT);
        if (baseUrl == null || model == null || dimension == 0) {
            return null;
        }
        return new EmbeddingConfig(baseUrl, model, dimension, apiKeyRef, inputMode, batch, connect, read);
    }

    /** Die Modellkennung ohne Katalogpräfix ({@code kipitz:x} → {@code x}); {@code null} bleibt {@code null}. */
    private static String modelId(String text) {
        ModelReference reference = ModelsConfig.parse(text);
        return reference == null ? null : reference.modelId();
    }

    /**
     * Modellverwaltung: eine Auswahl je Kategorie ({@code chat.model}, {@code embedding.model},
     * {@code model.<kategorie>}) und der optionale lokale Sidecar ({@code models.local.*}). Ohne Java-Pfad bleibt
     * der Sidecar aus; fehlt bei gesetztem Java-Pfad das Jar, gibt es einen Hinweis statt eines Fehlers.
     */
    private static ModelsConfig models(ConfigReader r, List<String> warnings) {
        ModelSelections selections = ModelSelections.none();
        for (ModelCategory category : ModelCategory.values()) {
            ModelReference reference = ModelsConfig.parse(r.text(ModelsConfig.keyOf(category), null));
            selections = selections.with(category, reference);
        }
        Path java = r.path("models.local.java", null);
        Path jar = r.path("models.local.sidecarJar", null);
        Path modelRoot = r.path("models.local.modelRoot", AppPaths.appDirectory().resolve("local-models"));
        int readyTimeout = r.integer("models.local.readyTimeoutMillis", 60000, 1000, MAX_TIMEOUT);
        LocalSidecarConfig local = null;
        if (java != null && jar == null) {
            warnings.add("models.local.java ist gesetzt, models.local.sidecarJar fehlt: lokale Modelle bleiben aus.");
        } else if (java != null) {
            local = new LocalSidecarConfig(java, jar, modelRoot, readyTimeout);
        }
        return new ModelsConfig(selections, local);
    }

    private static KnowledgeConfig knowledge(ConfigReader r) {
        Path indexDirectory = r.path("knowledge.indexDirectory", AppPaths.defaultIndexDirectory());
        boolean indexOnStartup = r.bool("knowledge.indexOnStartup", true);
        int maxTokens = r.integer("knowledge.chunk.maxTokens", KnowledgeChunkingPolicy.DEFAULT_MAX_TOKENS,
                KnowledgeChunkingPolicy.MIN_MAX_TOKENS, 100000);
        int overlap = r.integer("knowledge.chunk.overlapSentences", KnowledgeChunkingPolicy.DEFAULT_OVERLAP_SENTENCES,
                0, 100);
        KnowledgeChunkingPolicy chunking;
        try {
            chunking = KnowledgeChunkingPolicy.of(maxTokens, overlap);
        } catch (IllegalArgumentException e) {
            r.problem("knowledge.chunk.*", "Chunking-Einstellung ungültig");
            chunking = KnowledgeChunkingPolicy.defaults();
        }
        int batch = r.integer("knowledge.embeddingBatchSize", 16, 1, 1000);

        RetrievalSettings retrieval;
        try {
            retrieval = RetrievalSettings.builder()
                    .keywordEnabled(r.bool("retrieval.keywordEnabled", true))
                    .semanticEnabled(r.bool("retrieval.semanticEnabled", true))
                    .keywordCandidates(r.integer("retrieval.keywordCandidates", RetrievalSettings.DEFAULT_CANDIDATES,
                            1, RetrievalSettings.MAX_CANDIDATES))
                    .semanticCandidates(r.integer("retrieval.semanticCandidates", RetrievalSettings.DEFAULT_CANDIDATES,
                            1, RetrievalSettings.MAX_CANDIDATES))
                    .keywordWeight(r.decimal("retrieval.keywordWeight", 1.0, 0.0, 1000.0))
                    .semanticWeight(r.decimal("retrieval.semanticWeight", 1.0, 0.0, 1000.0))
                    .rankConstant(r.integer("retrieval.rankConstant", RetrievalSettings.DEFAULT_RANK_CONSTANT, 1, 100000))
                    .maxResults(r.integer("retrieval.maxResults", RetrievalSettings.DEFAULT_MAX_RESULTS, 1,
                            RetrievalSettings.MAX_CANDIDATES))
                    .minSemanticScore(r.decimal("retrieval.minSemanticScore", -1.0, -1.0, 1.0))
                    .build();
        } catch (IllegalArgumentException e) {
            r.problem("retrieval.*", "Retrieval-Einstellung ungültig (siehe Beispielkonfiguration)");
            retrieval = RetrievalSettings.defaults();
        }
        ContextSettings context;
        try {
            context = ContextSettings.defaults()
                    .withMaxContextTokens(r.integer("context.maxTokens", ContextSettings.DEFAULT_MAX_CONTEXT_TOKENS, 1,
                            1000000))
                    .withMaxSources(r.integer("context.maxSources", ContextSettings.DEFAULT_MAX_SOURCES, 1, 1000));
            String instruction = r.text("context.instruction", null);
            if (instruction != null) {
                context = context.withInstruction(instruction);
            }
        } catch (IllegalArgumentException e) {
            r.problem("context.*", "Kontext-Einstellung ungültig (siehe Beispielkonfiguration)");
            context = ContextSettings.defaults();
        }
        return new KnowledgeConfig(indexDirectory, indexOnStartup, chunking, batch, retrieval, context);
    }

    /**
     * Die Quellen aus {@code sources}. Probleme einer Quelle (ungültige oder doppelte ID, fehlende oder ungültige
     * Felder) werden nicht zu Konfigurationsfehlern, sondern zu einem Hinweis, und die Quelle wird übersprungen.
     */
    private static List<SourceConfig> sources(ConfigReader r, List<String> warnings) {
        List<SourceConfig> sources = new ArrayList<SourceConfig>();
        Set<String> seen = new HashSet<String>();
        for (String id : r.list("sources")) {
            if (!SOURCE_ID.matcher(id).matches()) {
                warnings.add("Wissensquelle „" + id + "“ wird übersprungen: " + SOURCE_ID_RULE);
                continue;
            }
            if (!seen.add(id)) {
                warnings.add("Wissensquelle „" + id + "“ steht doppelt in sources; der zweite Eintrag wird übersprungen.");
                continue;
            }
            int mark = r.problemCount();
            SourceConfig source = source(r, id);
            List<String> problems = r.takeProblemsSince(mark);
            if (!problems.isEmpty() || source == null) {
                r.markRead("source." + id + ".");
                warnings.add("Wissensquelle „" + id + "“ wird übersprungen, bis sie im Reiter „Wissensquellen“ "
                        + "korrigiert ist: " + String.join("; ", problems));
                continue;
            }
            sources.add(source);
        }
        return sources;
    }

    /**
     * Genau eine Quelle {@code source.<id>.*}, streng geprüft: der Bearbeiten-Dialog des Drawers prüft damit seinen
     * Entwurf, und die Anwendung baut damit eine neu gespeicherte Quelle, ohne neu zu starten.
     *
     * @throws AppConfigException mit allen Problemen dieser Quelle (Schlüssel und Erwartung, nie Werte)
     */
    public static SourceConfig sourceSection(Properties properties, String id) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        String trimmed = id == null ? "" : id.trim();
        if (!SOURCE_ID.matcher(trimmed).matches()) {
            throw new AppConfigException(Collections.singletonList("sources: " + SOURCE_ID_RULE));
        }
        ConfigReader reader = new ConfigReader(properties);
        SourceConfig source = source(reader, trimmed);
        if (reader.hasProblems() || source == null) {
            throw new AppConfigException(reader.hasProblems() ? reader.problems()
                    : Collections.singletonList("source." + trimmed + ".type: fehlt (Pflichtangabe)"));
        }
        return source;
    }

    private static SourceConfig source(ConfigReader r, String id) {
        String prefix = "source." + id + ".";
        String type = r.required(prefix + "type");
        if (type == null) {
            return null;
        }
        KnowledgeSourceId sourceId = KnowledgeSourceId.of(id);
        String lower = type.toLowerCase(Locale.ROOT);
        boolean enabled = r.bool(prefix + "enabled", true);
        if ("files".equals(lower)) {
            return localFiles(r, prefix, sourceId, enabled);
        }
        SourceScope scope = scope(r, prefix);
        SecretRef credentialRef = r.secretRef(prefix + "credentialRef");
        if ("mediawiki".equals(lower)) {
            return mediaWiki(r, prefix, sourceId, scope, credentialRef, enabled);
        }
        if ("confluence".equals(lower)) {
            return confluence(r, prefix, sourceId, scope, credentialRef, enabled);
        }
        r.problem(prefix + "type", "unbekannter Quelltyp; erlaubt sind mediawiki, confluence und files");
        return null;
    }

    private static SourceScope scope(ConfigReader r, String prefix) {
        List<String> startPoints = r.list(prefix + "startPoints");
        if (startPoints.isEmpty()) {
            r.problem(prefix + "startPoints", "fehlt (mindestens ein Startpunkt)");
            startPoints = Collections.singletonList("-");
        }
        int maxDepth = r.integer(prefix + "maxDepth", 1, 0, 100);
        int maxResources = r.integer(prefix + "maxResources", SourceScope.DEFAULT_MAX_RESOURCES, 1, 1000000);
        try {
            return SourceScope.builder().startPoints(startPoints).maxDepth(maxDepth).maxResources(maxResources).build();
        } catch (IllegalArgumentException e) {
            r.problem(prefix + "startPoints", "Crawl-Umfang ungültig (siehe Beispielkonfiguration)");
            return SourceScope.of("-");
        }
    }

    /** Lokales Verzeichnis: Startpunkte Standard {@code .}, Tiefe Standard 20 (praktisch rekursiv). */
    private static SourceConfig localFiles(ConfigReader r, String prefix, KnowledgeSourceId sourceId, boolean enabled) {
        Path directory = r.path(prefix + "directory", null);
        List<String> startPoints = r.list(prefix + "startPoints");
        int maxDepth = r.integer(prefix + "maxDepth", 20, 0, 100);
        int maxResources = r.integer(prefix + "maxResources", 5000, 1, 1000000);
        int maxFileMegabytes = r.integer(prefix + "maxFileMegabytes", 50, 1, 2000);
        if (directory == null) {
            r.problem(prefix + "directory", "fehlt (Pflichtangabe: Verzeichnis mit den Dokumenten)");
            return null;
        }
        SourceScope scope = SourceScope.builder()
                .startPoints(startPoints.isEmpty() ? Collections.singletonList(".") : startPoints)
                .maxDepth(maxDepth).maxResources(maxResources).build();
        return new LocalFilesSourceConfig(sourceId, scope, directory, maxFileMegabytes * 1024L * 1024L, enabled);
    }

    private static SourceConfig mediaWiki(ConfigReader r, String prefix, KnowledgeSourceId sourceId,
                                          SourceScope scope, SecretRef credentialRef, boolean enabled) {
        URI apiUrl = r.httpUri(prefix + "apiUrl", true);
        String siteKey = r.text(prefix + "siteKey", sourceId.value().toLowerCase(Locale.ROOT));
        String displayName = r.text(prefix + "displayName", null);
        boolean requiresLogin = r.bool(prefix + "requiresLogin", credentialRef != null);
        int connect = r.integer(prefix + "connectTimeoutMillis", 15000, 1, MAX_TIMEOUT);
        int read = r.integer(prefix + "readTimeoutMillis", 30000, 1, MAX_TIMEOUT);
        // Eigener Wert der Quelle vor network.http.userAgent; ohne beide gilt der Standard des Adapters.
        String userAgent = r.text(prefix + "userAgent", r.text("network.http.userAgent", null));
        List<String> namespaces = r.list(prefix + "linkNamespaces");
        if (apiUrl == null) {
            return null;
        }
        try {
            MediaWikiSiteConfig.Builder site = MediaWikiSiteConfig.builder(siteKey, apiUrl.toString())
                    .requiresLogin(requiresLogin)
                    .connectTimeoutMillis(connect)
                    .readTimeoutMillis(read);
            if (displayName != null) {
                site.displayName(displayName);
            }
            if (userAgent != null) {
                site.userAgent(userAgent);
            }
            if (!namespaces.isEmpty()) {
                int[] ids = new int[namespaces.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = Integer.parseInt(namespaces.get(i));
                }
                site.linkNamespaces(ids);
            }
            return new MediaWikiSourceConfig(sourceId, scope, credentialRef, site.build(), enabled);
        } catch (NumberFormatException e) {
            r.problem(prefix + "linkNamespaces", "Namensräume müssen ganze Zahlen sein");
            return null;
        } catch (IllegalArgumentException e) {
            r.problem(prefix + "*", "MediaWiki-Einstellung ungültig (siehe Beispielkonfiguration)");
            return null;
        }
    }

    private static SourceConfig confluence(ConfigReader r, String prefix, KnowledgeSourceId sourceId,
                                           SourceScope scope, SecretRef credentialRef, boolean enabled) {
        URI baseUrl = r.httpUri(prefix + "baseUrl", true);
        int pageSize = r.integer(prefix + "pageSize", 50, 1, 500);
        boolean attachments = r.bool(prefix + "includeAttachments", false);
        int maxAttachmentBytes = r.integer(prefix + "maxAttachmentBytes", 5 * 1024 * 1024, 1, Integer.MAX_VALUE);
        int maxResponseBytes = r.integer(prefix + "maxResponseBytes", 20 * 1024 * 1024, 1, Integer.MAX_VALUE);
        boolean allowInsecureHttp = r.bool(prefix + "allowInsecureHttp", false);
        List<String> spaceKeys = r.list(prefix + "searchSpaceKeys");
        int connect = r.integer(prefix + "connectTimeoutMillis", 15000, 1, MAX_TIMEOUT);
        int read = r.integer(prefix + "readTimeoutMillis", 60000, 1, MAX_TIMEOUT);
        ClientCertificateConfig certificate = clientCertificate(r, prefix + "clientCertificate.");
        if (baseUrl == null) {
            return null;
        }
        try {
            ConfluenceConfig.Builder builder = ConfluenceConfig.builder(baseUrl)
                    .credentialRef(credentialRef)
                    .pageSize(pageSize)
                    .includeAttachments(attachments)
                    .maxAttachmentBytes(maxAttachmentBytes)
                    .maxResponseBytes(maxResponseBytes)
                    .allowInsecureHttp(allowInsecureHttp);
            for (String spaceKey : spaceKeys) {
                builder.searchSpaceKey(spaceKey);
            }
            return new ConfluenceSourceConfig(sourceId, scope, builder.build(), connect, read, certificate, enabled);
        } catch (IllegalArgumentException e) {
            r.problem(prefix + "*", "Confluence-Einstellung ungültig (siehe Beispielkonfiguration)");
            return null;
        }
    }

    private static ClientCertificateConfig clientCertificate(ConfigReader r, String prefix) {
        String alias = r.text(prefix + "alias", null);
        Path keyStore = r.path(prefix + "keyStoreFile", null);
        SecretRef passwordRef = r.secretRef(prefix + "keyStorePasswordRef");
        if (alias == null) {
            if (keyStore != null || passwordRef != null) {
                r.problem(prefix + "alias", "fehlt, obwohl ein Client-Zertifikat konfiguriert ist");
            }
            return null;
        }
        if (keyStore == null && passwordRef != null) {
            r.problem(prefix + "keyStorePasswordRef", "nur zusammen mit keyStoreFile sinnvoll (Windows-MY hat kein Passwort)");
        }
        return new ClientCertificateConfig(alias, keyStore, passwordRef);
    }

    private static KeePassConfig keePass(ConfigReader r) {
        boolean enabled = r.bool("security.keepass.enabled", true);
        String store = r.text("security.keepass.pairingKeyStore", "file").toLowerCase(Locale.ROOT);
        Path keyFile = null;
        if ("file".equals(store)) {
            keyFile = r.path("security.keepass.pairingKeyFile", AppPaths.defaultPairingKeyFile());
        } else if (!"memory".equals(store)) {
            r.problem("security.keepass.pairingKeyStore", "muss file oder memory sein");
        }
        KeePassRpcConfig rpc;
        try {
            rpc = KeePassRpcConfig.builder()
                    .host(r.text("security.keepass.host", KeePassRpcConfig.DEFAULT_HOST))
                    .port(r.integer("security.keepass.port", KeePassRpcConfig.DEFAULT_PORT, 1, 65535))
                    .origin(r.text("security.keepass.origin", KeePassRpcConfig.DEFAULT_ORIGIN))
                    .clientId(r.text("security.keepass.clientId", KeePassRpcConfig.DEFAULT_CLIENT_ID))
                    .clientDisplayName(r.text("security.keepass.clientDisplayName", "Enterprise AI Client"))
                    .timeoutMillis(r.integer("security.keepass.timeoutMillis", KeePassRpcConfig.DEFAULT_TIMEOUT_MILLIS,
                            1, MAX_TIMEOUT))
                    .build();
        } catch (IllegalArgumentException e) {
            r.problem("security.keepass.*", "KeePassRPC-Einstellung ungültig (siehe Beispielkonfiguration)");
            rpc = KeePassRpcConfig.defaults();
        }
        return new KeePassConfig(enabled, rpc, keyFile);
    }

    /**
     * {@code network.*}: Modusnamen sind die der Bibliothek win-proxy-java. Alte Schlüssel und Namen der Fassungen
     * bis 0.1.3 werden nur gelesen und übersetzt (NONE → DISABLED, MANUAL → MANUAL_PROXY, SYSTEM →
     * WINDOWS_STATIC_PROXY, AUTO → PAC_URL_MANUAL mit pacUrl, sonst PAC_URL_POWERSHELL;
     * {@code network.tls.useWindowsCertificateStore} → beide Windows-Quellen); der Einstellungen-Dialog schreibt
     * beim nächsten Speichern die neuen. Jede Übersetzung steht als Hinweis in {@code warnings}.
     */
    static NetworkConfig network(ConfigReader r, URI chatBaseUrl, List<String> warnings) {
        NetworkConfig.Builder b = NetworkConfig.builder();
        String pacUrl = pacUrl(r);
        ProxyMode mode = proxyMode(r, pacUrl, warnings);
        b.proxyMode(mode);
        String host = r.text("network.proxy.host", null);
        int port = r.integer("network.proxy.port", 0, 0, 65535);
        if (mode == ProxyMode.MANUAL_PROXY) {
            if (host == null) {
                r.problem("network.proxy.host", "fehlt (Pflicht bei network.proxy.mode=MANUAL_PROXY)");
            }
            if (port == 0) {
                r.problem("network.proxy.port", "fehlt oder 0 (Pflicht bei network.proxy.mode=MANUAL_PROXY)");
            }
        }
        if (mode == ProxyMode.PAC_URL_MANUAL && pacUrl == null && !r.has("network.proxy.pacUrl")) {
            r.problem("network.proxy.pacUrl", "fehlt (Pflicht bei network.proxy.mode=PAC_URL_MANUAL)");
        }
        b.proxy(host, port);
        b.nonProxyHosts(r.list("network.proxy.nonProxyHosts"));
        b.pacUrl(pacUrl);
        b.pacDiscoveryScript(r.text("network.proxy.pacDiscoveryScript", null));
        URI testUrl = r.httpUri("network.proxy.testUrl", false);
        b.testUrl(testUrl != null ? testUrl : chatBaseUrl == null ? null : modelsUrl(chatBaseUrl));
        b.resolveTimeoutMillis(r.integer("network.proxy.resolveTimeoutMillis",
                NetworkConfig.DEFAULT_RESOLVE_TIMEOUT_MILLIS, 1000, MAX_TIMEOUT));
        ProxyAuthMode auth = r.enumValue("network.proxy.auth.mode", ProxyAuthMode.class, ProxyAuthMode.NONE);
        SecretRef credentialRef = r.secretRef("network.proxy.auth.credentialRef");
        if (auth == ProxyAuthMode.BASIC && credentialRef == null && !r.has("network.proxy.auth.credentialRef")) {
            r.problem("network.proxy.auth.credentialRef", "fehlt (Pflicht bei network.proxy.auth.mode=BASIC: Titel "
                    + "des KeePass-Eintrags mit Benutzername und Passwort für den Proxy)");
        }
        b.proxyAuth(auth, auth == ProxyAuthMode.BASIC ? credentialRef : null);
        b.userAgent(r.text("network.http.userAgent", null));
        b.preferIpv6(r.bool("network.http.preferIPv6", false));
        boolean legacyWindowsStore = r.bool("network.tls.useWindowsCertificateStore", true);
        if (r.has("network.tls.useWindowsCertificateStore")) {
            warnings.add("network.tls.useWindowsCertificateStore ist der alte Schalter; er gilt für "
                    + "network.tls.useWindowsRoot und network.tls.useWindowsCaStores, solange diese fehlen. Der "
                    + "Einstellungen-Dialog schreibt beim Speichern die neuen Schlüssel.");
        }
        b.tls(r.bool("network.tls.useJvmDefault", true),
                r.bool("network.tls.useWindowsRoot", legacyWindowsStore),
                r.bool("network.tls.useWindowsCaStores", legacyWindowsStore));
        b.caCertificatesFile(r.path("network.tls.caCertificatesFile", null));
        return b.build();
    }

    /** {@code <baseUrl>/models} ohne doppelten Schrägstrich. */
    static URI modelsUrl(URI baseUrl) {
        String text = baseUrl.toString();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return URI.create(text + "/models");
    }

    /** Modusname der Bibliothek oder einer der alten Namen; die Übersetzung erzeugt einen Hinweis. */
    private static ProxyMode proxyMode(ConfigReader r, String pacUrl, List<String> warnings) {
        String legacyDiscovery = r.text("network.proxy.pacDiscovery", null);
        if (legacyDiscovery != null) {
            warnings.add("network.proxy.pacDiscovery wird nicht mehr ausgewertet; den Modus PAC_URL_POWERSHELL, "
                    + "PAC_URL_WSCRIPT oder PAC_URL_WINDOWS_SETTINGS wählen. Der Einstellungen-Dialog entfernt den "
                    + "Schlüssel beim Speichern.");
        }
        String raw = r.text("network.proxy.mode", null);
        if (raw == null) {
            return ProxyMode.PAC_URL_POWERSHELL;
        }
        String name = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        ProxyMode legacy = legacyMode(name, pacUrl);
        if (legacy != null) {
            warnings.add("network.proxy.mode=" + name + " ist der alte Name und gilt als " + legacy
                    + "; der Einstellungen-Dialog schreibt beim Speichern den neuen.");
            return legacy;
        }
        return r.enumValue("network.proxy.mode", ProxyMode.class, ProxyMode.PAC_URL_POWERSHELL);
    }

    /** Die Modusnamen der Fassungen bis 0.1.3; {@code null}, wenn {@code name} keiner davon ist. */
    public static ProxyMode legacyMode(String name, String pacUrl) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "NONE":
                return ProxyMode.DISABLED;
            case "MANUAL":
                return ProxyMode.MANUAL_PROXY;
            case "SYSTEM":
                return ProxyMode.WINDOWS_STATIC_PROXY;
            case "AUTO":
                return pacUrl == null ? ProxyMode.PAC_URL_POWERSHELL : ProxyMode.PAC_URL_MANUAL;
            default:
                return null;
        }
    }

    /** Die PAC-Adresse muss eine absolute http-, https- oder file-URL sein; die Meldung nennt nie den Wert. */
    private static String pacUrl(ConfigReader r) {
        String value = r.text("network.proxy.pacUrl", null);
        if (value == null) {
            return null;
        }
        String scheme;
        try {
            scheme = new java.net.URI(value).getScheme();
        } catch (java.net.URISyntaxException e) {
            scheme = null;
        }
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme))) {
            r.problem("network.proxy.pacUrl", "keine absolute http-, https- oder file-URL");
            return null;
        }
        return value;
    }

    private static AgentConfig agent(ConfigReader r) {
        boolean enabled = r.bool("agent.enabled", false);
        String command = r.text("agent.command", null);
        List<String> args = CommandLine.split(r.text("agent.args", ""));
        int timeoutSeconds = r.integer("agent.requestTimeoutSeconds", 30, 1, 86400);
        String endpointId = r.text("agent.mcpEndpointId", "agent-tools");
        String displayName = r.text("agent.mcpDisplayName", "Wissenswerkzeuge");
        if (enabled && command == null) {
            r.problem("agent.command", "fehlt (Pflicht bei agent.enabled=true)");
        }
        KnowledgeToolSettings defaults = KnowledgeToolSettings.defaults();
        KnowledgeToolSettings tools = defaults
                .withMaxResponseChars(r.integer("agent.tools.maxResponseChars", defaults.maxResponseChars(), 1000, 10000000))
                .withSnippetChars(r.integer("agent.tools.snippetChars", defaults.snippetChars(), 80, 100000))
                .withDefaultMaxResults(r.integer("agent.tools.defaultMaxResults", defaults.defaultMaxResults(), 1, 1000))
                .withMaxFailuresListed(r.integer("agent.tools.maxFailuresListed", defaults.maxFailuresListed(), 0, 10000));
        return new AgentConfig(enabled, command, args, Duration.ofSeconds(timeoutSeconds), endpointId, displayName, tools);
    }
}
