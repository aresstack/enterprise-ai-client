package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.winproxy.ProxyMode;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Netzwerk: Proxy-Modus und -Parameter der Bibliothek win-proxy-java, Hosts ohne Proxy, Fristen, Anmeldung am
 * Proxy, HTTP-Kopfzeilen und die TLS-Vertrauensquellen der Bibliothek win-trust-java. Die Anwendung selbst wertet
 * weder Registry noch PAC-Skript aus; sie reicht diese Werte an die Bibliotheken durch, speichert Ergebnisse je
 * Host zwischen, protokolliert und macht Diagnose.
 *
 * <p>Proxy: {@link ProxyMode} der Bibliothek, mit {@code network.proxy.pacUrl} ({@code PAC_URL_MANUAL}),
 * {@code network.proxy.pacDiscoveryScript} (PowerShell- bzw. VBScript-Skript, das die PAC-Adresse liefert; leer =
 * Standard der Bibliothek) und {@code network.proxy.host/port} ({@code MANUAL_PROXY}). Loopback-Adressen stehen
 * immer auf der Ausnahmeliste, damit KeePassRPC und der lokale MCP-Server nie über einen Proxy laufen.
 *
 * <p>TLS: drei getrennte Quellen (JVM-Truststore {@code cacerts}, Windows-Stammzertifikate {@code Windows-ROOT},
 * Windows-Root- und Zwischenzertifikate per PowerShell-Export) plus optional eine PEM-/DER-Datei mit weiteren
 * CA-Zertifikaten.
 */
public final class NetworkConfig {

    public static final int DEFAULT_RESOLVE_TIMEOUT_MILLIS = 45000;

    private final ProxyMode proxyMode;
    private final String proxyHost;
    private final int proxyPort;
    private final List<String> nonProxyHosts;
    private final String pacUrl;
    private final String pacDiscoveryScript;
    private final URI testUrl;
    private final int resolveTimeoutMillis;
    private final ProxyAuthMode proxyAuthMode;
    private final SecretRef proxyCredentialRef;
    private final String userAgent;
    private final boolean preferIpv6;
    private final boolean tlsUseJvmDefault;
    private final boolean tlsUseWindowsRoot;
    private final boolean tlsUseWindowsCaStores;
    private final Path caCertificatesFile;

    private NetworkConfig(Builder b) {
        this.proxyMode = b.proxyMode == null ? ProxyMode.PAC_URL_POWERSHELL : b.proxyMode;
        this.proxyHost = b.proxyHost;
        this.proxyPort = b.proxyPort;
        this.nonProxyHosts = Collections.unmodifiableList(new ArrayList<String>(b.nonProxyHosts));
        this.pacUrl = b.pacUrl;
        this.pacDiscoveryScript = b.pacDiscoveryScript;
        this.testUrl = b.testUrl;
        this.resolveTimeoutMillis = b.resolveTimeoutMillis;
        this.proxyAuthMode = b.proxyAuthMode == null ? ProxyAuthMode.NONE : b.proxyAuthMode;
        this.proxyCredentialRef = b.proxyCredentialRef;
        this.userAgent = b.userAgent;
        this.preferIpv6 = b.preferIpv6;
        this.tlsUseJvmDefault = b.tlsUseJvmDefault;
        this.tlsUseWindowsRoot = b.tlsUseWindowsRoot;
        this.tlsUseWindowsCaStores = b.tlsUseWindowsCaStores;
        this.caCertificatesFile = b.caCertificatesFile;
    }

    /** Standardwerte des Loaders: PAC per PowerShell, keine Anmeldung, alle drei TLS-Quellen an. */
    public static Builder builder() {
        return new Builder();
    }

    /** Direkte Verbindung ohne Proxy, TLS-Standard; für Tests und Fakes. */
    public static NetworkConfig direct() {
        return builder().proxyMode(ProxyMode.DISABLED).build();
    }

    public ProxyMode proxyMode() {
        return proxyMode;
    }

    /** Proxy-Host, nur bei {@link ProxyMode#MANUAL_PROXY}; sonst {@code null}. */
    public String proxyHost() {
        return proxyHost;
    }

    /** Proxy-Port, nur bei {@link ProxyMode#MANUAL_PROXY}; sonst 0. */
    public int proxyPort() {
        return proxyPort;
    }

    /** Hostmuster ohne Proxy ({@code *.intern.example}); Loopback gilt immer als Ausnahme. */
    public List<String> nonProxyHosts() {
        return nonProxyHosts;
    }

    /** Adresse des PAC-Skripts bei {@link ProxyMode#PAC_URL_MANUAL}, sonst {@code null}. */
    public String pacUrl() {
        return pacUrl;
    }

    /** Skript, das die PAC-Adresse ermittelt (PowerShell bzw. VBScript); {@code null} = Standard der Bibliothek. */
    public String pacDiscoveryScript() {
        return pacDiscoveryScript;
    }

    /** Ziel für „Proxy auflösen“ und den HTTPS-Test; Standard {@code <chat.baseUrl>/models}; {@code null}, wenn beides fehlt. */
    public URI testUrl() {
        return testUrl;
    }

    /** Frist für eine Proxy-Auflösung je Host (Skripte, Registry, PAC-Download und -Auswertung zusammen). */
    public int resolveTimeoutMillis() {
        return resolveTimeoutMillis;
    }

    public ProxyAuthMode proxyAuthMode() {
        return proxyAuthMode;
    }

    /** KeePass-Eintrag mit Benutzername und Passwort für den Proxy bei {@link ProxyAuthMode#BASIC}; sonst {@code null}. */
    public SecretRef proxyCredentialRef() {
        return proxyCredentialRef;
    }

