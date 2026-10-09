package com.aresstack.enterpriseai.app.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Netzwerk: Proxy-Modus, fester Proxy und Hosts, die ihn umgehen (Muster wie {@code *.intern}, {@code localhost}),
 * sowie die TLS-Vertrauensregel. Loopback-Adressen stehen immer auf der Ausnahmeliste, damit KeePassRPC und der
 * lokale MCP-Server nie über einen Proxy laufen.
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
    private final boolean useWindowsCertificateStore;
    private final Path caCertificatesFile;

    NetworkConfig(ProxyMode proxyMode, String proxyHost, int proxyPort, List<String> nonProxyHosts) {
        this(proxyMode, proxyHost, proxyPort, nonProxyHosts, true, null);
    }

    NetworkConfig(ProxyMode proxyMode, String proxyHost, int proxyPort, List<String> nonProxyHosts,
                  boolean useWindowsCertificateStore, Path caCertificatesFile) {
        this.proxyMode = proxyMode;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.nonProxyHosts = Collections.unmodifiableList(new ArrayList<String>(nonProxyHosts));
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
                + ", nonProxyHosts=" + nonProxyHosts
                + ", useWindowsCertificateStore=" + useWindowsCertificateStore
                + ", caCertificatesFile=" + (caCertificatesFile == null ? "keine" : caCertificatesFile) + "]";
    }
}
