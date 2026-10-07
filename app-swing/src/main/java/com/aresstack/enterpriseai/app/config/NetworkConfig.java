package com.aresstack.enterpriseai.app.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Netzwerk: Proxy-Modus, fester Proxy und Hosts, die ihn umgehen (Muster wie {@code *.intern}, {@code localhost}).
 * Loopback-Adressen stehen immer auf der Ausnahmeliste, damit KeePassRPC und der lokale MCP-Server nie über einen
 * Proxy laufen.
 */
public final class NetworkConfig {

    private final ProxyMode proxyMode;
    private final String proxyHost;
    private final int proxyPort;
    private final List<String> nonProxyHosts;

    NetworkConfig(ProxyMode proxyMode, String proxyHost, int proxyPort, List<String> nonProxyHosts) {
        this.proxyMode = proxyMode;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.nonProxyHosts = Collections.unmodifiableList(new ArrayList<String>(nonProxyHosts));
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

    @Override
    public String toString() {
        return "NetworkConfig[proxyMode=" + proxyMode
                + (proxyMode == ProxyMode.MANUAL ? ", proxy=" + proxyHost + ":" + proxyPort : "")
                + ", nonProxyHosts=" + nonProxyHosts + "]";
    }
}
