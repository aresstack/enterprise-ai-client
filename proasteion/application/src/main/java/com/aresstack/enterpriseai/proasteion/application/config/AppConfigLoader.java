package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlanningPolicy;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeToolSettings;
import com.aresstack.enterpriseai.astu.acropolis.rag.ContextSettings;
import com.aresstack.enterpriseai.astu.acropolis.rag.EvidencePolicy;
import com.aresstack.enterpriseai.astu.acropolis.rag.FollowUpResearch;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalSettings;
import com.aresstack.enterpriseai.astu.acropolis.speech.ReadAloudService;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechChunking;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.chat.openai.DeveloperRolePolicy;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelSelections;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.embedding.openai.EmbeddingInputMode;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassRpcConfig;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
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
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Liest {@link AppConfig} aus einer Properties-Datei (UTF-8). Alle Schlüssel sind in
 * {@code enterprise-ai-client.example.properties} (Ressource dieses Pakets) dokumentiert.
 *
 * <p>Regeln: Pflicht sind nur Chat-Basis-URL, -Modell und -API-Key-Referenz ({@code chat.apiKeyRef}, Titel des
 * KeePass-Eintrags) sowie Embedding-Modell und -Dimension; alles andere hat Defaults (AP3/AP6/AP10/AP14). Probleme
 * werden gesammelt und als eine {@link AppConfigException} gemeldet, die nur Schlüssel und Erwartung nennt, nie
 * den abgelehnten Wert (auch nicht aus verschachtelten Ausnahmen der Adapter-Konfigurationen). Unbekannte Schlüssel
 * sind kein Fehler, erscheinen aber als Hinweis in {@link AppConfig#warnings()}. Wissensquellen liest der Loader
 * typneutral ({@link #sourceDefinitions}); ihre Einstellungen prüft der Quellen-Port des jeweiligen Adapters beim
 * Start, eine fehlerhafte Quelle hält den Start nicht auf und lässt sich im Drawer-Reiter „Wissensquellen“
 * korrigieren.
 */
public final class AppConfigLoader {

    public static final String EXAMPLE_RESOURCE = "enterprise-ai-client.example.properties";
    /** Modellverzeichnis der lokalen Stimmen unter dem Anwendungsverzeichnis, wenn {@code models.local.modelRoot} fehlt. */
    public static final String DEFAULT_LOCAL_MODEL_DIRECTORY = "local-models";
    /** Vorladen: höchstens so viele Treffer ab der Cursorposition (MainframeMate confluencePrefetchMaxItems). */
    public static final String KNOWLEDGE_PREFETCH_MAX_ITEMS_KEY = "knowledge.prefetch.maxItems";
    /** Vorladen: parallele Abrufe im Hintergrund (MainframeMate confluencePrefetchConcurrency). */
    public static final String KNOWLEDGE_PREFETCH_CONCURRENCY_KEY = "knowledge.prefetch.concurrency";
    /** Vorladen: Größe des Speicher-Caches in MB (MainframeMate confluencePrefetchCacheMaxMb). */
    public static final String KNOWLEDGE_PREFETCH_CACHE_MB_KEY = "knowledge.prefetch.cacheMaxMb";
    /** Rohindex: Threads, die geladene Seiten sofort lexikalisch durchsuchbar machen (1–8, Standard 2). */
    public static final String KNOWLEDGE_RAW_WORKERS_KEY = "knowledge.raw.workers";
    /** Aufbereitung: Dokumente, die gleichzeitig in Passagen zerlegt und eingebettet werden (1–16, Standard 4). */
    public static final String KNOWLEDGE_ENRICHMENT_WORKERS_KEY = "knowledge.enrichment.workers";
    /** Aufbereitung: gleichzeitige Aufrufe des Embedding-Endpunkts (1–32, Standard 4); unabhängig vom Chat-Modell. */
    public static final String EMBEDDING_MAX_CONCURRENCY_KEY = "embedding.maxConcurrency";
    /** Texte je Aufruf des Embedding-Endpunkts beim Aufbereiten (1–1000, Standard 16). */
    public static final String KNOWLEDGE_EMBEDDING_BATCH_SIZE_KEY = "knowledge.embeddingBatchSize";
    /** Textfenster je geladener, noch nicht aufbereiteter Seite für die Evidenzbewertung (1–10, Standard 2). */
    public static final String RETRIEVAL_RAW_WINDOWS_KEY = "retrieval.raw.windowsPerDocument";
    /** Ungefähre Länge eines Textfensters in Zeichen (100–10000, Standard 700). */
    public static final String RETRIEVAL_RAW_WINDOW_CHARS_KEY = "retrieval.raw.windowChars";
    /** Werkzeugrunden je Antwort im normalen Chat (1–32, Standard 8), danach eine Abschlussrunde ohne Werkzeuge. */
    public static final String CHAT_TOOLS_MAX_ROUNDS_KEY = "chat.tools.maxRounds";
    /** Fristen und Wiederholungen der Modellanfrage (Einstellungen → Netzwerk & Agent → Zeitlimits und Wiederholungen). */
    public static final String CHAT_CONNECT_TIMEOUT_KEY = "chat.connectTimeoutMillis";
    public static final String CHAT_READ_TIMEOUT_KEY = "chat.readTimeoutMillis";
    public static final String CHAT_RETRY_COUNT_KEY = "chat.retryCount";
    public static final String CHAT_RETRY_DELAY_KEY = "chat.retryDelayMillis";
    /** Frist und Wiederholungen der KeePassRPC-Zugriffe. */
    public static final String KEEPASS_TIMEOUT_KEY = "security.keepass.timeoutMillis";
    public static final String KEEPASS_RETRY_COUNT_KEY = "security.keepass.retryCount";
    public static final String KEEPASS_RETRY_DELAY_KEY = "security.keepass.retryDelayMillis";
    /** Wiederprüfungen des Einrichtungsassistenten, solange KeePass oder der KI-Dienst kurz nicht erreichbar ist. */
    public static final String KEEPASS_SETUP_RECHECK_COUNT_KEY = "security.keepass.setupRecheckCount";
    public static final String KEEPASS_SETUP_RECHECK_DELAY_KEY = "security.keepass.setupRecheckDelayMillis";
    /** Wiederholungen der Quellenzugriffe (Live-Suche, Seitenabruf, Wissenssuche des Agenten). */
    public static final String SOURCES_RETRY_COUNT_KEY = "knowledge.sources.retryCount";
    public static final String SOURCES_RETRY_DELAY_KEY = "knowledge.sources.retryDelayMillis";
    /** Höchstzahl der Suchauszüge im RAG-Kontext einer normalen Chat-Antwort ({@link ContextSettings#maxSources()}). */
    public static final String CONTEXT_MAX_SOURCES_KEY = "context.maxSources";
    /** Token-Budget des RAG-Kontextblocks ({@link ContextSettings#maxContextTokens()}). */
    public static final String CONTEXT_MAX_TOKENS_KEY = "context.maxTokens";
    /** Höchstzahl der fusionierten Suchtreffer vor dem Kontextaufbau ({@link RetrievalSettings#maxResults()}). */
    public static final String RETRIEVAL_MAX_RESULTS_KEY = "retrieval.maxResults";
    /** Mindestzahl verschiedener Ressourcen, bevor die Evidenz als ausreichend gilt (0 = nur Prüfung, Standard 1). */
    public static final String RETRIEVAL_MIN_INDEPENDENT_SOURCES_KEY = "retrieval.minIndependentSources";
    /** Nachsuchen mit genauerer Anfrage, wenn die Treffer nicht reichen (0–5, Standard 2). */
    public static final String RETRIEVAL_MAX_FOLLOW_UPS_KEY = "retrieval.maxFollowUps";
    /** Treffer vor der Übernahme vom Chat-Modell gegen die Frage bewerten lassen (Standard true). */
    public static final String RETRIEVAL_RERANK_KEY = "retrieval.rerank";
    /** Candidates a selected cross-encoder ({@code model.rerank}) passes on to the evidence check, 1–40. */
    public static final String RETRIEVAL_RERANK_TOP_K_KEY = "retrieval.rerankTopK";
    /** Volltext-Kandidaten je Suche ({@link RetrievalSettings#keywordCandidates()}). */
    public static final String RETRIEVAL_KEYWORD_CANDIDATES_KEY = "retrieval.keywordCandidates";
    /** Semantische Kandidaten je Suche ({@link RetrievalSettings#semanticCandidates()}). */
    public static final String RETRIEVAL_SEMANTIC_CANDIDATES_KEY = "retrieval.semanticCandidates";
    /** Live-Suche: auf so viele indexierte Treffer wartet die erste Evidenzbewertung einer Suche. */
    public static final String LIVE_SEARCH_INITIAL_PAGES_KEY = "retrieval.liveSearch.initialPages";
    /** Live-Suche: auf höchstens so viele indexierte Treffer wartet eine Suche blockierend. */
    public static final String LIVE_SEARCH_MAX_PAGES_KEY = "retrieval.liveSearch.maxPages";
    /** Live-Suche: so viele Treffer je Quelle (gehen alle an den Vorlader). */
    public static final String LIVE_SEARCH_HITS_PER_SOURCE_KEY = "retrieval.liveSearch.hitsPerSource";
    /** Sprache des Satzmodells (OpenNLP) für die Passagenbildung beim Indexieren: de oder en. */
    public static final String KNOWLEDGE_PASSAGE_LANGUAGE_KEY = "knowledge.passages.language";

    /** Präfix der Quellenliste: {@code source.<n>.*}, {@code n} = Listenposition ab 1, keine Identität. */
    public static final String SOURCE_PREFIX = "source.";
    /** Feld mit der kanonischen Adresse der Quelle ({@code source.<n>.address}), ihrer Identität. */
    public static final String SOURCE_ADDRESS_FIELD = "address";
    private static final Pattern SOURCE_SLOT = Pattern.compile("source\\.([1-9][0-9]{0,5})\\..+");
    private static final int MAX_TIMEOUT = 3600000;
    /** Directory of the global metadata catalog (#106); default {@code <app directory>/facts}. */
    public static final String FACTS_DIRECTORY_KEY = "facts.directory";
    /** How long a catalog writer waits for a locked row, in milliseconds. */
    public static final String FACTS_LOCK_TIMEOUT_KEY = "facts.lockTimeoutMillis";

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
        List<SourceDefinition> sources = sources(reader, warnings);
        KeePassConfig keePass = keePass(reader);
        NetworkConfig network = network(reader, chat == null ? null : chat.baseUrl(), warnings);
        AgentConfig agent = agent(reader);
        ModelsConfig models = models(reader, warnings);
        Set<String> disabledTools = new LinkedHashSet<String>(reader.list("tools.disabled"));
        BubbleColorsConfig bubbleColors = bubbleColors(reader, warnings);
        FactStoreConfig facts = new FactStoreConfig(reader.path(FACTS_DIRECTORY_KEY, AppPaths.defaultFactDirectory()),
                reader.integer(FACTS_LOCK_TIMEOUT_KEY, FactStoreConfig.DEFAULT_LOCK_TIMEOUT_MILLIS, 0, MAX_TIMEOUT));

        for (String unread : reader.unreadKeys()) {
            warnings.add("Unbekannter Konfigurationsschlüssel wird ignoriert: " + unread);
        }
        if (!keePass.enabled()) {
            List<String> refs = new ArrayList<String>();
            if (chat != null && VaultLocations.isVault(chat.apiKeyRef())) {
                refs.add("chat.apiKeyRef");
            }
            if (embedding != null && embedding.apiKeyRef() != null && !reader.text("embedding.apiKeyRef", "").isEmpty()) {
                refs.add("embedding.apiKeyRef");
            }
            for (SourceDefinition source : sources) {
                if (source.settings().has("credentialRef") && !source.settings().get("credentialRef").trim()
                        .regionMatches(true, 0, VaultLocations.ASK_SCHEME, 0, VaultLocations.ASK_SCHEME.length())) {
                    refs.add(source.address() + " credentialRef");
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
                disabledTools, bubbleColors, facts, warnings);
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
        SecretRef apiKeyRef = r.secretRef("chat.apiKeyRef");
        // Mit KeePass-Eintrag darf die URL fehlen: dann gilt die URL des Eintrags (VaultLocations).
        URI baseUrl = baseUrl(r, "chat.baseUrl", !VaultLocations.isVault(apiKeyRef));
        String model = modelId(r.required("chat.model"));
        if (apiKeyRef == null && !r.has("chat.apiKeyRef")) {
            r.problem("chat.apiKeyRef", "fehlt (Titel des KeePass-Eintrags mit dem API-Key)");
        }
        String systemPrompt = r.text("chat.systemPrompt", ChatConfig.DEFAULT_SYSTEM_PROMPT);
        int connect = r.integer(CHAT_CONNECT_TIMEOUT_KEY, ChatConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS, 0, MAX_TIMEOUT);
        int read = r.integer(CHAT_READ_TIMEOUT_KEY, ChatConfig.DEFAULT_READ_TIMEOUT_MILLIS, 0, MAX_TIMEOUT);
        RetryPolicy retry = retry(r, CHAT_RETRY_COUNT_KEY, ChatConfig.DEFAULT_RETRY_COUNT, CHAT_RETRY_DELAY_KEY,
                ChatConfig.DEFAULT_RETRY_DELAY_MILLIS);
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
        // Kein 0 = unbegrenzt: ein Modell, das immer weiter Werkzeuge verlangt, hinge sonst endlos.
        int maxToolRounds = r.integer(CHAT_TOOLS_MAX_ROUNDS_KEY, ChatCompletionsToolLoop.DEFAULT_MAX_ROUNDS,
                ChatCompletionsToolLoop.MIN_MAX_ROUNDS, ChatCompletionsToolLoop.MAX_MAX_ROUNDS);
        String assistantName = r.text(ChatConfig.ASSISTANT_DISPLAY_NAME_KEY,
                ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME);
        return new ChatConfig(baseUrl, model, apiKeyRef, systemPrompt, connect, read, policy, defaults, tools,
                maxToolRounds, retry, assistantName, r.bool(ChatConfig.TURN_ANALYSIS_LOG_KEY, false),
                new AnswerPlanningPolicy(r.bool(ChatConfig.ANSWER_PLANNING_ENABLED_KEY, true),
                        r.integer(ChatConfig.ANSWER_PLANNING_USE_AT_KEY, AnswerPlanningPolicy.DEFAULT_USE_AT_PERCENT, 0,
                                100),
                        r.integer(ChatConfig.ANSWER_PLANNING_ASK_BELOW_KEY,
                                AnswerPlanningPolicy.DEFAULT_ASK_BELOW_PERCENT, 0, 100)));
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
        // wie der Chat: ohne URL kommt sie aus dem KeePass-Eintrag des Chats (VaultLocations)
        boolean fromVault = baseUrl == null && chat != null && chat.baseUrl() == null && !r.has("embedding.baseUrl");
        if ((baseUrl == null && !fromVault) || model == null || dimension == 0) {
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
     * {@code model.<kategorie>}) und das Modellverzeichnis der lokalen Stimmen ({@code models.local.modelRoot}).
     * {@code speech.readAloud.autoStart} liest neue Antworten automatisch vor (wie askai arch, Standard aus),
     * {@code speech.readAloud.chunking} und {@code speech.readAloud.synthesisWorkers} wie {@code tts.chunking} und
     * {@code tts.synthesisWorkers} in arch (Standard Absätze, 2); {@code speech.readAloud.voiceStabilityFix} begrenzt
     * lange generative TTS-Stücke standardmäßig auf ungefähr 500 Zeichen an Satzgrenzen; {@code speech.voices} die
     * Stimme je TTS-Modell ({@code speech.voice} die frühere globale).
     * {@code setup.merlin.ttsModel}/{@code setup.merlin.ttsVoice} sind Merlins eigene Stimme im Einrichtungsassistenten.
     */
    private static ModelsConfig models(ConfigReader r, List<String> warnings) {
        ModelSelections selections = ModelSelections.none();
        for (ModelCategory category : ModelCategory.values()) {
            ModelReference reference = ModelsConfig.parse(r.text(ModelsConfig.keyOf(category), null));
            selections = selections.with(category, reference);
        }
        Path modelRoot = r.path("models.local.modelRoot", AppPaths.appDirectory().resolve(DEFAULT_LOCAL_MODEL_DIRECTORY));
        return new ModelsConfig(selections, r.bool(ModelsConfig.READ_ALOUD_AUTO_START_KEY, false),
                SpeechChunking.parse(r.text(ModelsConfig.READ_ALOUD_CHUNKING_KEY, null), SpeechChunking.PARAGRAPHS),
                r.integer(ModelsConfig.READ_ALOUD_WORKERS_KEY, ReadAloudService.DEFAULT_SYNTHESIS_WORKERS, 1,
                        ReadAloudService.MAX_SYNTHESIS_WORKERS),
                r.integer(ModelsConfig.READ_ALOUD_ATTEMPTS_KEY, ReadAloudService.DEFAULT_SYNTHESIS_ATTEMPTS, 1,
                        ReadAloudService.MAX_SYNTHESIS_ATTEMPTS),
                r.bool(ModelsConfig.READ_ALOUD_VOICE_STABILITY_FIX_KEY, true),
                r.text(ModelsConfig.SPEECH_VOICE_KEY, null),
                ModelsConfig.parseSpeechVoices(r.text(ModelsConfig.SPEECH_VOICES_KEY, null)), modelRoot,
                ModelsConfig.parse(r.text(ModelsConfig.MERLIN_VOICE_KEY, ModelsConfig.defaultMerlinVoice())),
                r.text(ModelsConfig.MERLIN_SPEAKER_KEY, ModelsConfig.defaultMerlinSpeaker()));
    }

    /**
     * Farben der Sprechblasen ({@code chat.bubble.user|assistant.background|foreground}, je {@code #RRGGBB}). Ein
     * unlesbarer Wert hält den Start nicht auf: er wird als Hinweis gemeldet und die bisherige Farbe gilt.
     */
    private static BubbleColorsConfig bubbleColors(ConfigReader r, List<String> warnings) {
        return new BubbleColorsConfig(
                bubbleColor(r, BubbleColorsConfig.USER_BACKGROUND_KEY, BubbleColorsConfig.DEFAULT_USER_BACKGROUND,
                        warnings),
                bubbleColor(r, BubbleColorsConfig.USER_FOREGROUND_KEY, BubbleColorsConfig.DEFAULT_USER_FOREGROUND,
                        warnings),
                bubbleColor(r, BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY,
                        BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND, warnings),
                bubbleColor(r, BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY,
                        BubbleColorsConfig.DEFAULT_ASSISTANT_FOREGROUND, warnings));
    }

    private static int bubbleColor(ConfigReader r, String key, int def, List<String> warnings) {
        String text = r.text(key, null);
        if (text == null) {
            return def;
        }
        Integer parsed = BubbleColorsConfig.parseHex(text);
        if (parsed == null) {
            warnings.add(key + ": \"" + text + "\" ist keine Farbe im Format #RRGGBB; es gilt "
                    + BubbleColorsConfig.hex(def) + ".");
            return def;
        }
        return parsed;
    }

    private static KnowledgeConfig knowledge(ConfigReader r) {
        Path indexDirectory = r.path("knowledge.indexDirectory", AppPaths.defaultIndexDirectory());
        // Altbestand: Eine Indexierung beim Start gibt es nicht mehr; der Schlüssel wird gelesen, damit er nicht als
        // unbekannt gemeldet wird, aber weder geprüft noch verwendet.
        r.text("knowledge.indexOnStartup", null);
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
        int batch = r.integer(KNOWLEDGE_EMBEDDING_BATCH_SIZE_KEY, IndexKnowledgeUseCase.DEFAULT_EMBEDDING_BATCH_SIZE,
                1, 1000);

        RetrievalSettings retrieval;
        try {
            retrieval = RetrievalSettings.builder()
                    .keywordEnabled(r.bool("retrieval.keywordEnabled", true))
                    .semanticEnabled(r.bool("retrieval.semanticEnabled", true))
                    .keywordCandidates(r.integer(RETRIEVAL_KEYWORD_CANDIDATES_KEY, RetrievalSettings.DEFAULT_CANDIDATES,
                            1, RetrievalSettings.MAX_CANDIDATES))
                    .semanticCandidates(r.integer(RETRIEVAL_SEMANTIC_CANDIDATES_KEY, RetrievalSettings.DEFAULT_CANDIDATES,
                            1, RetrievalSettings.MAX_CANDIDATES))
                    .keywordWeight(r.decimal("retrieval.keywordWeight", 1.0, 0.0, 1000.0))
                    .semanticWeight(r.decimal("retrieval.semanticWeight", 1.0, 0.0, 1000.0))
                    .rankConstant(r.integer("retrieval.rankConstant", RetrievalSettings.DEFAULT_RANK_CONSTANT, 1, 100000))
                    .maxResults(r.integer(RETRIEVAL_MAX_RESULTS_KEY, RetrievalSettings.DEFAULT_MAX_RESULTS, 1,
                            RetrievalSettings.MAX_CANDIDATES))
                    .minSemanticScore(r.decimal("retrieval.minSemanticScore", -1.0, -1.0, 1.0))
                    .relationsEnabled(r.bool("retrieval.relations.enabled", true))
                    .relationSeeds(r.integer("retrieval.relations.seeds", RetrievalSettings.DEFAULT_RELATION_SEEDS, 1,
                            RetrievalSettings.MAX_CANDIDATES))
                    .relationNeighbors(r.integer("retrieval.relations.neighbors",
                            RetrievalSettings.DEFAULT_RELATION_NEIGHBORS, 1, RetrievalSettings.MAX_CANDIDATES))
                    .relationMinSimilarity(r.decimal("retrieval.relations.minSimilarity",
                            RetrievalSettings.DEFAULT_RELATION_MIN_SIMILARITY, -1.0, 1.0))
                    .relationWeight(r.decimal("retrieval.relations.weight", RetrievalSettings.DEFAULT_RELATION_WEIGHT,
                            0.0, 1000.0))
                    .rawWindows(r.integer(RETRIEVAL_RAW_WINDOWS_KEY, RetrievalSettings.DEFAULT_RAW_WINDOWS, 1,
                            RetrievalSettings.MAX_RAW_WINDOWS))
                    .rawWindowChars(r.integer(RETRIEVAL_RAW_WINDOW_CHARS_KEY, RetrievalSettings.DEFAULT_RAW_WINDOW_CHARS,
                            RetrievalSettings.MIN_RAW_WINDOW_CHARS, RetrievalSettings.MAX_RAW_WINDOW_CHARS))
                    .build();
        } catch (IllegalArgumentException e) {
            r.problem("retrieval.*", "Retrieval-Einstellung ungültig (siehe Beispielkonfiguration)");
            retrieval = RetrievalSettings.defaults();
        }
        ContextSettings context;
        try {
            context = ContextSettings.defaults()
                    .withMaxContextTokens(r.integer(CONTEXT_MAX_TOKENS_KEY, ContextSettings.DEFAULT_MAX_CONTEXT_TOKENS, 1,
                            1000000))
                    .withMaxSources(r.integer(CONTEXT_MAX_SOURCES_KEY, ContextSettings.DEFAULT_MAX_SOURCES, 1, 1000));
            String instruction = r.text("context.instruction", null);
            if (instruction != null) {
                context = context.withInstruction(instruction);
            }
        } catch (IllegalArgumentException e) {
            r.problem("context.*", "Kontext-Einstellung ungültig (siehe Beispielkonfiguration)");
            context = ContextSettings.defaults();
        }
        KnowledgePrefetchService.Settings prefetch = KnowledgePrefetchService.Settings.of(
                r.integer(KNOWLEDGE_PREFETCH_MAX_ITEMS_KEY, KnowledgePrefetchService.Settings.DEFAULT_MAX_ITEMS, 1,
                        KnowledgePrefetchService.Settings.MAX_ITEMS_LIMIT),
                r.integer(KNOWLEDGE_PREFETCH_CONCURRENCY_KEY, KnowledgePrefetchService.Settings.DEFAULT_CONCURRENCY, 1,
                        KnowledgePrefetchService.Settings.MAX_CONCURRENCY),
                r.integer(KNOWLEDGE_PREFETCH_CACHE_MB_KEY, KnowledgePrefetchService.Settings.DEFAULT_CACHE_MAX_MB, 1,
                        KnowledgePrefetchService.Settings.CACHE_MAX_MB_LIMIT),
                r.integer(KNOWLEDGE_RAW_WORKERS_KEY, KnowledgePrefetchService.Settings.DEFAULT_RAW_WORKERS, 1,
                        KnowledgePrefetchService.Settings.MAX_RAW_WORKERS),
                r.integer(KNOWLEDGE_ENRICHMENT_WORKERS_KEY, KnowledgePrefetchService.Settings.DEFAULT_ENRICHMENT_WORKERS,
                        1, KnowledgePrefetchService.Settings.MAX_ENRICHMENT_WORKERS),
                r.integer(EMBEDDING_MAX_CONCURRENCY_KEY,
                        KnowledgePrefetchService.Settings.DEFAULT_EMBEDDING_CONCURRENCY, 1,
                        KnowledgePrefetchService.Settings.MAX_EMBEDDING_CONCURRENCY));
        String passageLanguage = r.text(KNOWLEDGE_PASSAGE_LANGUAGE_KEY, "de");
        if (!"de".equals(passageLanguage) && !"en".equals(passageLanguage)) {
            r.problem(KNOWLEDGE_PASSAGE_LANGUAGE_KEY, "Sprache der Passagen muss de oder en sein");
            passageLanguage = "de";
        }
        EvidencePolicy evidence = EvidencePolicy.defaults()
                .withMinSources(r.integer(RETRIEVAL_MIN_INDEPENDENT_SOURCES_KEY, EvidencePolicy.DEFAULT_MIN_SOURCES, 0,
                        EvidencePolicy.MAX_MIN_SOURCES))
                .withMaxFollowUps(r.integer(RETRIEVAL_MAX_FOLLOW_UPS_KEY, FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS, 0,
                        EvidencePolicy.MAX_FOLLOW_UPS))
                .withReranking(r.bool(RETRIEVAL_RERANK_KEY, true))
                .withRerankTopK(r.integer(RETRIEVAL_RERANK_TOP_K_KEY, EvidencePolicy.DEFAULT_RERANK_TOP_K, 1,
                        EvidencePolicy.MAX_RERANK_TOP_K));
        int initialPages = r.integer(LIVE_SEARCH_INITIAL_PAGES_KEY, LiveSearchConfig.DEFAULT_INITIAL_PAGES, 0,
                LiveSearchConfig.MAX_PAGES_LIMIT);
        int maxPages = r.integer(LIVE_SEARCH_MAX_PAGES_KEY, LiveSearchConfig.DEFAULT_MAX_PAGES, 0,
                LiveSearchConfig.MAX_PAGES_LIMIT);
        if (initialPages > maxPages) {
            r.problem(LIVE_SEARCH_INITIAL_PAGES_KEY, "darf nicht größer als " + LIVE_SEARCH_MAX_PAGES_KEY + " sein");
            initialPages = maxPages;
        }
        LiveSearchConfig liveSearch = new LiveSearchConfig(initialPages, maxPages,
                r.integer(LIVE_SEARCH_HITS_PER_SOURCE_KEY, LiveSearchConfig.DEFAULT_HITS_PER_SOURCE, 1,
                        LiveSearchConfig.MAX_HITS_PER_SOURCE));
        return new KnowledgeConfig(indexDirectory, chunking, batch, retrieval, context, prefetch,
                passageLanguage, evidence, liveSearch, retry(r, SOURCES_RETRY_COUNT_KEY,
                        KnowledgeConfig.DEFAULT_SOURCE_RETRY_COUNT, SOURCES_RETRY_DELAY_KEY,
                        KnowledgeConfig.DEFAULT_SOURCE_RETRY_DELAY_MILLIS));
    }

    /**
     * Eine Wiederholungsregel aus Anzahl (0–{@link RetryPolicy#MAX_RETRIES}, 0 = nie wiederholen) und Anfangspause
     * (0–{@link RetryPolicy#MAX_INITIAL_DELAY_MILLIS} ms). Fehlen beide Schlüssel, gilt der Standard.
     */
    private static RetryPolicy retry(ConfigReader r, String countKey, int defaultCount, String delayKey,
                                     int defaultDelay) {
        int count = r.integer(countKey, defaultCount, 0, RetryPolicy.MAX_RETRIES);
        int delay = r.integer(delayKey, defaultDelay, 0, RetryPolicy.MAX_INITIAL_DELAY_MILLIS);
        return RetryPolicy.of(count, delay);
    }

    /**
     * Die Quellen der Liste {@code source.<n>.*}, typneutral: Adresse ({@code .address}), {@code type},
     * {@code enabled} und alle übrigen Schlüssel als Einstellungen des Adapters. Ob die Einstellungen stimmen, prüft
     * beim Start der Quellen-Port des Adapters ({@code KnowledgeSourceProvider}); hier werden nur Einträge ohne Typ
     * und doppelte Adressen mit einem Hinweis übersprungen; fehlt die Adresse (von Hand eingetragen), bildet der Use Case
     * sie aus Typ und Endpoint.
     */
    private static List<SourceDefinition> sources(ConfigReader r, List<String> warnings) {
        List<SourceDefinition> sources = new ArrayList<SourceDefinition>();
        Set<String> seen = new HashSet<String>();
        for (int slot : sourceSlots(r.properties())) {
            r.markRead(SOURCE_PREFIX + slot + ".");
            SourceDefinition source = sourceDefinition(r.properties(), slot);
            String label = source.address().isEmpty() ? "Nr. " + slot : source.address();
            if (source.typeId().isEmpty()) {
                warnings.add("Wissensquelle „" + label + "“ wird übersprungen, bis sie im Reiter „Wissensquellen“ "
                        + "korrigiert ist: Typ fehlt (Pflichtangabe)");
                continue;
            }
            if (!source.address().isEmpty() && !seen.add(source.address())) {
                warnings.add("Wissensquelle „" + label + "“ steht doppelt in der Liste; der zweite Eintrag wird "
                        + "übersprungen.");
                continue;
            }
            sources.add(source);
        }
        return sources;
    }

    /** Alle Quellen der Liste {@code source.<n>.*} in Listenreihenfolge, so wie sie dort stehen (auch fehlerhafte). */
    public static List<SourceDefinition> sourceDefinitions(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        List<SourceDefinition> sources = new ArrayList<SourceDefinition>();
        for (int slot : sourceSlots(properties)) {
            sources.add(sourceDefinition(properties, slot));
        }
        return sources;
    }

    /** Die belegten Listenpositionen {@code n} der Schlüssel {@code source.<n>.*}, aufsteigend. */
    public static List<Integer> sourceSlots(Properties properties) {
        Set<Integer> slots = new java.util.TreeSet<Integer>();
        for (String key : properties.stringPropertyNames()) {
            Integer slot = sourceSlotOf(key);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return new ArrayList<Integer>(slots);
    }

    /** Die Listenposition eines Schlüssels {@code source.<n>.<feld>}, sonst {@code null}. */
    public static Integer sourceSlotOf(String key) {
        java.util.regex.Matcher m = SOURCE_SLOT.matcher(key);
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /**
     * Ein Listeneintrag {@code source.<n>.*}: Adresse, Typ (klein geschrieben), Häkchen (Standard {@code true}) und
     * die übrigen Schlüssel ohne Präfix.
     */
    static SourceDefinition sourceDefinition(Properties properties, int slot) {
        String prefix = SOURCE_PREFIX + slot + ".";
        String address = "";
        String type = "";
        boolean enabled = true;
        Map<String, String> settings = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix) || key.length() == prefix.length()) {
                continue;
            }
            String field = key.substring(prefix.length());
            String value = properties.getProperty(key).trim();
            if (SOURCE_ADDRESS_FIELD.equals(field)) {
                address = value;
            } else if ("type".equals(field)) {
                type = value.toLowerCase(Locale.ROOT);
            } else if ("enabled".equals(field)) {
                enabled = !("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)
                        || "nein".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "0".equals(value));
            } else {
                settings.put(field, value);
            }
        }
        return new SourceDefinition(address, type, enabled, SourceSettings.of(settings));
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
                    .timeoutMillis(r.integer(KEEPASS_TIMEOUT_KEY, KeePassRpcConfig.DEFAULT_TIMEOUT_MILLIS,
                            1, MAX_TIMEOUT))
                    .build();
        } catch (IllegalArgumentException e) {
            r.problem("security.keepass.*", "KeePassRPC-Einstellung ungültig (siehe Beispielkonfiguration)");
            rpc = KeePassRpcConfig.defaults();
        }
        return new KeePassConfig(enabled, rpc, keyFile, retry(r, KEEPASS_RETRY_COUNT_KEY,
                KeePassConfig.DEFAULT_RETRY_COUNT, KEEPASS_RETRY_DELAY_KEY, KeePassConfig.DEFAULT_RETRY_DELAY_MILLIS),
                retry(r, KEEPASS_SETUP_RECHECK_COUNT_KEY, KeePassConfig.DEFAULT_SETUP_RECHECK_COUNT,
                        KEEPASS_SETUP_RECHECK_DELAY_KEY, KeePassConfig.DEFAULT_SETUP_RECHECK_DELAY_MILLIS));
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

    /** Wie {@link #legacyMode} als Name des neuen Modus (Einstellungen-Dialog); {@code null} für keinen alten Namen. */
    public static String legacyModeName(String name, String pacUrl) {
        ProxyMode legacy = legacyMode(name, pacUrl);
        return legacy == null ? null : legacy.name();
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
                .withMaxFailuresListed(r.integer("agent.tools.maxFailuresListed", defaults.maxFailuresListed(), 0, 10000))
                .withMaxPages(r.integer("agent.tools.maxPages", defaults.maxPages(), 0, 10000));
        // Recherche-Budget (arch: besuchte Seiten pro Lauf); bisherige Dateien mit agent.tools.maxPages behalten
        // ihren Wert, bis agent.research.maxPages gesetzt ist.
        int fallbackPages = r.has("agent.tools.maxPages")
                ? r.integer("agent.tools.maxPages", AgentConfig.DEFAULT_RESEARCH_MAX_PAGES, 0, 10000)
                : AgentConfig.DEFAULT_RESEARCH_MAX_PAGES;
        int researchMaxPages = r.integer("agent.research.maxPages", fallbackPages, 0, 10000);
        return new AgentConfig(enabled, command, args, Duration.ofSeconds(timeoutSeconds), endpointId, displayName, tools,
                researchMaxPages);
    }
}
