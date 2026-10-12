package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AgentConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ChatConfig;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.KnowledgeConfig;
import com.aresstack.enterpriseai.proasteion.application.config.LiveSearchConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.ContextSettings;
import com.aresstack.enterpriseai.astu.acropolis.rag.EvidencePolicy;
import com.aresstack.enterpriseai.astu.acropolis.rag.FollowUpResearch;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalSettings;
import com.aresstack.enterpriseai.astu.acropolis.tool.ChatCompletionsToolLoop;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Übersetzt zwischen der Konfigurationsdatei ({@link Properties}) und dem {@link SettingsForm} des Dialogs.
 * Verwaltet nur die Schlüssel, die der Dialog zeigt; alle anderen Schlüssel der Datei bleiben unberührt.
 * Leere optionale Felder werden nicht als leerer Wert geschrieben, sondern entfernt (auskommentiert), damit die
 * Standardwerte des Loaders greifen und die Vorlage lesbar bleibt.
 */
public final class SettingsMapper {

    static final String KEY_WINDOW_TITLE = "ui.windowTitle";
    static final String KEY_CHAT_BASE_URL = "chat.baseUrl";
    static final String KEY_CHAT_MODEL = "chat.model";
    static final String KEY_CHAT_API_KEY_REF = "chat.apiKeyRef";
    static final String KEY_CHAT_SYSTEM_PROMPT = "chat.systemPrompt";
    static final String KEY_EMBEDDING_BASE_URL = "embedding.baseUrl";
    static final String KEY_EMBEDDING_MODEL = "embedding.model";
    static final String KEY_EMBEDDING_DIMENSION = "embedding.dimension";
    static final String KEY_EMBEDDING_API_KEY_REF = "embedding.apiKeyRef";
    static final String KEY_INDEX_DIRECTORY = "knowledge.indexDirectory";
    /** Abgeschaltete Werkzeuge (Reiter „Tools“), kommagetrennt; fehlt der Schlüssel, sind alle aktiv. */
    static final String KEY_DISABLED_TOOLS = "tools.disabled";
    static final String KEY_KEEPASS_ENABLED = "security.keepass.enabled";
    static final String KEY_KEEPASS_HOST = "security.keepass.host";
    static final String KEY_KEEPASS_PORT = "security.keepass.port";
    static final String KEY_KEEPASS_DISPLAY_NAME = "security.keepass.clientDisplayName";
    static final String KEY_KEEPASS_PAIRING_STORE = "security.keepass.pairingKeyStore";
    static final String KEY_PROXY_MODE = "network.proxy.mode";
    static final String KEY_PAC_URL = "network.proxy.pacUrl";
    static final String KEY_PAC_DISCOVERY_SCRIPT = "network.proxy.pacDiscoveryScript";
    static final String KEY_PROXY_HOST = "network.proxy.host";
    static final String KEY_PROXY_PORT = "network.proxy.port";
    static final String KEY_TEST_URL = "network.proxy.testUrl";
    static final String KEY_RESOLVE_TIMEOUT = "network.proxy.resolveTimeoutMillis";
    static final String KEY_PROXY_AUTH_MODE = "network.proxy.auth.mode";
    static final String KEY_PROXY_CREDENTIAL_REF = "network.proxy.auth.credentialRef";
    static final String KEY_USER_AGENT = "network.http.userAgent";
    static final String KEY_PREFER_IPV6 = "network.http.preferIPv6";
    static final String KEY_TLS_JVM = "network.tls.useJvmDefault";
    static final String KEY_TLS_WINDOWS_ROOT = "network.tls.useWindowsRoot";
    static final String KEY_TLS_WINDOWS_CA = "network.tls.useWindowsCaStores";
    static final String KEY_TLS_CA_FILE = "network.tls.caCertificatesFile";
    /** Schlüssel der Fassungen bis 0.1.3: nur gelesen, beim Speichern entfernt. */
    static final String KEY_LEGACY_PAC_DISCOVERY = "network.proxy.pacDiscovery";
    static final String KEY_LEGACY_TLS_WINDOWS_STORE = "network.tls.useWindowsCertificateStore";
    static final String KEY_AGENT_ENABLED = "agent.enabled";
    static final String KEY_AGENT_COMMAND = "agent.command";
    static final String KEY_AGENT_ARGS = "agent.args";
    static final String KEY_AGENT_TIMEOUT = "agent.requestTimeoutSeconds";
    static final String KEY_RESEARCH_MAX_PAGES = "agent.research.maxPages";
    /** Älterer Schlüssel des Seitenbudgets; gilt nur, solange {@link #KEY_RESEARCH_MAX_PAGES} fehlt. */
    static final String KEY_LEGACY_TOOLS_MAX_PAGES = "agent.tools.maxPages";
    static final String SOURCE_PREFIX = "source.";
    /** Schlüssel des entfernten Java-21-Sidecars (bis 0.1.42); Speichern entfernt sie aus der Datei. */
    static final String KEY_LOCAL_JAVA = "models.local.java";
    static final String KEY_LOCAL_SIDECAR_JAR = "models.local.sidecarJar";
    static final String KEY_LOCAL_READY_TIMEOUT = "models.local.readyTimeoutMillis";
    static final String KEY_LOCAL_MODEL_ROOT = "models.local.modelRoot";

    private SettingsMapper() {
    }

