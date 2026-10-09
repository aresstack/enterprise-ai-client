package com.aresstack.enterpriseai.app.ui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Der Inhalt des Einstellungen-Dialogs: alle Werte so, wie der Benutzer sie eingibt (Text, auch für Zahlen,
 * damit der Dialog nichts verliert und die Prüfung beim Konfigurations-Loader bleibt), dazu Schalter.
 * Enthält keine Secrets, nur Titel von KeePass-Einträgen; kennt weder Datei noch Adaptertypen. Unveränderlich;
 * Änderungen über {@link #toBuilder()}.
 *
 * <p>Nicht hier vertretene Schlüssel der Konfigurationsdatei (Timeouts, Retrieval-Feinheiten, Werkzeuggrenzen,
 * Proxy-Ausnahmen je Quelle, mTLS) bleiben in der Datei unverändert erhalten.
 */
public final class SettingsForm {

    public static final String PAIRING_KEY_STORE_FILE = "file";
    public static final String PAIRING_KEY_STORE_MEMORY = "memory";
    public static final String PROXY_SYSTEM = "SYSTEM";
    public static final String PROXY_NONE = "NONE";
    public static final String PROXY_MANUAL = "MANUAL";

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
    private final List<SourceForm> sources;
    private final boolean keePassEnabled;
    private final String keePassHost;
    private final String keePassPort;
    private final String keePassClientDisplayName;
    private final String keePassPairingKeyStore;
    private final String proxyMode;
    private final String proxyHost;
    private final String proxyPort;
    private final String nonProxyHosts;
    private final boolean agentEnabled;
    private final String agentCommand;
    private final String agentArgs;
    private final String agentRequestTimeoutSeconds;

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
        this.sources = Collections.unmodifiableList(new ArrayList<SourceForm>(b.sources));
        this.keePassEnabled = b.keePassEnabled;
        this.keePassHost = b.keePassHost;
        this.keePassPort = b.keePassPort;
        this.keePassClientDisplayName = b.keePassClientDisplayName;
        this.keePassPairingKeyStore = b.keePassPairingKeyStore;
        this.proxyMode = b.proxyMode;
        this.proxyHost = b.proxyHost;
        this.proxyPort = b.proxyPort;
        this.nonProxyHosts = b.nonProxyHosts;
        this.agentEnabled = b.agentEnabled;
        this.agentCommand = b.agentCommand;
        this.agentArgs = b.agentArgs;
        this.agentRequestTimeoutSeconds = b.agentRequestTimeoutSeconds;
    }

    /** Leeres Formular mit den Standardwerten des Loaders (Fenstertitel, KeePass an, Proxy SYSTEM, Agent aus). */
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
                .indexDirectory(indexDirectory).indexOnStartup(indexOnStartup).sources(sources)
                .keePassEnabled(keePassEnabled).keePassHost(keePassHost).keePassPort(keePassPort)
                .keePassClientDisplayName(keePassClientDisplayName).keePassPairingKeyStore(keePassPairingKeyStore)
                .proxyMode(proxyMode).proxyHost(proxyHost).proxyPort(proxyPort).nonProxyHosts(nonProxyHosts)
                .agentEnabled(agentEnabled).agentCommand(agentCommand).agentArgs(agentArgs)
                .agentRequestTimeoutSeconds(agentRequestTimeoutSeconds);
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

    public List<SourceForm> sources() {
        return sources;
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

    /** {@link #PROXY_SYSTEM}, {@link #PROXY_NONE} oder {@link #PROXY_MANUAL}. */
    public String proxyMode() {
        return proxyMode;
    }

    public String proxyHost() {
        return proxyHost;
    }

    public String proxyPort() {
        return proxyPort;
    }

    public String nonProxyHosts() {
        return nonProxyHosts;
    }

    public boolean agentEnabled() {
        return agentEnabled;
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

    @Override
    public String toString() {
        return "SettingsForm[chatBaseUrl=" + chatBaseUrl + ", chatModel=" + chatModel + ", chatApiKeyRef="
                + chatApiKeyRef + ", embeddingModel=" + embeddingModel + ", sources=" + sources.size()
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
        private List<SourceForm> sources = new ArrayList<SourceForm>();
        private boolean keePassEnabled = true;
        private String keePassHost = "127.0.0.1";
        private String keePassPort = "12546";
        private String keePassClientDisplayName = "Enterprise AI Client";
        private String keePassPairingKeyStore = PAIRING_KEY_STORE_FILE;
        private String proxyMode = PROXY_SYSTEM;
        private String proxyHost = "";
        private String proxyPort = "";
        private String nonProxyHosts = "";
        private boolean agentEnabled;
        private String agentCommand = "";
        private String agentArgs = "";
        private String agentRequestTimeoutSeconds = "30";

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

        public Builder sources(List<SourceForm> value) {
            this.sources = new ArrayList<SourceForm>(value == null ? Collections.<SourceForm>emptyList() : value);
            return this;
        }

        public Builder addSource(SourceForm value) {
            if (value != null) {
                this.sources.add(value);
            }
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

        public Builder proxyHost(String value) {
            this.proxyHost = text(value);
            return this;
        }

        public Builder proxyPort(String value) {
            this.proxyPort = text(value);
            return this;
        }

        public Builder nonProxyHosts(String value) {
            this.nonProxyHosts = text(value);
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

        public SettingsForm build() {
            return new SettingsForm(this);
        }
    }
}
