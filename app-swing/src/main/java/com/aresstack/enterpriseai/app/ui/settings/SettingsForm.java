package com.aresstack.enterpriseai.app.ui.settings;

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
    private final String embeddingBaseUrl;
    private final String embeddingModel;
    private final String embeddingDimension;
    private final String embeddingApiKeyRef;
    private final String indexDirectory;
    private final boolean indexOnStartup;
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
    private final String agentCommand;
    private final String agentArgs;
    private final String agentRequestTimeoutSeconds;
    private final Map<String, String> modelSelections;
    private final String localJava;
    private final String localSidecarJar;
    private final String localModelRoot;

    private SettingsForm(Builder b) {
        this.windowTitle = b.windowTitle;
        this.chatBaseUrl = b.chatBaseUrl;
        this.chatModel = b.chatModel;
        this.chatApiKeyRef = b.chatApiKeyRef;
        this.chatSystemPrompt = b.chatSystemPrompt;
        this.embeddingBaseUrl = b.embeddingBaseUrl;
        this.embeddingModel = b.embeddingModel;
        this.embeddingDimension = b.embeddingDimension;
        this.embeddingApiKeyRef = b.embeddingApiKeyRef;
        this.indexDirectory = b.indexDirectory;
        this.indexOnStartup = b.indexOnStartup;
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
        this.agentCommand = b.agentCommand;
        this.agentArgs = b.agentArgs;
        this.agentRequestTimeoutSeconds = b.agentRequestTimeoutSeconds;
        this.modelSelections = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.modelSelections));
        this.localJava = b.localJava;
        this.localSidecarJar = b.localSidecarJar;
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
                .embeddingBaseUrl(embeddingBaseUrl).embeddingModel(embeddingModel)
                .embeddingDimension(embeddingDimension).embeddingApiKeyRef(embeddingApiKeyRef)
                .indexDirectory(indexDirectory).indexOnStartup(indexOnStartup)
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
                .agentRequestTimeoutSeconds(agentRequestTimeoutSeconds)
                .modelSelections(modelSelections)
                .localJava(localJava).localSidecarJar(localSidecarJar).localModelRoot(localModelRoot);
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

    public boolean indexOnStartup() {
        return indexOnStartup;
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

    public String agentCommand() {
        return agentCommand;
    }

    public String agentArgs() {
        return agentArgs;
    }

    public String agentRequestTimeoutSeconds() {
        return agentRequestTimeoutSeconds;
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

    /** Java-21-Programm des optionalen lokalen Sidecars; leer = keine lokalen Modelle. */
    public String localJava() {
        return localJava;
    }

    public String localSidecarJar() {
        return localSidecarJar;
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
        private String embeddingBaseUrl = "";
        private String embeddingModel = "";
        private String embeddingDimension = "";
        private String embeddingApiKeyRef = "";
        private String indexDirectory = "";
        private boolean indexOnStartup = true;
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
        private String agentCommand = "";
        private String agentArgs = "";
        private String agentRequestTimeoutSeconds = "30";
        private final Map<String, String> modelSelections = new LinkedHashMap<String, String>();
        private String localJava = "";
        private String localSidecarJar = "";
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

        public Builder indexOnStartup(boolean value) {
            this.indexOnStartup = value;
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

        public Builder localJava(String value) {
            this.localJava = text(value);
            return this;
        }

        public Builder localSidecarJar(String value) {
            this.localSidecarJar = text(value);
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