    /**
     * Das Formular für den allerersten Start: leere Pflichtfelder und die Standardwerte des Loaders für den Rest.
     * Einzige Ausnahme ist der KeePass-Titel {@code keepass:Enterprise AI API}: ein Vorschlag, derselbe wie in der
     * Vorlage und in der Doku, damit der Eintrag in KeePass und die Konfiguration ohne Abtippen zusammenpassen.
     */
    public static SettingsForm firstStartDefaults() {
        return SettingsForm.builder().chatApiKeyRef("keepass:Enterprise AI API")
                .assistantDisplayName(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME)
                .defaultAssistantDisplayName(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME).build();
    }

    /** Liest die vom Dialog verwalteten Schlüssel; fehlende Schlüssel erscheinen leer bzw. mit Standardwert. */
    public static SettingsForm fromProperties(Properties p) {
        if (p == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        SettingsForm.Builder b = SettingsForm.builder();
        b.windowTitle(text(p, KEY_WINDOW_TITLE, "Enterprise AI Client"));
        b.chatBaseUrl(text(p, KEY_CHAT_BASE_URL, ""));
        b.chatModel(text(p, KEY_CHAT_MODEL, ""));
        b.chatApiKeyRef(text(p, KEY_CHAT_API_KEY_REF, ""));
        b.chatSystemPrompt(text(p, KEY_CHAT_SYSTEM_PROMPT, ChatConfig.DEFAULT_SYSTEM_PROMPT));
        b.assistantDisplayName(text(p, ChatConfig.ASSISTANT_DISPLAY_NAME_KEY, ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME));
        b.defaultAssistantDisplayName(ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME);
        b.embeddingBaseUrl(text(p, KEY_EMBEDDING_BASE_URL, ""));
        b.embeddingModel(text(p, KEY_EMBEDDING_MODEL, ""));
        b.embeddingDimension(text(p, KEY_EMBEDDING_DIMENSION, ""));
        b.embeddingApiKeyRef(text(p, KEY_EMBEDDING_API_KEY_REF, ""));
        b.indexDirectory(text(p, KEY_INDEX_DIRECTORY, ""));
        b.disabledTools(text(p, KEY_DISABLED_TOOLS, ""));
        b.keePassEnabled(bool(p, KEY_KEEPASS_ENABLED, true));
        b.keePassHost(text(p, KEY_KEEPASS_HOST, "127.0.0.1"));
        b.keePassPort(text(p, KEY_KEEPASS_PORT, "12546"));
        b.keePassClientDisplayName(text(p, KEY_KEEPASS_DISPLAY_NAME, "Enterprise AI Client"));
        b.keePassPairingKeyStore(text(p, KEY_KEEPASS_PAIRING_STORE, SettingsForm.PAIRING_KEY_STORE_FILE)
                .toLowerCase(Locale.ROOT));
        String pacUrl = text(p, KEY_PAC_URL, "");
        String mode = text(p, KEY_PROXY_MODE, SettingsForm.PROXY_PAC_URL_POWERSHELL).toUpperCase(Locale.ROOT)
                .replace('-', '_');
        String legacy = AppConfigLoader.legacyModeName(mode, pacUrl.isEmpty() ? null : pacUrl);
        b.proxyMode(legacy == null ? mode : legacy);
        b.pacUrl(pacUrl);
        String script = p.getProperty(KEY_PAC_DISCOVERY_SCRIPT);
        b.pacDiscoveryScript(script == null ? "" : script);
        b.proxyHost(text(p, KEY_PROXY_HOST, ""));
        b.proxyPort(text(p, KEY_PROXY_PORT, ""));
        b.testUrl(text(p, KEY_TEST_URL, ""));
        b.resolveTimeoutMillis(text(p, KEY_RESOLVE_TIMEOUT, ""));
        b.proxyAuthMode(text(p, KEY_PROXY_AUTH_MODE, SettingsForm.PROXY_AUTH_NONE).toUpperCase(Locale.ROOT));
        b.proxyCredentialRef(text(p, KEY_PROXY_CREDENTIAL_REF, ""));
        b.userAgent(text(p, KEY_USER_AGENT, ""));
        b.preferIpv6(bool(p, KEY_PREFER_IPV6, false));
        boolean legacyWindows = bool(p, KEY_LEGACY_TLS_WINDOWS_STORE, true);
        b.tlsJvmDefault(bool(p, KEY_TLS_JVM, true));
        b.tlsWindowsRoot(bool(p, KEY_TLS_WINDOWS_ROOT, legacyWindows));
        b.tlsWindowsCaStores(bool(p, KEY_TLS_WINDOWS_CA, legacyWindows));
        b.caCertificatesFile(text(p, KEY_TLS_CA_FILE, ""));
        b.agentEnabled(bool(p, KEY_AGENT_ENABLED, false));
        b.readAloudAutoStart(bool(p, ModelsConfig.READ_ALOUD_AUTO_START_KEY, false));
        b.readAloudChunking(text(p, ModelsConfig.READ_ALOUD_CHUNKING_KEY, "PARAGRAPHS").toUpperCase(Locale.ROOT));
        b.synthesisWorkers(text(p, ModelsConfig.READ_ALOUD_WORKERS_KEY, "2"));
        b.bubbleUserBackground(bubbleColor(p, BubbleColorsConfig.USER_BACKGROUND_KEY,
                BubbleColorsConfig.DEFAULT_USER_BACKGROUND));
        b.bubbleUserForeground(bubbleColor(p, BubbleColorsConfig.USER_FOREGROUND_KEY,
                BubbleColorsConfig.DEFAULT_USER_FOREGROUND));
        b.bubbleAssistantBackground(bubbleColor(p, BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY,
                BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND));
        b.bubbleAssistantForeground(bubbleColor(p, BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY,
                BubbleColorsConfig.DEFAULT_ASSISTANT_FOREGROUND));
        b.speechVoices(ModelsConfig.parseSpeechVoices(text(p, ModelsConfig.SPEECH_VOICES_KEY, "")));
        b.prefetchMaxItems(text(p, AppConfigLoader.KNOWLEDGE_PREFETCH_MAX_ITEMS_KEY, "100"));
        b.prefetchConcurrency(text(p, AppConfigLoader.KNOWLEDGE_PREFETCH_CONCURRENCY_KEY, "4"));
        b.prefetchCacheMaxMb(text(p, AppConfigLoader.KNOWLEDGE_PREFETCH_CACHE_MB_KEY, "50"));
        // Zeitlimits und Wiederholungen: fehlt ein Schlüssel, zeigt das Formular den Standard des Loaders.
        b.chatConnectTimeoutMillis(text(p, AppConfigLoader.CHAT_CONNECT_TIMEOUT_KEY, String.valueOf(ChatConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS)));
        b.chatReadTimeoutMillis(text(p, AppConfigLoader.CHAT_READ_TIMEOUT_KEY, String.valueOf(ChatConfig.DEFAULT_READ_TIMEOUT_MILLIS)));
        b.chatRetryCount(text(p, AppConfigLoader.CHAT_RETRY_COUNT_KEY, String.valueOf(ChatConfig.DEFAULT_RETRY_COUNT)));
        b.chatRetryDelayMillis(text(p, AppConfigLoader.CHAT_RETRY_DELAY_KEY, String.valueOf(ChatConfig.DEFAULT_RETRY_DELAY_MILLIS)));
        b.keePassTimeoutMillis(text(p, AppConfigLoader.KEEPASS_TIMEOUT_KEY, String.valueOf(KeePassConfig.DEFAULT_TIMEOUT_MILLIS)));
        b.keePassRetryCount(text(p, AppConfigLoader.KEEPASS_RETRY_COUNT_KEY, String.valueOf(KeePassConfig.DEFAULT_RETRY_COUNT)));
        b.keePassRetryDelayMillis(text(p, AppConfigLoader.KEEPASS_RETRY_DELAY_KEY, String.valueOf(KeePassConfig.DEFAULT_RETRY_DELAY_MILLIS)));
        b.sourceRetryCount(text(p, AppConfigLoader.SOURCES_RETRY_COUNT_KEY, String.valueOf(KnowledgeConfig.DEFAULT_SOURCE_RETRY_COUNT)));
        b.sourceRetryDelayMillis(text(p, AppConfigLoader.SOURCES_RETRY_DELAY_KEY, String.valueOf(KnowledgeConfig.DEFAULT_SOURCE_RETRY_DELAY_MILLIS)));
        b.agentCommand(text(p, KEY_AGENT_COMMAND, ""));
        b.agentArgs(text(p, KEY_AGENT_ARGS, ""));
        b.agentRequestTimeoutSeconds(text(p, KEY_AGENT_TIMEOUT, "30"));
        b.researchMaxPages(text(p, KEY_RESEARCH_MAX_PAGES, text(p, KEY_LEGACY_TOOLS_MAX_PAGES,
                String.valueOf(AgentConfig.DEFAULT_RESEARCH_MAX_PAGES))));
        b.chatToolsMaxRounds(text(p, AppConfigLoader.CHAT_TOOLS_MAX_ROUNDS_KEY,
                String.valueOf(ChatCompletionsToolLoop.DEFAULT_MAX_ROUNDS)));
        b.contextMaxSources(text(p, AppConfigLoader.CONTEXT_MAX_SOURCES_KEY,
                String.valueOf(ContextSettings.DEFAULT_MAX_SOURCES)));
        b.retrievalMaxResults(text(p, AppConfigLoader.RETRIEVAL_MAX_RESULTS_KEY,
                String.valueOf(RetrievalSettings.DEFAULT_MAX_RESULTS)));
        b.contextMaxTokens(text(p, AppConfigLoader.CONTEXT_MAX_TOKENS_KEY,
                String.valueOf(ContextSettings.DEFAULT_MAX_CONTEXT_TOKENS)));
        b.minIndependentSources(text(p, AppConfigLoader.RETRIEVAL_MIN_INDEPENDENT_SOURCES_KEY,
                String.valueOf(EvidencePolicy.DEFAULT_MIN_SOURCES)));
        b.keywordCandidates(text(p, AppConfigLoader.RETRIEVAL_KEYWORD_CANDIDATES_KEY,
                String.valueOf(RetrievalSettings.DEFAULT_CANDIDATES)));
        b.semanticCandidates(text(p, AppConfigLoader.RETRIEVAL_SEMANTIC_CANDIDATES_KEY,
                String.valueOf(RetrievalSettings.DEFAULT_CANDIDATES)));
        b.liveSearchInitialPages(text(p, AppConfigLoader.LIVE_SEARCH_INITIAL_PAGES_KEY,
                String.valueOf(LiveSearchConfig.DEFAULT_INITIAL_PAGES)));
        b.liveSearchMaxPages(text(p, AppConfigLoader.LIVE_SEARCH_MAX_PAGES_KEY,
                String.valueOf(LiveSearchConfig.DEFAULT_MAX_PAGES)));
        b.liveSearchHitsPerSource(text(p, AppConfigLoader.LIVE_SEARCH_HITS_PER_SOURCE_KEY,
                String.valueOf(LiveSearchConfig.DEFAULT_HITS_PER_SOURCE)));
        b.retrievalMaxFollowUps(text(p, AppConfigLoader.RETRIEVAL_MAX_FOLLOW_UPS_KEY,
                String.valueOf(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS)));
        b.retrievalRerank(bool(p, AppConfigLoader.RETRIEVAL_RERANK_KEY, true));
        b.rawWorkers(text(p, AppConfigLoader.KNOWLEDGE_RAW_WORKERS_KEY, String.valueOf(KnowledgePrefetchService.Settings.DEFAULT_RAW_WORKERS)));
        b.enrichmentWorkers(text(p, AppConfigLoader.KNOWLEDGE_ENRICHMENT_WORKERS_KEY, String.valueOf(KnowledgePrefetchService.Settings.DEFAULT_ENRICHMENT_WORKERS)));
        b.embeddingConcurrency(text(p, AppConfigLoader.EMBEDDING_MAX_CONCURRENCY_KEY, String.valueOf(KnowledgePrefetchService.Settings.DEFAULT_EMBEDDING_CONCURRENCY)));
        b.embeddingBatchSize(text(p, AppConfigLoader.KNOWLEDGE_EMBEDDING_BATCH_SIZE_KEY, String.valueOf(IndexKnowledgeUseCase.DEFAULT_EMBEDDING_BATCH_SIZE)));
        b.rawWindows(text(p, AppConfigLoader.RETRIEVAL_RAW_WINDOWS_KEY, String.valueOf(RetrievalSettings.DEFAULT_RAW_WINDOWS)));
        b.rawWindowChars(text(p, AppConfigLoader.RETRIEVAL_RAW_WINDOW_CHARS_KEY, String.valueOf(RetrievalSettings.DEFAULT_RAW_WINDOW_CHARS)));
        for (ModelCategory category : ModelCategory.values()) {
            if (category != ModelCategory.CHAT && category != ModelCategory.EMBEDDING) {
                b.modelSelection(category.key(), text(p, ModelsConfig.keyOf(category), ""));
            }
        }
        b.localModelRoot(text(p, KEY_LOCAL_MODEL_ROOT, ""));
        return b.build();
    }

    /** Die Schlüssel, die das Formular setzt (in Dateireihenfolge der Vorlage). */
    public static Map<String, String> changes(SettingsForm form) {
        Map<String, String> set = new LinkedHashMap<String, String>();
        put(set, KEY_WINDOW_TITLE, form.windowTitle());
        put(set, KEY_CHAT_BASE_URL, form.chatBaseUrl());
        put(set, KEY_CHAT_MODEL, form.chatModel());
        put(set, KEY_CHAT_API_KEY_REF, form.chatApiKeyRef());
        put(set, KEY_CHAT_SYSTEM_PROMPT, form.chatSystemPrompt());
        // Der Standardname steht nicht in der Datei; nur ein eigener Name wird gespeichert.
        put(set, ChatConfig.ASSISTANT_DISPLAY_NAME_KEY,
                ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME.equals(form.assistantDisplayName().trim()) ? ""
                        : form.assistantDisplayName());
        put(set, KEY_EMBEDDING_BASE_URL, form.embeddingBaseUrl());
        put(set, KEY_EMBEDDING_MODEL, form.embeddingModel());
        put(set, KEY_EMBEDDING_DIMENSION, form.embeddingDimension());
        put(set, KEY_EMBEDDING_API_KEY_REF, form.embeddingApiKeyRef());
        put(set, KEY_INDEX_DIRECTORY, form.indexDirectory());
        put(set, KEY_DISABLED_TOOLS, form.disabledTools());
        set.put(KEY_KEEPASS_ENABLED, String.valueOf(form.keePassEnabled()));
        put(set, KEY_KEEPASS_HOST, form.keePassHost());
        put(set, KEY_KEEPASS_PORT, form.keePassPort());
        put(set, KEY_KEEPASS_DISPLAY_NAME, form.keePassClientDisplayName());
        put(set, KEY_KEEPASS_PAIRING_STORE, form.keePassPairingKeyStore());
        put(set, KEY_PROXY_MODE, form.proxyMode());
        put(set, KEY_PAC_URL, form.pacUrl());
        // Skript unverändert (mehrzeilig); ConfigurationFile schreibt Zeilenumbrüche als \n.
        set.put(KEY_PAC_DISCOVERY_SCRIPT, form.pacDiscoveryScript() == null || form.pacDiscoveryScript().trim().isEmpty()
                ? "" : form.pacDiscoveryScript());
        put(set, KEY_PROXY_HOST, form.proxyHost());
        put(set, KEY_PROXY_PORT, form.proxyPort());
        put(set, KEY_TEST_URL, form.testUrl());
        put(set, KEY_RESOLVE_TIMEOUT, form.resolveTimeoutMillis());
        put(set, KEY_PROXY_AUTH_MODE, form.proxyAuthMode());
        put(set, KEY_PROXY_CREDENTIAL_REF, form.proxyCredentialRef());
        put(set, KEY_USER_AGENT, form.userAgent());
        set.put(KEY_PREFER_IPV6, String.valueOf(form.preferIpv6()));
        set.put(KEY_TLS_JVM, String.valueOf(form.tlsJvmDefault()));
        set.put(KEY_TLS_WINDOWS_ROOT, String.valueOf(form.tlsWindowsRoot()));
        set.put(KEY_TLS_WINDOWS_CA, String.valueOf(form.tlsWindowsCaStores()));
        put(set, KEY_TLS_CA_FILE, form.caCertificatesFile());
        set.put(KEY_AGENT_ENABLED, String.valueOf(form.agentEnabled()));
        // Nur „an“ steht in der Datei; aus heißt Standard (Schlüssel entfernt).
        put(set, ModelsConfig.READ_ALOUD_AUTO_START_KEY, form.readAloudAutoStart() ? "true" : "");
        // Standardwerte (Absätze, 2 Worker) stehen nicht in der Datei.
        put(set, ModelsConfig.READ_ALOUD_CHUNKING_KEY,
                "PARAGRAPHS".equals(form.readAloudChunking()) ? "" : form.readAloudChunking());
        put(set, ModelsConfig.READ_ALOUD_WORKERS_KEY,
                "2".equals(form.synthesisWorkers()) ? "" : form.synthesisWorkers());
        // Blasenfarben: die Standardfarben stehen nicht in der Datei („Standard wiederherstellen“ entfernt die Schlüssel).
        put(set, BubbleColorsConfig.USER_BACKGROUND_KEY,
                bubbleColorValue(form.bubbleUserBackground(), BubbleColorsConfig.DEFAULT_USER_BACKGROUND));
        put(set, BubbleColorsConfig.USER_FOREGROUND_KEY,
                bubbleColorValue(form.bubbleUserForeground(), BubbleColorsConfig.DEFAULT_USER_FOREGROUND));
        put(set, BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY,
                bubbleColorValue(form.bubbleAssistantBackground(), BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND));
        put(set, BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY,
                bubbleColorValue(form.bubbleAssistantForeground(), BubbleColorsConfig.DEFAULT_ASSISTANT_FOREGROUND));
        // Stimme je TTS-Modell; ohne Eintrag gilt der Standard des Modells (steht nicht in der Datei).
        put(set, ModelsConfig.SPEECH_VOICES_KEY, ModelsConfig.formatSpeechVoices(form.speechVoices()));
        // Vorladen: Standardwerte (100 Treffer, 4 Abrufe, 50 MB) stehen nicht in der Datei.
        put(set, AppConfigLoader.KNOWLEDGE_PREFETCH_MAX_ITEMS_KEY, "100".equals(form.prefetchMaxItems().trim()) ? "" : form.prefetchMaxItems().trim());
        put(set, AppConfigLoader.KNOWLEDGE_PREFETCH_CONCURRENCY_KEY, "4".equals(form.prefetchConcurrency().trim()) ? "" : form.prefetchConcurrency().trim());
        put(set, AppConfigLoader.KNOWLEDGE_PREFETCH_CACHE_MB_KEY, "50".equals(form.prefetchCacheMaxMb().trim()) ? "" : form.prefetchCacheMaxMb().trim());
        // Zeitlimits stehen wie in der Vorlage in der Datei; Wiederholungen nur, wenn sie vom Standard abweichen (ältere
        // Dateien funktionieren ohne die neuen Schlüssel).
        put(set, AppConfigLoader.CHAT_CONNECT_TIMEOUT_KEY, form.chatConnectTimeoutMillis());
        put(set, AppConfigLoader.CHAT_READ_TIMEOUT_KEY, form.chatReadTimeoutMillis());
        putUnlessDefault(set, AppConfigLoader.CHAT_RETRY_COUNT_KEY, form.chatRetryCount(),
                ChatConfig.DEFAULT_RETRY_COUNT);
        putUnlessDefault(set, AppConfigLoader.CHAT_RETRY_DELAY_KEY, form.chatRetryDelayMillis(),
                ChatConfig.DEFAULT_RETRY_DELAY_MILLIS);
        put(set, AppConfigLoader.KEEPASS_TIMEOUT_KEY, form.keePassTimeoutMillis());
        putUnlessDefault(set, AppConfigLoader.KEEPASS_RETRY_COUNT_KEY, form.keePassRetryCount(),
                KeePassConfig.DEFAULT_RETRY_COUNT);
        putUnlessDefault(set, AppConfigLoader.KEEPASS_RETRY_DELAY_KEY, form.keePassRetryDelayMillis(),
                KeePassConfig.DEFAULT_RETRY_DELAY_MILLIS);
        putUnlessDefault(set, AppConfigLoader.SOURCES_RETRY_COUNT_KEY, form.sourceRetryCount(),
                KnowledgeConfig.DEFAULT_SOURCE_RETRY_COUNT);
        putUnlessDefault(set, AppConfigLoader.SOURCES_RETRY_DELAY_KEY, form.sourceRetryDelayMillis(),
                KnowledgeConfig.DEFAULT_SOURCE_RETRY_DELAY_MILLIS);
        put(set, KEY_AGENT_COMMAND, form.agentCommand());
        put(set, KEY_AGENT_ARGS, form.agentArgs());
        put(set, KEY_AGENT_TIMEOUT, form.agentRequestTimeoutSeconds());
        // Recherche-Budget: der Standard 4 steht nicht in der Datei; das ältere agent.tools.maxPages hat das Formular
        // beim Laden übernommen und verschwindet beim Speichern (removals), damit es den Wert nie überdeckt.
        put(set, KEY_RESEARCH_MAX_PAGES, String.valueOf(AgentConfig.DEFAULT_RESEARCH_MAX_PAGES)
                .equals(form.researchMaxPages().trim()) ? "" : form.researchMaxPages().trim());
        // Werkzeugrunden und RAG-Limits des normalen Chats: der Wert bleibt in der Datei, wie er gespeichert wurde
        // (die Vorlage nennt retrieval.maxResults und context.* ausdrücklich); leer heißt Standard.
        put(set, AppConfigLoader.CHAT_TOOLS_MAX_ROUNDS_KEY, form.chatToolsMaxRounds());
        put(set, AppConfigLoader.RETRIEVAL_MAX_RESULTS_KEY, form.retrievalMaxResults());
        put(set, AppConfigLoader.CONTEXT_MAX_TOKENS_KEY, form.contextMaxTokens());
        put(set, AppConfigLoader.CONTEXT_MAX_SOURCES_KEY, form.contextMaxSources());
        put(set, AppConfigLoader.RETRIEVAL_MIN_INDEPENDENT_SOURCES_KEY, form.minIndependentSources());
        put(set, AppConfigLoader.RETRIEVAL_KEYWORD_CANDIDATES_KEY, form.keywordCandidates());
        put(set, AppConfigLoader.RETRIEVAL_SEMANTIC_CANDIDATES_KEY, form.semanticCandidates());
        put(set, AppConfigLoader.LIVE_SEARCH_INITIAL_PAGES_KEY, form.liveSearchInitialPages());
        put(set, AppConfigLoader.LIVE_SEARCH_MAX_PAGES_KEY, form.liveSearchMaxPages());
        put(set, AppConfigLoader.LIVE_SEARCH_HITS_PER_SOURCE_KEY, form.liveSearchHitsPerSource());
        put(set, AppConfigLoader.RETRIEVAL_MAX_FOLLOW_UPS_KEY, form.retrievalMaxFollowUps());
        put(set, AppConfigLoader.RETRIEVAL_RERANK_KEY, form.retrievalRerank() ? "" : "false");
        put(set, AppConfigLoader.KNOWLEDGE_RAW_WORKERS_KEY, form.rawWorkers());
        put(set, AppConfigLoader.KNOWLEDGE_ENRICHMENT_WORKERS_KEY, form.enrichmentWorkers());
        put(set, AppConfigLoader.EMBEDDING_MAX_CONCURRENCY_KEY, form.embeddingConcurrency());
        put(set, AppConfigLoader.KNOWLEDGE_EMBEDDING_BATCH_SIZE_KEY, form.embeddingBatchSize());
        put(set, AppConfigLoader.RETRIEVAL_RAW_WINDOWS_KEY, form.rawWindows());
        put(set, AppConfigLoader.RETRIEVAL_RAW_WINDOW_CHARS_KEY, form.rawWindowChars());
        for (ModelCategory category : ModelCategory.values()) {
            if (category != ModelCategory.CHAT && category != ModelCategory.EMBEDDING) {
                put(set, ModelsConfig.keyOf(category), form.modelSelection(category.key()));
            }
        }
        // Der Sidecar ist entfernt: seine Schlüssel verschwinden beim Speichern (leer = entfernen).
        set.put(KEY_LOCAL_JAVA, "");
        set.put(KEY_LOCAL_SIDECAR_JAR, "");
        set.put(KEY_LOCAL_READY_TIMEOUT, "");
        put(set, KEY_LOCAL_MODEL_ROOT, form.localModelRoot());
        return set;
    }

    /** Die Schlüssel, die der Dialog wirklich schreibt: {@link #changes} ohne leere Werte (die sind Entfernungen). */
    public static Map<String, String> writes(SettingsForm form) {
        Map<String, String> writes = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : changes(form).entrySet()) {
            if (!entry.getValue().isEmpty()) {
                writes.put(entry.getKey(), entry.getValue());
            }
        }
        return writes;
    }

