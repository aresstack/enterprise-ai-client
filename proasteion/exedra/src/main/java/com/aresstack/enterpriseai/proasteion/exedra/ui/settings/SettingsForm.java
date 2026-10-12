package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Inhalt des Einstellungen-Dialogs: alle Werte so, wie der Benutzer sie eingibt (Text, auch für Zahlen,
 * damit der Dialog nichts verliert und die Prüfung beim Konfigurations-Loader bleibt), dazu Schalter.
 * Enthält keine Secrets, nur Titel von KeePass-Einträgen; kennt weder Datei noch Adaptertypen. Unveränderlich;
 * Änderungen über {@link #toBuilder()}.
 *
 * <p>Nicht hier vertretene Schlüssel der Konfigurationsdatei (Timeouts, Retrieval-Feinheiten, Werkzeuggrenzen,
 * Proxy-Ausnahmen, mTLS) bleiben in der Datei unverändert erhalten.
 */
public final class SettingsForm {

    public static final String PAIRING_KEY_STORE_FILE = "file";
    public static final String PAIRING_KEY_STORE_MEMORY = "memory";
    /** Die Modi von win-proxy-java ({@code com.aresstack.winproxy.ProxyMode}), Reihenfolge wie in AskAI. */
    public static final String PROXY_DISABLED = "DISABLED";
    public static final String PROXY_MANUAL = "MANUAL_PROXY";
    public static final String PROXY_WINDOWS_STATIC = "WINDOWS_STATIC_PROXY";
    public static final String PROXY_PAC_URL_MANUAL = "PAC_URL_MANUAL";
    public static final String PROXY_PAC_URL_POWERSHELL = "PAC_URL_POWERSHELL";
    public static final String PROXY_PAC_URL_WSCRIPT = "PAC_URL_WSCRIPT";
    public static final String PROXY_PAC_URL_WINDOWS_SETTINGS = "PAC_URL_WINDOWS_SETTINGS";
    public static final String PROXY_WINDOWS_NATIVE_PROXY_SETTINGS = "WINDOWS_NATIVE_PROXY_SETTINGS";
    public static final String PROXY_WINDOWS_NATIVE_ROUTE_RESOLVER = "WINDOWS_NATIVE_ROUTE_RESOLVER";
    private static final String[] PROXY_MODES = {PROXY_DISABLED, PROXY_MANUAL, PROXY_WINDOWS_STATIC,
            PROXY_PAC_URL_MANUAL, PROXY_PAC_URL_POWERSHELL, PROXY_PAC_URL_WSCRIPT, PROXY_PAC_URL_WINDOWS_SETTINGS,
            PROXY_WINDOWS_NATIVE_PROXY_SETTINGS, PROXY_WINDOWS_NATIVE_ROUTE_RESOLVER};

    /** Die Modi der Bibliothek win-proxy-java in der Reihenfolge der Auswahlliste. */
    public static String[] proxyModes() {
        return PROXY_MODES.clone();
    }

    public static final String PROXY_AUTH_NONE = "NONE";
    public static final String PROXY_AUTH_BASIC = "BASIC";

    private final String windowTitle;
    private final String chatBaseUrl;
    private final String chatModel;
    private final String chatApiKeyRef;
    private final String chatSystemPrompt;
    private final String assistantDisplayName;
    private final String defaultAssistantDisplayName;
    private final String embeddingBaseUrl;
    private final String embeddingModel;
    private final String embeddingDimension;
    private final String embeddingApiKeyRef;
    private final String indexDirectory;
    private final String disabledTools;
    private final boolean keePassEnabled;
    private final String keePassHost;
    private final String keePassPort;
    private final String keePassClientDisplayName;
    private final String keePassPairingKeyStore;
    private final String proxyMode;
    private final String pacUrl;
    private final String pacDiscoveryScript;
    private final String proxyHost;
    private final String proxyPort;
    private final String testUrl;
    private final String resolveTimeoutMillis;
    private final String proxyAuthMode;
    private final String proxyCredentialRef;
    private final String userAgent;
    private final boolean preferIpv6;
    private final boolean tlsJvmDefault;
    private final boolean tlsWindowsRoot;
    private final boolean tlsWindowsCaStores;
    private final String caCertificatesFile;
    private final boolean agentEnabled;
    private final boolean readAloudAutoStart;
    private final String readAloudChunking;
    private final String synthesisWorkers;
    private final String bubbleUserBackground;
    private final String bubbleUserForeground;
    private final String bubbleAssistantBackground;
    private final String bubbleAssistantForeground;
    private final Map<String, String> speechVoices;
    private final String merlinVoice;
    private final String merlinSpeaker;
    private final String prefetchMaxItems;
    private final String prefetchConcurrency;
    private final String prefetchCacheMaxMb;
    private final String chatConnectTimeoutMillis;
    private final String chatReadTimeoutMillis;
    private final String chatRetryCount;
    private final String chatRetryDelayMillis;
    private final String keePassTimeoutMillis;
    private final String keePassRetryCount;
    private final String keePassRetryDelayMillis;
    private final String sourceRetryCount;
    private final String sourceRetryDelayMillis;
    private final String agentCommand;
    private final String agentArgs;
    private final String agentRequestTimeoutSeconds;
    private final String researchMaxPages;
    private final String chatToolsMaxRounds;
    private final String contextMaxSources;
    private final String retrievalMaxResults;
    private final String contextMaxTokens;
    private final String minIndependentSources;
    private final String keywordCandidates;
    private final String semanticCandidates;
    private final String liveSearchInitialPages;
    private final String liveSearchMaxPages;
    private final String liveSearchHitsPerSource;
    private final String retrievalMaxFollowUps;
    private final boolean retrievalRerank;
    private final String rawWorkers;
    private final String enrichmentWorkers;
    private final String embeddingConcurrency;
    private final String embeddingBatchSize;
    private final String rawWindows;
    private final String rawWindowChars;
    private final Map<String, String> modelSelections;
    private final String localModelRoot;

    private SettingsForm(Builder b) {
        this.windowTitle = b.windowTitle;
        this.chatBaseUrl = b.chatBaseUrl;
        this.chatModel = b.chatModel;
        this.chatApiKeyRef = b.chatApiKeyRef;
        this.chatSystemPrompt = b.chatSystemPrompt;
        this.assistantDisplayName = b.assistantDisplayName;
        this.defaultAssistantDisplayName = b.defaultAssistantDisplayName;
        this.embeddingBaseUrl = b.embeddingBaseUrl;
        this.embeddingModel = b.embeddingModel;
        this.embeddingDimension = b.embeddingDimension;
        this.embeddingApiKeyRef = b.embeddingApiKeyRef;
        this.indexDirectory = b.indexDirectory;
        this.disabledTools = b.disabledTools;
        this.keePassEnabled = b.keePassEnabled;
        this.keePassHost = b.keePassHost;
        this.keePassPort = b.keePassPort;
        this.keePassClientDisplayName = b.keePassClientDisplayName;
        this.keePassPairingKeyStore = b.keePassPairingKeyStore;
        this.proxyMode = b.proxyMode;
        this.pacUrl = b.pacUrl;
        this.pacDiscoveryScript = b.pacDiscoveryScript;
        this.proxyHost = b.proxyHost;
        this.proxyPort = b.proxyPort;
        this.testUrl = b.testUrl;
        this.resolveTimeoutMillis = b.resolveTimeoutMillis;
        this.proxyAuthMode = b.proxyAuthMode;
        this.proxyCredentialRef = b.proxyCredentialRef;
        this.userAgent = b.userAgent;
        this.preferIpv6 = b.preferIpv6;
        this.tlsJvmDefault = b.tlsJvmDefault;
        this.tlsWindowsRoot = b.tlsWindowsRoot;
        this.tlsWindowsCaStores = b.tlsWindowsCaStores;
        this.caCertificatesFile = b.caCertificatesFile;
        this.agentEnabled = b.agentEnabled;
        this.readAloudAutoStart = b.readAloudAutoStart;
        this.readAloudChunking = b.readAloudChunking;
        this.synthesisWorkers = b.synthesisWorkers;
        this.bubbleUserBackground = b.bubbleUserBackground;
        this.bubbleUserForeground = b.bubbleUserForeground;
        this.bubbleAssistantBackground = b.bubbleAssistantBackground;
        this.bubbleAssistantForeground = b.bubbleAssistantForeground;
        this.speechVoices = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.speechVoices));
        this.merlinVoice = b.merlinVoice;
        this.merlinSpeaker = b.merlinSpeaker;
        this.prefetchMaxItems = b.prefetchMaxItems;
        this.prefetchConcurrency = b.prefetchConcurrency;
        this.prefetchCacheMaxMb = b.prefetchCacheMaxMb;
        this.chatConnectTimeoutMillis = b.chatConnectTimeoutMillis;
        this.chatReadTimeoutMillis = b.chatReadTimeoutMillis;
        this.chatRetryCount = b.chatRetryCount;
        this.chatRetryDelayMillis = b.chatRetryDelayMillis;
        this.keePassTimeoutMillis = b.keePassTimeoutMillis;
        this.keePassRetryCount = b.keePassRetryCount;
        this.keePassRetryDelayMillis = b.keePassRetryDelayMillis;
        this.sourceRetryCount = b.sourceRetryCount;
        this.sourceRetryDelayMillis = b.sourceRetryDelayMillis;
        this.agentCommand = b.agentCommand;
        this.agentArgs = b.agentArgs;
        this.agentRequestTimeoutSeconds = b.agentRequestTimeoutSeconds;
        this.researchMaxPages = b.researchMaxPages;
        this.chatToolsMaxRounds = b.chatToolsMaxRounds;
        this.contextMaxSources = b.contextMaxSources;
        this.retrievalMaxResults = b.retrievalMaxResults;
        this.contextMaxTokens = b.contextMaxTokens;
        this.minIndependentSources = b.minIndependentSources;
        this.keywordCandidates = b.keywordCandidates;
        this.semanticCandidates = b.semanticCandidates;
        this.liveSearchInitialPages = b.liveSearchInitialPages;
        this.liveSearchMaxPages = b.liveSearchMaxPages;
        this.liveSearchHitsPerSource = b.liveSearchHitsPerSource;
        this.retrievalMaxFollowUps = b.retrievalMaxFollowUps;
        this.retrievalRerank = b.retrievalRerank;
        this.rawWorkers = b.rawWorkers;
        this.enrichmentWorkers = b.enrichmentWorkers;
        this.embeddingConcurrency = b.embeddingConcurrency;
        this.embeddingBatchSize = b.embeddingBatchSize;
        this.rawWindows = b.rawWindows;
        this.rawWindowChars = b.rawWindowChars;
        this.modelSelections = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.modelSelections));
        this.localModelRoot = b.localModelRoot;
    }

    /** Leeres Formular mit den Standardwerten des Loaders (Fenstertitel, KeePass an, Proxy PAC_URL_POWERSHELL, alle TLS-Quellen an, Agent aus). */
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .windowTitle(windowTitle)
                .chatBaseUrl(chatBaseUrl).chatModel(chatModel).chatApiKeyRef(chatApiKeyRef)
                .chatSystemPrompt(chatSystemPrompt)
                .assistantDisplayName(assistantDisplayName)
                .defaultAssistantDisplayName(defaultAssistantDisplayName)
                .embeddingBaseUrl(embeddingBaseUrl).embeddingModel(embeddingModel)
                .embeddingDimension(embeddingDimension).embeddingApiKeyRef(embeddingApiKeyRef)
                .indexDirectory(indexDirectory)
                .disabledTools(disabledTools)
                .keePassEnabled(keePassEnabled).keePassHost(keePassHost).keePassPort(keePassPort)
                .keePassClientDisplayName(keePassClientDisplayName).keePassPairingKeyStore(keePassPairingKeyStore)
                .proxyMode(proxyMode).pacUrl(pacUrl).pacDiscoveryScript(pacDiscoveryScript)
                .proxyHost(proxyHost).proxyPort(proxyPort).testUrl(testUrl).resolveTimeoutMillis(resolveTimeoutMillis)
                .proxyAuthMode(proxyAuthMode).proxyCredentialRef(proxyCredentialRef)
                .userAgent(userAgent).preferIpv6(preferIpv6)
                .tlsJvmDefault(tlsJvmDefault).tlsWindowsRoot(tlsWindowsRoot).tlsWindowsCaStores(tlsWindowsCaStores)
                .caCertificatesFile(caCertificatesFile)
                .agentEnabled(agentEnabled).agentCommand(agentCommand).agentArgs(agentArgs)
                .readAloudAutoStart(readAloudAutoStart)
                .readAloudChunking(readAloudChunking)
                .synthesisWorkers(synthesisWorkers)
                .bubbleUserBackground(bubbleUserBackground)
                .bubbleUserForeground(bubbleUserForeground)
                .bubbleAssistantBackground(bubbleAssistantBackground)
                .bubbleAssistantForeground(bubbleAssistantForeground)
                .speechVoices(speechVoices)
                .merlinVoice(merlinVoice)
                .merlinSpeaker(merlinSpeaker)
                .prefetchMaxItems(prefetchMaxItems)
                .prefetchConcurrency(prefetchConcurrency)
                .prefetchCacheMaxMb(prefetchCacheMaxMb)
                .chatConnectTimeoutMillis(chatConnectTimeoutMillis)
                .chatReadTimeoutMillis(chatReadTimeoutMillis)
                .chatRetryCount(chatRetryCount)
                .chatRetryDelayMillis(chatRetryDelayMillis)
                .keePassTimeoutMillis(keePassTimeoutMillis)
                .keePassRetryCount(keePassRetryCount)
                .keePassRetryDelayMillis(keePassRetryDelayMillis)
                .sourceRetryCount(sourceRetryCount)
                .sourceRetryDelayMillis(sourceRetryDelayMillis)
                .agentRequestTimeoutSeconds(agentRequestTimeoutSeconds)
                .researchMaxPages(researchMaxPages)
                .chatToolsMaxRounds(chatToolsMaxRounds)
                .contextMaxSources(contextMaxSources)
                .retrievalMaxResults(retrievalMaxResults)
                .contextMaxTokens(contextMaxTokens)
                .minIndependentSources(minIndependentSources)
                .keywordCandidates(keywordCandidates)
                .semanticCandidates(semanticCandidates)
                .liveSearchInitialPages(liveSearchInitialPages)
                .liveSearchMaxPages(liveSearchMaxPages)
                .liveSearchHitsPerSource(liveSearchHitsPerSource)
                .retrievalMaxFollowUps(retrievalMaxFollowUps)
                .retrievalRerank(retrievalRerank)
                .rawWorkers(rawWorkers)
                .enrichmentWorkers(enrichmentWorkers)
                .embeddingConcurrency(embeddingConcurrency)
                .embeddingBatchSize(embeddingBatchSize)
                .rawWindows(rawWindows)
                .rawWindowChars(rawWindowChars)
                .modelSelections(modelSelections)
                .localModelRoot(localModelRoot);
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public String windowTitle() {
        return windowTitle;
    }

    public String chatBaseUrl() {
        return chatBaseUrl;
    }

    public String chatModel() {
        return chatModel;
    }

    /** Titel des KeePass-Eintrags mit dem API-Key (optional mit Präfix {@code keepass:}). */
    public String chatApiKeyRef() {
        return chatApiKeyRef;
    }

    public String chatSystemPrompt() {
        return chatSystemPrompt;
    }

    /**
     * Name über den Antworten im Verlauf und in Exporten ({@code chat.assistant.displayName}); nur Beschriftung,
     * unabhängig von der Stimme. Leer heißt Standard.
     */
    public String assistantDisplayName() {
        return assistantDisplayName;
    }

    /** Der Standardname, den der Knopf „Standard“ einsetzt (aus der Konfiguration, nicht im Formular erfunden). */
    public String defaultAssistantDisplayName() {
        return defaultAssistantDisplayName;
    }

    /** Leer: wie Chat. */
    public String embeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public String embeddingDimension() {
        return embeddingDimension;
    }

    /** Leer: wie Chat. */
    public String embeddingApiKeyRef() {
        return embeddingApiKeyRef;
    }

    /** Leer: Standardverzeichnis unter dem Anwendungsverzeichnis. */
    public String indexDirectory() {
        return indexDirectory;
    }

    /** {@code tools.disabled}: abgeschaltete Werkzeuge, kommagetrennt; leer = alle aktiv. */
    public String disabledTools() {
        return disabledTools;
    }

    public boolean keePassEnabled() {
        return keePassEnabled;
    }

    public String keePassHost() {
        return keePassHost;
    }

    public String keePassPort() {
        return keePassPort;
    }

    public String keePassClientDisplayName() {
        return keePassClientDisplayName;
    }

    /** {@link #PAIRING_KEY_STORE_FILE} oder {@link #PAIRING_KEY_STORE_MEMORY}. */
    public String keePassPairingKeyStore() {
        return keePassPairingKeyStore;
    }

    /** Einer der {@link #proxyModes()} (Namen der Bibliothek). */
    public String proxyMode() {
        return proxyMode;
    }

    /** Bei PAC_URL_MANUAL: Adresse des PAC-/WPAD-Skripts. */
    public String pacUrl() {
        return pacUrl;
    }

    /** Bei PAC_URL_POWERSHELL/PAC_URL_WSCRIPT: Skript, das die PAC-Adresse liefert; leer = Standard der Bibliothek. */
    public String pacDiscoveryScript() {
        return pacDiscoveryScript;
    }

    public String proxyHost() {
        return proxyHost;
    }

    public String proxyPort() {
        return proxyPort;
    }

    /** Ziel für „Proxy auflösen“ und den HTTPS-Test; leer = Basis-URL + /models. */
    public String testUrl() {
        return testUrl;
    }

    /** Frist der Proxy-Auflösung in Millisekunden; leer = Standard. */
    public String resolveTimeoutMillis() {
        return resolveTimeoutMillis;
    }

    /** {@link #PROXY_AUTH_NONE} oder {@link #PROXY_AUTH_BASIC}. */
    public String proxyAuthMode() {
        return proxyAuthMode;
    }

    /** Bei BASIC: Titel des KeePass-Eintrags mit Benutzername und Passwort für den Proxy. */
    public String proxyCredentialRef() {
        return proxyCredentialRef;
    }

    /** Leer = Standard der Anwendung. */
    public String userAgent() {
        return userAgent;
    }

    public boolean preferIpv6() {
        return preferIpv6;
    }

    public boolean tlsJvmDefault() {
        return tlsJvmDefault;
    }

    public boolean tlsWindowsRoot() {
        return tlsWindowsRoot;
    }

    public boolean tlsWindowsCaStores() {
        return tlsWindowsCaStores;
    }

    /** Leer: keine zusätzliche CA-Datei; sonst Pfad einer PEM-/DER-Datei mit weiteren CA-Zertifikaten. */
    public String caCertificatesFile() {
        return caCertificatesFile;
    }

    public boolean agentEnabled() {
        return agentEnabled;
    }

    /** Neue Antworten automatisch vorlesen ({@code speech.readAloud.autoStart}). */
    public boolean readAloudAutoStart() {
        return readAloudAutoStart;
    }

    /** Einheit je Synthese ({@code speech.readAloud.chunking}): ANSWER, PARAGRAPHS oder SENTENCES. */
    public String readAloudChunking() {
        return readAloudChunking;
    }

    /** Parallele Synthesen beim Vorlesen ({@code speech.readAloud.synthesisWorkers}). */
    public String synthesisWorkers() {
        return synthesisWorkers;
    }

    /** Hintergrund der eigenen Blasen als {@code #RRGGBB} ({@code chat.bubble.user.background}). */
    public String bubbleUserBackground() {
        return bubbleUserBackground;
    }

    /** Schrift der eigenen Blasen als {@code #RRGGBB} ({@code chat.bubble.user.foreground}). */
    public String bubbleUserForeground() {
        return bubbleUserForeground;
    }

    /** Hintergrund der Antwortblasen als {@code #RRGGBB} ({@code chat.bubble.assistant.background}). */
    public String bubbleAssistantBackground() {
        return bubbleAssistantBackground;
    }

    /** Schrift der Antwortblasen als {@code #RRGGBB} ({@code chat.bubble.assistant.foreground}). */
    public String bubbleAssistantForeground() {
        return bubbleAssistantForeground;
    }

    /** Die Stimme je TTS-Modell ({@code speech.voices}): gespeicherte Modellauswahl → Stimme. */
    public Map<String, String> speechVoices() {
        return speechVoices;
    }

    /** Merlins Stimme im Einrichtungsassistenten ({@code setup.merlin.ttsModel}); leer = Vorgabe. */
    public String merlinVoice() {
        return merlinVoice;
    }

    /** Merlins fester Sprecher ({@code setup.merlin.ttsVoice}). */
    public String merlinSpeaker() {
        return merlinSpeaker;
    }

    /** Vorladen: höchstens so viele Treffer ab der Cursorposition ({@code knowledge.prefetch.maxItems}). */
    public String prefetchMaxItems() {
        return prefetchMaxItems;
    }

    /** Vorladen: parallele Abrufe im Hintergrund ({@code knowledge.prefetch.concurrency}). */
    public String prefetchConcurrency() {
        return prefetchConcurrency;
    }

    /** Vorladen: Speicher-Cache in MB ({@code knowledge.prefetch.cacheMaxMb}). */
    public String prefetchCacheMaxMb() {
        return prefetchCacheMaxMb;
    }

    /** Chat: Frist für den Verbindungsaufbau in ms ({@code chat.connectTimeoutMillis}). Leer = Standard. */
    public String chatConnectTimeoutMillis() {
        return chatConnectTimeoutMillis;
    }

    /** Chat: Frist zwischen zwei empfangenen Datenstücken in ms ({@code chat.readTimeoutMillis}). Leer = Standard. */
    public String chatReadTimeoutMillis() {
        return chatReadTimeoutMillis;
    }

    /** Chat: Wiederholungen einer Modellanfrage nach einem vorübergehenden Fehler ({@code chat.retryCount}). Leer = Standard. */
    public String chatRetryCount() {
        return chatRetryCount;
    }

    /** Chat: Pause vor der ersten Wiederholung in ms ({@code chat.retryDelayMillis}). Leer = Standard. */
    public String chatRetryDelayMillis() {
        return chatRetryDelayMillis;
    }

    /** KeePassRPC: Frist je Zugriff in ms ({@code security.keepass.timeoutMillis}). Leer = Standard. */
    public String keePassTimeoutMillis() {
        return keePassTimeoutMillis;
    }

    /** KeePassRPC: Wiederholungen, wenn KeePass nicht antwortet ({@code security.keepass.retryCount}). Leer = Standard. */
    public String keePassRetryCount() {
        return keePassRetryCount;
    }

    /** KeePassRPC: Pause vor der ersten Wiederholung in ms ({@code security.keepass.retryDelayMillis}). Leer = Standard. */
    public String keePassRetryDelayMillis() {
        return keePassRetryDelayMillis;
    }

    /** Wissensquellen: Wiederholungen von Suche und Seitenabruf ({@code knowledge.sources.retryCount}). Leer = Standard. */
    public String sourceRetryCount() {
        return sourceRetryCount;
    }

    /** Wissensquellen: Pause vor der ersten Wiederholung in ms ({@code knowledge.sources.retryDelayMillis}). Leer = Standard. */
    public String sourceRetryDelayMillis() {
        return sourceRetryDelayMillis;
    }

    public String agentCommand() {
        return agentCommand;
    }

    public String agentArgs() {
        return agentArgs;
    }

    public String agentRequestTimeoutSeconds() {
        return agentRequestTimeoutSeconds;
    }

    /** Recherche-Budget: gelesene Seiten je Recherchelauf ({@code agent.research.maxPages}, 0 = unbegrenzt). */
    public String researchMaxPages() {
        return researchMaxPages;
    }

    /** Werkzeugrunden je Antwort im normalen Chat ({@code chat.tools.maxRounds}, 1–32). */
    public String chatToolsMaxRounds() {
        return chatToolsMaxRounds;
    }

    /** Suchauszüge im RAG-Kontext einer normalen Chat-Antwort ({@code context.maxSources}). */
    public String contextMaxSources() {
        return contextMaxSources;
    }

    /** Fusionierte Suchtreffer vor dem Kontextaufbau ({@code retrieval.maxResults}). */
    public String retrievalMaxResults() {
        return retrievalMaxResults;
    }

    /** Token-Budget des RAG-Kontextblocks ({@code context.maxTokens}). */
    public String contextMaxTokens() {
        return contextMaxTokens;
    }

    /** Mindestzahl verschiedener Ressourcen ({@code retrieval.minIndependentSources}, 0 = nur Evidenzprüfung). */
    public String minIndependentSources() {
        return minIndependentSources;
    }

    /** Volltext-Kandidaten je Suche ({@code retrieval.keywordCandidates}). */
    public String keywordCandidates() {
        return keywordCandidates;
    }

    /** Semantische Kandidaten je Suche ({@code retrieval.semanticCandidates}). */
    public String semanticCandidates() {
        return semanticCandidates;
    }

    /** Live-Suche: Seiten vor der ersten Evidenzbewertung ({@code retrieval.liveSearch.initialPages}). */
    public String liveSearchInitialPages() {
        return liveSearchInitialPages;
    }

    /** Live-Suche: höchstens blockierend abgewartete Seiten ({@code retrieval.liveSearch.maxPages}). */
    public String liveSearchMaxPages() {
        return liveSearchMaxPages;
    }

    /** Live-Suche: Treffer je Quelle ({@code retrieval.liveSearch.hitsPerSource}). */
    public String liveSearchHitsPerSource() {
        return liveSearchHitsPerSource;
    }

    /** Nachsuchen mit genauerer Anfrage im normalen Chat ({@code retrieval.maxFollowUps}, 0–5). */
    public String retrievalMaxFollowUps() {
        return retrievalMaxFollowUps;
    }

    /** Treffer vor der Übernahme gegen die Frage bewerten ({@code retrieval.rerank}). */
    public boolean retrievalRerank() {
        return retrievalRerank;
    }

    /** Rohindex: Schreiber ({@code knowledge.raw.workers}). */
    public String rawWorkers() {
        return rawWorkers;
    }

    /** Aufbereitung: Dokumente gleichzeitig ({@code knowledge.enrichment.workers}). */
    public String enrichmentWorkers() {
        return enrichmentWorkers;
    }

    /** Aufbereitung: gleichzeitige Embedding-Aufrufe ({@code embedding.maxConcurrency}). */
    public String embeddingConcurrency() {
        return embeddingConcurrency;
    }

    /** Texte je Embedding-Aufruf ({@code knowledge.embeddingBatchSize}). */
    public String embeddingBatchSize() {
        return embeddingBatchSize;
    }

    /** Textfenster je geladener Seite ({@code retrieval.raw.windowsPerDocument}). */
    public String rawWindows() {
        return rawWindows;
    }

    /** Zeichen je Textfenster ({@code retrieval.raw.windowChars}). */
    public String rawWindowChars() {
        return rawWindowChars;
    }

    /**
     * Modellauswahl der Kategorien ohne eigenes Feld (Schlüssel = Kategorieschlüssel wie {@code rerank},
     * {@code tts}; Wert {@code [<katalog>:]<modell>}). Chat und Embeddings stehen in {@link #chatModel()} und
     * {@link #embeddingModel()}.
     */
    public Map<String, String> modelSelections() {
        return modelSelections;
    }

    /** @return die Auswahl der Kategorie oder leer */
    public String modelSelection(String categoryKey) {
        String value = modelSelections.get(categoryKey);
        return value == null ? "" : value;
    }

    public String localModelRoot() {
        return localModelRoot;
    }

    @Override
    public String toString() {
        return "SettingsForm[chatBaseUrl=" + chatBaseUrl + ", chatModel=" + chatModel + ", chatApiKeyRef="
                + chatApiKeyRef + ", embeddingModel=" + embeddingModel
                + ", keePassEnabled=" + keePassEnabled + ", proxyMode=" + proxyMode + ", agentEnabled=" + agentEnabled
                + "]";
    }

    public static final class Builder {
        private String windowTitle = "Enterprise AI Client";
        private String chatBaseUrl = "";
        private String chatModel = "";
        private String chatApiKeyRef = "";
        private String chatSystemPrompt = "";
        private String assistantDisplayName = "";
        private String defaultAssistantDisplayName = "";
        private String embeddingBaseUrl = "";
        private String embeddingModel = "";
        private String embeddingDimension = "";
        private String embeddingApiKeyRef = "";
        private String indexDirectory = "";
        private String disabledTools = "";
        private boolean keePassEnabled = true;
        private String keePassHost = "127.0.0.1";
        private String keePassPort = "12546";
        private String keePassClientDisplayName = "Enterprise AI Client";
        private String keePassPairingKeyStore = PAIRING_KEY_STORE_FILE;
        private String proxyMode = PROXY_PAC_URL_POWERSHELL;
        private String pacUrl = "";
        private String pacDiscoveryScript = "";
        private String proxyHost = "";
        private String proxyPort = "";
        private String testUrl = "";
        private String resolveTimeoutMillis = "";
        private String proxyAuthMode = PROXY_AUTH_NONE;
        private String proxyCredentialRef = "";
        private String userAgent = "";
        private boolean preferIpv6;
        private boolean tlsJvmDefault = true;
        private boolean tlsWindowsRoot = true;
        private boolean tlsWindowsCaStores = true;
        private String caCertificatesFile = "";
        private boolean agentEnabled;
        private boolean readAloudAutoStart;
        private String readAloudChunking = "PARAGRAPHS";
        private String synthesisWorkers = "2";
        private String bubbleUserBackground = BubbleColorsConfig.hex(BubbleColorsConfig.DEFAULT_USER_BACKGROUND);
        private String bubbleUserForeground = BubbleColorsConfig.hex(BubbleColorsConfig.DEFAULT_USER_FOREGROUND);
        private String bubbleAssistantBackground =
                BubbleColorsConfig.hex(BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND);
        private String bubbleAssistantForeground =
                BubbleColorsConfig.hex(BubbleColorsConfig.DEFAULT_ASSISTANT_FOREGROUND);
        private final Map<String, String> speechVoices = new LinkedHashMap<String, String>();
        private String merlinVoice = "";
        private String merlinSpeaker = "";
        private String prefetchMaxItems = "100";
        private String prefetchConcurrency = "4";
        private String prefetchCacheMaxMb = "50";
        private String chatConnectTimeoutMillis = "";
        private String chatReadTimeoutMillis = "";
        private String chatRetryCount = "";
        private String chatRetryDelayMillis = "";
        private String keePassTimeoutMillis = "";
        private String keePassRetryCount = "";
        private String keePassRetryDelayMillis = "";
        private String sourceRetryCount = "";
        private String sourceRetryDelayMillis = "";
        private String agentCommand = "";
        private String agentArgs = "";
        private String agentRequestTimeoutSeconds = "30";
        private String researchMaxPages = "4";
        private String chatToolsMaxRounds = "8";
        private String contextMaxSources = "6";
        private String retrievalMaxResults = "10";
        private String contextMaxTokens = "1500";
        private String minIndependentSources = "1";
        private String keywordCandidates = "20";
        private String semanticCandidates = "20";
        private String liveSearchInitialPages = "1";
        private String liveSearchMaxPages = "4";
        private String liveSearchHitsPerSource = "10";
        private String retrievalMaxFollowUps = "2";
        private boolean retrievalRerank = true;
        private String rawWorkers = "2";
        private String enrichmentWorkers = "4";
        private String embeddingConcurrency = "4";
        private String embeddingBatchSize = "16";
        private String rawWindows = "2";
        private String rawWindowChars = "700";
        private final Map<String, String> modelSelections = new LinkedHashMap<String, String>();
        private String localModelRoot = "";

        private Builder() {
        }

        public Builder windowTitle(String value) {
            this.windowTitle = text(value);
            return this;
        }

        public Builder chatBaseUrl(String value) {
            this.chatBaseUrl = text(value);
            return this;
        }

        public Builder chatModel(String value) {
            this.chatModel = text(value);
            return this;
        }

        public Builder chatApiKeyRef(String value) {
            this.chatApiKeyRef = text(value);
            return this;
        }

        public Builder chatSystemPrompt(String value) {
            this.chatSystemPrompt = text(value);
            return this;
        }

        public Builder assistantDisplayName(String value) {
            this.assistantDisplayName = text(value);
            return this;
        }

        public Builder defaultAssistantDisplayName(String value) {
            this.defaultAssistantDisplayName = text(value);
            return this;
        }

        public Builder embeddingBaseUrl(String value) {
            this.embeddingBaseUrl = text(value);
            return this;
        }

        public Builder embeddingModel(String value) {
            this.embeddingModel = text(value);
            return this;
        }

        public Builder embeddingDimension(String value) {
            this.embeddingDimension = text(value);
            return this;
        }

        public Builder embeddingApiKeyRef(String value) {
            this.embeddingApiKeyRef = text(value);
            return this;
        }

        public Builder indexDirectory(String value) {
            this.indexDirectory = text(value);
            return this;
        }

        public Builder disabledTools(String value) {
            this.disabledTools = text(value);
            return this;
        }

        public Builder keePassEnabled(boolean value) {
            this.keePassEnabled = value;
            return this;
        }

        public Builder keePassHost(String value) {
            this.keePassHost = text(value);
            return this;
        }

        public Builder keePassPort(String value) {
            this.keePassPort = text(value);
            return this;
        }

        public Builder keePassClientDisplayName(String value) {
            this.keePassClientDisplayName = text(value);
            return this;
        }

        public Builder keePassPairingKeyStore(String value) {
            this.keePassPairingKeyStore = text(value);
            return this;
        }

        public Builder proxyMode(String value) {
            this.proxyMode = text(value);
            return this;
        }

        public Builder pacUrl(String value) {
            this.pacUrl = text(value);
            return this;
        }

        /** Skripte bleiben unverändert (mehrzeilig); nur ganz leerer Text zählt als leer. */
        public Builder pacDiscoveryScript(String value) {
            this.pacDiscoveryScript = value == null || value.trim().isEmpty() ? "" : value;
            return this;
        }

        public Builder proxyHost(String value) {
            this.proxyHost = text(value);
            return this;
        }

        public Builder proxyPort(String value) {
            this.proxyPort = text(value);
            return this;
        }

        public Builder testUrl(String value) {
            this.testUrl = text(value);
            return this;
        }

        public Builder resolveTimeoutMillis(String value) {
            this.resolveTimeoutMillis = text(value);
            return this;
        }

        public Builder proxyAuthMode(String value) {
            this.proxyAuthMode = text(value);
            return this;
        }

        public Builder proxyCredentialRef(String value) {
            this.proxyCredentialRef = text(value);
            return this;
        }

        public Builder userAgent(String value) {
            this.userAgent = text(value);
            return this;
        }

        public Builder preferIpv6(boolean value) {
            this.preferIpv6 = value;
            return this;
        }

        public Builder tlsJvmDefault(boolean value) {
            this.tlsJvmDefault = value;
            return this;
        }

        public Builder tlsWindowsRoot(boolean value) {
            this.tlsWindowsRoot = value;
            return this;
        }

        public Builder tlsWindowsCaStores(boolean value) {
            this.tlsWindowsCaStores = value;
            return this;
        }

        public Builder caCertificatesFile(String value) {
            this.caCertificatesFile = text(value);
            return this;
        }

        public Builder readAloudAutoStart(boolean value) {
            this.readAloudAutoStart = value;
            return this;
        }

        public Builder readAloudChunking(String value) {
            this.readAloudChunking = value;
            return this;
        }

        /** Ersetzt die Stimmen je Modell; leere Stimmen fallen weg. */
        public Builder speechVoices(Map<String, String> values) {
            speechVoices.clear();
            if (values != null) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    String model = text(entry.getKey());
                    String voice = text(entry.getValue());
                    if (!model.isEmpty() && !voice.isEmpty()) {
                        speechVoices.put(model, voice);
                    }
                }
            }
            return this;
        }

        public Builder merlinSpeaker(String value) {
            this.merlinSpeaker = text(value);
            return this;
        }

        public Builder merlinVoice(String value) {
            this.merlinVoice = text(value);
            return this;
        }

        public Builder synthesisWorkers(String value) {
            this.synthesisWorkers = value;
            return this;
        }

        public Builder bubbleUserBackground(String value) {
            this.bubbleUserBackground = text(value);
            return this;
        }

        public Builder bubbleUserForeground(String value) {
            this.bubbleUserForeground = text(value);
            return this;
        }

        public Builder bubbleAssistantBackground(String value) {
            this.bubbleAssistantBackground = text(value);
            return this;
        }

        public Builder bubbleAssistantForeground(String value) {
            this.bubbleAssistantForeground = text(value);
            return this;
        }

        public Builder prefetchMaxItems(String value) {
            this.prefetchMaxItems = value;
            return this;
        }

        public Builder prefetchConcurrency(String value) {
            this.prefetchConcurrency = value;
            return this;
        }

        public Builder prefetchCacheMaxMb(String value) {
            this.prefetchCacheMaxMb = value;
            return this;
        }

        public Builder chatConnectTimeoutMillis(String value) {
            this.chatConnectTimeoutMillis = text(value);
            return this;
        }

        public Builder chatReadTimeoutMillis(String value) {
            this.chatReadTimeoutMillis = text(value);
            return this;
        }

        public Builder chatRetryCount(String value) {
            this.chatRetryCount = text(value);
            return this;
        }

        public Builder chatRetryDelayMillis(String value) {
            this.chatRetryDelayMillis = text(value);
            return this;
        }

        public Builder keePassTimeoutMillis(String value) {
            this.keePassTimeoutMillis = text(value);
            return this;
        }

        public Builder keePassRetryCount(String value) {
            this.keePassRetryCount = text(value);
            return this;
        }

        public Builder keePassRetryDelayMillis(String value) {
            this.keePassRetryDelayMillis = text(value);
            return this;
        }

        public Builder sourceRetryCount(String value) {
            this.sourceRetryCount = text(value);
            return this;
        }

        public Builder sourceRetryDelayMillis(String value) {
            this.sourceRetryDelayMillis = text(value);
            return this;
        }

        public Builder agentEnabled(boolean value) {
            this.agentEnabled = value;
            return this;
        }

        public Builder agentCommand(String value) {
            this.agentCommand = text(value);
            return this;
        }

        public Builder agentArgs(String value) {
            this.agentArgs = text(value);
            return this;
        }

        public Builder agentRequestTimeoutSeconds(String value) {
            this.agentRequestTimeoutSeconds = text(value);
            return this;
        }

        public Builder researchMaxPages(String value) {
            this.researchMaxPages = text(value);
            return this;
        }

        public Builder chatToolsMaxRounds(String value) {
            this.chatToolsMaxRounds = text(value);
            return this;
        }

        public Builder contextMaxSources(String value) {
            this.contextMaxSources = text(value);
            return this;
        }

        public Builder retrievalMaxResults(String value) {
            this.retrievalMaxResults = text(value);
            return this;
        }

        public Builder contextMaxTokens(String value) {
            this.contextMaxTokens = text(value);
            return this;
        }

        public Builder minIndependentSources(String value) {
            this.minIndependentSources = text(value);
            return this;
        }

        public Builder keywordCandidates(String value) {
            this.keywordCandidates = text(value);
            return this;
        }

        public Builder semanticCandidates(String value) {
            this.semanticCandidates = text(value);
            return this;
        }

        public Builder liveSearchInitialPages(String value) {
            this.liveSearchInitialPages = text(value);
            return this;
        }

        public Builder liveSearchMaxPages(String value) {
            this.liveSearchMaxPages = text(value);
            return this;
        }

        public Builder liveSearchHitsPerSource(String value) {
            this.liveSearchHitsPerSource = text(value);
            return this;
        }

        public Builder retrievalMaxFollowUps(String value) {
            this.retrievalMaxFollowUps = text(value);
            return this;
        }

        public Builder retrievalRerank(boolean value) {
            this.retrievalRerank = value;
            return this;
        }

        public Builder rawWorkers(String value) {
            this.rawWorkers = text(value);
            return this;
        }

        public Builder enrichmentWorkers(String value) {
            this.enrichmentWorkers = text(value);
            return this;
        }

        public Builder embeddingConcurrency(String value) {
            this.embeddingConcurrency = text(value);
            return this;
        }

        public Builder embeddingBatchSize(String value) {
            this.embeddingBatchSize = text(value);
            return this;
        }

        public Builder rawWindows(String value) {
            this.rawWindows = text(value);
            return this;
        }

        public Builder rawWindowChars(String value) {
            this.rawWindowChars = text(value);
            return this;
        }

        /** Ersetzt alle Kategorien ohne eigenes Feld. */
        public Builder modelSelections(Map<String, String> values) {
            modelSelections.clear();
            if (values != null) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    modelSelection(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /** Leer entfernt die Auswahl der Kategorie. */
        public Builder modelSelection(String categoryKey, String value) {
            String trimmed = text(value);
            if (trimmed.isEmpty()) {
                modelSelections.remove(categoryKey);
            } else {
                modelSelections.put(categoryKey, trimmed);
            }
            return this;
        }

        public Builder localModelRoot(String value) {
            this.localModelRoot = text(value);
            return this;
        }

        public SettingsForm build() {
            return new SettingsForm(this);
        }
    }
}
