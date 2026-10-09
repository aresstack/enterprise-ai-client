package com.aresstack.enterpriseai.app.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Netzwerk: Proxy-Modus, PAC-Skript, fester Proxy und Hosts, die ihn umgehen (Muster wie {@code *.intern},
 * {@code localhost}), sowie die TLS-Vertrauensregel. Loopback-Adressen stehen immer auf der Ausnahmeliste, damit
 * KeePassRPC und der lokale MCP-Server nie über einen Proxy laufen.
 *
 * <p>Proxy: Firmennetze verteilen ihren Proxy meist als PAC-/WPAD-Skript; Java 8 wertet so ein Skript nicht
 * aus, PowerShell und Browser schon. {@link ProxyMode#AUTO} holt die Skriptadresse ({@link #pacUrl()} oder die
 * Windows-Einstellungen per {@link #pacDiscovery()}) und wertet das Skript in der Anwendung aus.
 *
 * <p>TLS: Java vertraut von Haus aus nur seinem eigenen Truststore ({@code cacerts}), nicht dem
 * Zertifikatspeicher des Betriebssystems. Damit sich die Anwendung unter Windows wie PowerShell oder ein Browser
 * verhält, werden die Windows-Stammzertifikate ({@link #useWindowsCertificateStore()}) und optional eine
 * PEM-/DER-Datei mit weiteren CA-Zertifikaten ({@link #caCertificatesFile()}) zusätzlich herangezogen.
 */
public final class NetworkConfig {

    private final ProxyMode proxyMode;
    private final String proxyHost;
    private final int proxyPort;
    private final List<String> nonProxyHosts;
    private final String pacUrl;
    private final PacDiscovery pacDiscovery;
    private final boolean useWindowsCertificateStore;
    private final Path caCertificatesFile;

    NetworkConfig(ProxyMode proxyMode, String proxyHost, int proxyPort, List<String> nonProxyHosts) {
        this(proxyMode, proxyHost, proxyPort, nonProxyHosts, true, null);
    }

    NetworkConfig(ProxyMode proxyMode, String proxyHost, int proxyPort, List<String> nonProxyHosts,
                  boolean useWindowsCertificateStore, Path caCertificatesFile) {
        this(proxyMode, proxyHost, proxyPort, nonProxyHosts, null, PacDiscovery.WINDOWS_SETTINGS,
                useWindowsCertificateStore, caCertificatesFile);
    }

    NetworkConfig(ProxyMode proxyMode, String proxyHost, int proxyPort, List<String> nonProxyHosts,
                  String pacUrl, PacDiscovery pacDiscovery,
                  boolean useWindowsCertificateStore, Path caCertificatesFile) {
        this.proxyMode = proxyMode;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.nonProxyHosts = Collections.unmodifiableList(new ArrayList<String>(nonProxyHosts));
        this.pacUrl = pacUrl;
        this.pacDiscovery = pacDiscovery == null ? PacDiscovery.WINDOWS_SETTINGS : pacDiscovery;
        this.useWindowsCertificateStore = useWindowsCertificateStore;
        this.caCertificatesFile = caCertificatesFile;
    }

    public ProxyMode proxyMode() {
        return proxyMode;
    }

    /** Proxy-Host, nur bei {@link ProxyMode#MANUAL}. */
    public String proxyHost() {
        return proxyHost;
    }

    public int proxyPort() {
        return proxyPort;
    }

    public List<String> nonProxyHosts() {
        return nonProxyHosts;
    }

    /** Adresse des PAC-/WPAD-Skripts ({@code http}, {@code https} oder {@code file}) bei {@link ProxyMode#AUTO}, sonst {@code null}. */
    public String pacUrl() {
        return pacUrl;
    }

    /** Woher die PAC-Adresse kommt, wenn {@link #pacUrl()} fehlt; nie {@code null}. */
    public PacDiscovery pacDiscovery() {
        return pacDiscovery;
    }

    /**
     * Unter Windows zusätzlich die Stammzertifikate des Windows-Zertifikatspeichers ({@code Windows-ROOT}) als
     * vertrauenswürdig behandeln; auf anderen Systemen ohne Wirkung.
     */
    public boolean useWindowsCertificateStore() {
        return useWindowsCertificateStore;
    }

    /** PEM- oder DER-Datei mit zusätzlichen CA-Zertifikaten (z. B. des Firmen-Proxys) oder {@code null}. */
    public Path caCertificatesFile() {
        return caCertificatesFile;
    }

    @Override
    public String toString() {
        return "NetworkConfig[proxyMode=" + proxyMode
                + (proxyMode == ProxyMode.MANUAL ? ", proxy=" + proxyHost + ":" + proxyPort : "")
                + (proxyMode == ProxyMode.AUTO ? ", pac=" + (pacUrl == null ? pacDiscovery : pacUrl) : "")
                + ", nonProxyHosts=" + nonProxyHosts
                + ", useWindowsCertificateStore=" + useWindowsCertificateStore
                + ", caCertificatesFile=" + (caCertificatesFile == null ? "keine" : caCertificatesFile) + "]";
    }
}