    /**
     * Die Schlüssel, die entfernt (auskommentiert) werden: verwaltete Schlüssel mit leerem Wert sowie alle
     * {@code source.<n>.*} der Datei, deren Quelle das Formular nicht mehr kennt oder deren Typ sich geändert hat.
     * Quell-IDs dürfen Punkte enthalten; ein Schlüssel wird deshalb der längsten bekannten ID (Formular oder
     * {@code sources} der Datei) zugeordnet. Schlüssel zu Quellen, die nirgends gelistet sind, bleiben unberührt.
     */
    public static Set<String> removals(SettingsForm form, Properties current) {
        Set<String> remove = new LinkedHashSet<String>();
        Map<String, String> changes = changes(form);
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                remove.add(entry.getKey());
            }
        }
        remove.add(KEY_LEGACY_PAC_DISCOVERY);
        remove.add(KEY_LEGACY_TLS_WINDOWS_STORE);
        remove.add(KEY_LEGACY_TOOLS_MAX_PAGES);
        remove.removeAll(nonEmptyKeys(changes));
        return remove;
    }

    /** Die Datei nach dem Speichern, als Properties (für die Prüfung mit dem Loader). */
    public static Properties merge(Properties current, SettingsForm form) {
        Properties merged = new Properties();
        if (current != null) {
            for (String key : current.stringPropertyNames()) {
                merged.setProperty(key, current.getProperty(key));
            }
        }
        for (String key : removals(form, current)) {
            merged.remove(key);
        }
        for (Map.Entry<String, String> entry : changes(form).entrySet()) {
            if (entry.getValue().isEmpty()) {
                merged.remove(entry.getKey());
            } else {
                merged.setProperty(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /** Verständlicher Feldname zu einem Schlüssel des Loaders, z. B. für Problemmeldungen. */
    public static String labelFor(String key) {
        if (key == null) {
            return "";
        }
        Integer slot = AppConfigLoader.sourceSlotOf(key); // Listenposition, nicht die Identität
        if (slot != null) {
            String field = key.substring(SOURCE_PREFIX.length() + String.valueOf(slot).length() + 1);
            String label = "apiUrl".equals(field) ? "API-URL" : "baseUrl".equals(field) ? "Basis-URL"
                    : "directory".equals(field) ? "Verzeichnis"
                    : "startPoints".equals(field) ? "Startpunkte" : "credentialRef".equals(field) ? "KeePass-Eintrag"
                    : "maxDepth".equals(field) ? "Tiefe" : "maxResources".equals(field) ? "Höchstzahl Seiten"
                    : "type".equals(field) ? "Typ" : "enabled".equals(field) ? "Aktiv" : field;
            return "Quelle Nr. " + slot + ", " + label;
        }
        switch (key) {
            case KEY_WINDOW_TITLE:
                return "Fenstertitel";
            case KEY_CHAT_BASE_URL:
                return "Basis-URL des KI-Dienstes";
            case KEY_CHAT_MODEL:
                return "Chat-Modell";
            case KEY_CHAT_API_KEY_REF:
                return "KeePass-Eintrag mit dem API-Key";
            case KEY_CHAT_SYSTEM_PROMPT:
                return "System-Prompt";
            case KEY_EMBEDDING_BASE_URL:
                return "Basis-URL für Embeddings";
            case KEY_EMBEDDING_MODEL:
                return "Embedding-Modell";
            case KEY_EMBEDDING_DIMENSION:
                return "Embedding-Dimension";
            case KEY_EMBEDDING_API_KEY_REF:
                return "KeePass-Eintrag für Embeddings";
            case KEY_INDEX_DIRECTORY:
                return "Indexverzeichnis";
            case KEY_DISABLED_TOOLS:
                return "Abgeschaltete Werkzeuge";
            case KEY_KEEPASS_HOST:
                return "KeePassRPC-Host";
            case KEY_KEEPASS_PORT:
                return "KeePassRPC-Port";
            case KEY_KEEPASS_PAIRING_STORE:
                return "Ablage des Pairing-Schlüssels";
            case KEY_PROXY_MODE:
                return "Mode";
            case KEY_PAC_URL:
                return "PAC URL";
            case KEY_PAC_DISCOVERY_SCRIPT:
                return "PAC URL discovery script";
            case KEY_PROXY_HOST:
                return "Manual host";
            case KEY_PROXY_PORT:
                return "Manual port";
            case KEY_TEST_URL:
                return "Test URL";
            case KEY_RESOLVE_TIMEOUT:
                return "Resolve timeout (ms)";
            case KEY_PROXY_AUTH_MODE:
                return "Proxy auth mode";
            case KEY_PROXY_CREDENTIAL_REF:
                return "Proxy credentials (KeePass entry)";
            case KEY_USER_AGENT:
                return "User-Agent";
            case KEY_TLS_CA_FILE:
                return "CA-Datei";
            case KEY_AGENT_COMMAND:
                return "Agent-Kommando";
            case KEY_AGENT_TIMEOUT:
                return "Agent-Timeout";
            case KEY_RESEARCH_MAX_PAGES:
                return "Recherche-Budget (Seiten)";
            case AppConfigLoader.CHAT_TOOLS_MAX_ROUNDS_KEY:
                return "Max. Werkzeugrunden";
            case AppConfigLoader.CHAT_CONNECT_TIMEOUT_KEY:
                return "Chat: Verbindungs-Timeout (ms)";
            case AppConfigLoader.CHAT_READ_TIMEOUT_KEY:
                return "Chat: Lese-Timeout (ms)";
            case AppConfigLoader.CHAT_RETRY_COUNT_KEY:
                return "Chat: Wiederholungen";
            case AppConfigLoader.CHAT_RETRY_DELAY_KEY:
                return "Chat: Pause vor Wiederholung (ms)";
            case AppConfigLoader.KEEPASS_TIMEOUT_KEY:
                return "KeePassRPC: Timeout (ms)";
            case AppConfigLoader.KEEPASS_RETRY_COUNT_KEY:
                return "KeePassRPC: Wiederholungen";
            case AppConfigLoader.KEEPASS_RETRY_DELAY_KEY:
                return "KeePassRPC: Pause vor Wiederholung (ms)";
            case AppConfigLoader.SOURCES_RETRY_COUNT_KEY:
                return "Wissensquellen: Wiederholungen";
            case AppConfigLoader.SOURCES_RETRY_DELAY_KEY:
                return "Wissensquellen: Pause vor Wiederholung (ms)";
            case AppConfigLoader.CONTEXT_MAX_SOURCES_KEY:
                return "Max. Quellen im Antwortkontext";
            case AppConfigLoader.RETRIEVAL_MAX_RESULTS_KEY:
                return "Max. Retrieval-Treffer";
            case AppConfigLoader.CONTEXT_MAX_TOKENS_KEY:
                return "Kontextbudget (Tokens)";
            case AppConfigLoader.RETRIEVAL_MIN_INDEPENDENT_SOURCES_KEY:
                return "Mindestzahl unabhängiger Quellen";
            case AppConfigLoader.RETRIEVAL_MAX_FOLLOW_UPS_KEY:
                return "Max. Nachrecherchen";
            case AppConfigLoader.RETRIEVAL_KEYWORD_CANDIDATES_KEY:
                return "Volltext-Kandidaten";
            case AppConfigLoader.RETRIEVAL_SEMANTIC_CANDIDATES_KEY:
                return "Semantische Kandidaten";
            case AppConfigLoader.LIVE_SEARCH_INITIAL_PAGES_KEY:
                return "Live-Suche: erste Seiten";
            case AppConfigLoader.LIVE_SEARCH_MAX_PAGES_KEY:
                return "Live-Suche: max. abzuwartende Seiten";
            case AppConfigLoader.LIVE_SEARCH_HITS_PER_SOURCE_KEY:
                return "Live-Suche: Treffer je Quelle";
            case AppConfigLoader.KNOWLEDGE_RAW_WORKERS_KEY:
                return "Rohindex-Worker";
            case AppConfigLoader.KNOWLEDGE_ENRICHMENT_WORKERS_KEY:
                return "Aufbereitungs-Worker";
            case AppConfigLoader.EMBEDDING_MAX_CONCURRENCY_KEY:
                return "Embedding-Parallelität";
            case AppConfigLoader.KNOWLEDGE_EMBEDDING_BATCH_SIZE_KEY:
                return "Embedding-Batchgröße";
            case AppConfigLoader.RETRIEVAL_RAW_WINDOWS_KEY:
                return "Textfenster je Seite";
            case AppConfigLoader.RETRIEVAL_RAW_WINDOW_CHARS_KEY:
                return "Zeichen je Textfenster";
            default:
                return key;
        }
    }

    /** Problemzeile des Loaders ("schlüssel: Erwartung") mit Feldname davor. */
    public static String describe(String problem) {
        if (problem == null) {
            return "";
        }
        int colon = problem.indexOf(": ");
        if (colon <= 0) {
            return problem;
        }
        String key = problem.substring(0, colon);
        String label = labelFor(key);
        if (label.equals(key)) {
            return problem;
        }
        return label + " (" + key + "): " + problem.substring(colon + 2);
    }

    public static List<String> describe(List<String> problems) {
        List<String> described = new ArrayList<String>();
        if (problems != null) {
            for (String problem : problems) {
                described.add(describe(problem));
            }
        }
        return described;
    }

    /**
     * Problemzeile des Loaders als Klartext ohne Properties-Schlüssel, für den Einrichtungsassistenten: der Schlüssel
     * vorn wird zum Feldnamen, Schlüssel im Text ({@code Pflicht bei network.proxy.mode=MANUAL_PROXY}) ebenso. Ein
     * Sammelschlüssel ({@code chat.*}) fällt weg, weil seine Meldung schon sagt, worum es geht. Zeilen ohne
     * Schlüssel vorn bleiben, wie sie sind.
     */
    public static String plain(String problem) {
        if (problem == null) {
            return "";
        }
        int colon = problem.indexOf(": ");
        if (colon <= 0 || !KEY.matcher(problem.substring(0, colon)).matches()) {
            return plainKeys(problem);
        }
        String key = problem.substring(0, colon);
        String expectation = plainKeys(problem.substring(colon + 2));
        if (key.endsWith("*")) {
            return expectation;
        }
        String label = labelFor(key);
        return (label.equals(key) ? "Einstellung" : label) + ": " + expectation;
    }

    public static List<String> plain(List<String> problems) {
        List<String> plain = new ArrayList<String>();
        if (problems != null) {
            for (String problem : problems) {
                plain.add(plain(problem));
            }
        }
        return plain;
    }

    /** Ein Properties-Schlüssel wie {@code chat.baseUrl}, {@code source.2.apiUrl} oder {@code retrieval.*}. */
    private static final Pattern KEY =
            Pattern.compile("[a-z][A-Za-z0-9]*(?:\\.(?:[A-Za-z0-9]+|\\*))+");

    /** Ersetzt bekannte Schlüssel im Text durch ihren Feldnamen; unbekannte bleiben stehen. */
    private static String plainKeys(String text) {
        Matcher matcher = KEY.matcher(text.replace("SecretRef verwenden", "KeePass-Eintrag verwenden"));
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group();
            String label = labelFor(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(label));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    // ------------------------------------------------------------------ Helfer

    private static void put(Map<String, String> set, String key, String value) {
        set.put(key, value == null ? "" : value.trim());
    }

    /** Wie {@link #put}, aber der Standardwert wird entfernt statt geschrieben. */
    private static void putUnlessDefault(Map<String, String> set, String key, String value, int defaultValue) {
        String trimmed = value == null ? "" : value.trim();
        set.put(key, String.valueOf(defaultValue).equals(trimmed) ? "" : trimmed);
    }

    private static Set<String> nonEmptyKeys(Map<String, String> changes) {
        Set<String> keys = new LinkedHashSet<String>();
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    static String text(Properties p, String key, String def) {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        return value.trim();
    }

    static boolean bool(Properties p, String key, boolean def) {
        String value = text(p, key, null);
        if (value == null) {
            return def;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "yes".equals(lower) || "ja".equals(lower) || "on".equals(lower) || "1".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "nein".equals(lower) || "off".equals(lower) || "0".equals(lower)) {
            return false;
        }
        return def;
    }

    static List<String> list(String value) {
        List<String> items = new ArrayList<String>();
        if (value == null) {
            return items;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * Eine Blasenfarbe der Datei als {@code #RRGGBB}; fehlt sie, die Vorgabe. Ein unlesbarer Wert bleibt als Text
     * stehen, damit der Dialog ihn als ungültig zeigt statt ihn still zu ersetzen.
     */
    private static String bubbleColor(Properties p, String key, int def) {
        String value = text(p, key, "");
        if (value.isEmpty()) {
            return BubbleColorsConfig.hex(def);
        }
        Integer parsed = BubbleColorsConfig.parseHex(value);
        return parsed == null ? value : BubbleColorsConfig.hex(parsed);
    }

    /** Die Standardfarbe und unlesbare Werte schreiben nichts (der Schlüssel entfällt, die Vorgabe gilt). */
    static String bubbleColorValue(String value, int def) {
        Integer parsed = BubbleColorsConfig.parseHex(value);
        return parsed == null || parsed == def ? "" : BubbleColorsConfig.hex(parsed);
    }
}