    /** {@code User-Agent} aller HTTP-Anfragen; {@code null} = Standard der Anwendung. */
    public String userAgent() {
        return userAgent;
    }

    /** {@code java.net.preferIPv6Addresses}: IPv6-Adressen bevorzugen, wenn ein Host beide hat. */
    public boolean preferIpv6() {
        return preferIpv6;
    }

    /** JVM-Truststore ({@code cacerts}) als Vertrauensquelle. */
    public boolean tlsUseJvmDefault() {
        return tlsUseJvmDefault;
    }

    /** Windows-Stammzertifikate ({@code Windows-ROOT} über SunMSCAPI); auf anderen Systemen ohne Wirkung. */
    public boolean tlsUseWindowsRoot() {
        return tlsUseWindowsRoot;
    }

    /** Windows-Root- und Zwischenzertifikate (PowerShell-Export der Speicher Root und CA); nur unter Windows. */
    public boolean tlsUseWindowsCaStores() {
        return tlsUseWindowsCaStores;
    }

    /** PEM- oder DER-Datei mit zusätzlichen CA-Zertifikaten (z. B. des Firmen-Proxys) oder {@code null}. */
    public Path caCertificatesFile() {
        return caCertificatesFile;
    }

    /** Die drei TLS-Quellen als Text für Protokoll und Dialog, ohne etwas zu laden. */
    public String describeTrust() {
        StringBuilder text = new StringBuilder();
        if (tlsUseJvmDefault) {
            text.append("JVM-Truststore");
        }
        if (tlsUseWindowsRoot) {
            text.append(text.length() > 0 ? ", " : "").append("Windows-ROOT");
        }
        if (tlsUseWindowsCaStores) {
            text.append(text.length() > 0 ? ", " : "").append("Windows Root+CA (PowerShell)");
        }
        if (caCertificatesFile != null) {
            text.append(text.length() > 0 ? ", " : "").append("CA-Datei ").append(caCertificatesFile);
        }
        return text.length() == 0 ? "keine (JVM-Rückfall)" : text.toString();
    }

    @Override
    public String toString() {
        return "NetworkConfig[proxyMode=" + proxyMode
                + (proxyMode == ProxyMode.MANUAL_PROXY ? ", proxy=" + proxyHost + ":" + proxyPort : "")
                + (pacUrl != null ? ", pacUrl=" + pacUrl : "")
                + (pacDiscoveryScript != null ? ", pacDiscoveryScript=eigenes" : "")
                + ", nonProxyHosts=" + nonProxyHosts
                + ", testUrl=" + testUrl
                + ", resolveTimeoutMillis=" + resolveTimeoutMillis
                + ", proxyAuth=" + proxyAuthMode
                + (proxyCredentialRef != null ? " (" + proxyCredentialRef + ")" : "")
                + (userAgent != null ? ", userAgent=" + userAgent : "")
                + ", preferIpv6=" + preferIpv6
                + ", tls=" + describeTrust() + "]";
    }

    public static final class Builder {
        private ProxyMode proxyMode = ProxyMode.PAC_URL_POWERSHELL;
        private String proxyHost;
        private int proxyPort;
        private List<String> nonProxyHosts = new ArrayList<String>();
        private String pacUrl;
        private String pacDiscoveryScript;
        private URI testUrl;
        private int resolveTimeoutMillis = DEFAULT_RESOLVE_TIMEOUT_MILLIS;
        private ProxyAuthMode proxyAuthMode = ProxyAuthMode.NONE;
        private SecretRef proxyCredentialRef;
        private String userAgent;
        private boolean preferIpv6;
        private boolean tlsUseJvmDefault = true;
        private boolean tlsUseWindowsRoot = true;
        private boolean tlsUseWindowsCaStores = true;
        private Path caCertificatesFile;

        private Builder() {
        }

        public Builder proxyMode(ProxyMode value) {
            this.proxyMode = value;
            return this;
        }

        public Builder proxy(String host, int port) {
            this.proxyHost = host;
            this.proxyPort = port;
            return this;
        }

        public Builder nonProxyHosts(List<String> value) {
            this.nonProxyHosts = new ArrayList<String>(value == null ? Collections.<String>emptyList() : value);
            return this;
        }

        public Builder pacUrl(String value) {
            this.pacUrl = value;
            return this;
        }

        public Builder pacDiscoveryScript(String value) {
            this.pacDiscoveryScript = value;
            return this;
        }

        public Builder testUrl(URI value) {
            this.testUrl = value;
            return this;
        }

        public Builder resolveTimeoutMillis(int value) {
            this.resolveTimeoutMillis = value;
            return this;
        }

        public Builder proxyAuth(ProxyAuthMode mode, SecretRef credentialRef) {
            this.proxyAuthMode = mode;
            this.proxyCredentialRef = credentialRef;
            return this;
        }

        public Builder userAgent(String value) {
            this.userAgent = value;
            return this;
        }

        public Builder preferIpv6(boolean value) {
            this.preferIpv6 = value;
            return this;
        }

        public Builder tls(boolean jvmDefault, boolean windowsRoot, boolean windowsCaStores) {
            this.tlsUseJvmDefault = jvmDefault;
            this.tlsUseWindowsRoot = windowsRoot;
            this.tlsUseWindowsCaStores = windowsCaStores;
            return this;
        }

        public Builder caCertificatesFile(Path value) {
            this.caCertificatesFile = value;
            return this;
        }

        public NetworkConfig build() {
            return new NetworkConfig(this);
        }
    }
}
