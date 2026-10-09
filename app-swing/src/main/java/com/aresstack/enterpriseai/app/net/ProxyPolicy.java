package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.config.ProxyMode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Entscheidet je Ziel-URI, ob ein Proxy benutzt wird. {@link ProxyMode#SYSTEM} übernimmt die Proxy-Einstellungen
 * des Betriebssystems (unter Windows die Internetoptionen, so wie PowerShell und Browser sie nutzen) bzw. die
 * JVM-Properties {@code http(s).proxyHost}, wenn sie gesetzt sind; {@link ProxyMode#NONE} verbindet immer direkt,
 * {@link ProxyMode#MANUAL} nutzt Host und Port aus der Konfiguration mit Ausnahmen. Loopback-Ziele (der lokale
 * MCP-Endpoint, KeePassRPC) gehen nie über einen Proxy.
 *
 * <p>Java liest die Systemeinstellungen nur, wenn {@code java.net.useSystemProxies=true} gesetzt ist, bevor der
 * Standard-{@link ProxySelector} zum ersten Mal erzeugt wird. Der öffentliche Konstruktor setzt die Property
 * deshalb für {@link ProxyMode#SYSTEM}, sofern sie nicht schon gesetzt ist, und erfasst erst danach den
 * Standard-Selector. Java 8 wertet unter Windows nur feste Proxy-Einträge aus, keine PAC-Skripte.
 *
 * <p>{@link #install()} setzt die Regel als {@link ProxySelector} der JVM; {@link #proxyFor(URI)} liefert sie
 * als {@link Proxy} für Adapter mit eigener Proxy-Einstellung. Keine statischen Felder, der Standard-Selector
 * wird im Konstruktor erfasst und bei {@link #uninstall()} zurückgesetzt.
 */
public final class ProxyPolicy {

    static final String USE_SYSTEM_PROXIES = "java.net.useSystemProxies";

    private final ProxyMode mode;
    private final InetSocketAddress manualAddress;
    private final List<String> nonProxyHosts;
    private final ProxySelector systemSelector;
    private final Selector installed;

    public ProxyPolicy(NetworkConfig config) {
        this(config, defaultSelectorFor(config));
    }

    /** Für {@link ProxyMode#SYSTEM}: Systemeinstellungen einschalten, bevor der Standard-Selector entsteht. */
    static ProxySelector defaultSelectorFor(NetworkConfig config) {
        if (config != null && config.proxyMode() == ProxyMode.SYSTEM && System.getProperty(USE_SYSTEM_PROXIES) == null) {
            System.setProperty(USE_SYSTEM_PROXIES, "true");
        }
        return ProxySelector.getDefault();
    }

    ProxyPolicy(NetworkConfig config, ProxySelector systemSelector) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.mode = config.proxyMode();
        this.manualAddress = mode == ProxyMode.MANUAL
                ? InetSocketAddress.createUnresolved(config.proxyHost(), config.proxyPort())
                : null;
        this.nonProxyHosts = config.nonProxyHosts();
        this.systemSelector = systemSelector;
        this.installed = new Selector();
    }

    public ProxyMode mode() {
        return mode;
    }

    /** Der Proxy für ein Ziel oder {@link Proxy#NO_PROXY}. Nie {@code null}. */
    public Proxy proxyFor(URI target) {
        if (target == null) {
            return Proxy.NO_PROXY;
        }
        List<Proxy> proxies = select(target);
        for (Proxy proxy : proxies) {
            if (proxy != null) {
                return proxy;
            }
        }
        return Proxy.NO_PROXY;
    }

    /** Setzt die Regel als JVM-weiten {@link ProxySelector}. Idempotent. */
    public void install() {
        ProxySelector.setDefault(installed);
    }

    /** Stellt den beim Erzeugen gültigen Selector wieder her, falls diese Regel installiert ist. */
    public void uninstall() {
        if (ProxySelector.getDefault() == installed) {
            ProxySelector.setDefault(systemSelector);
        }
    }

    private List<Proxy> select(URI uri) {
        String host = uri.getHost();
        if (isLoopback(host) || isExcluded(host)) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }
        switch (mode) {
            case NONE:
                return Collections.singletonList(Proxy.NO_PROXY);
            case MANUAL:
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP, manualAddress));
            case SYSTEM:
            default:
                if (systemSelector == null) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
                List<Proxy> proxies = systemSelector.select(uri);
                return proxies == null || proxies.isEmpty() ? Collections.singletonList(Proxy.NO_PROXY) : proxies;
        }
    }

    static boolean isLoopback(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        return h.equals("localhost") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1") || h.startsWith("127.");
    }

    private boolean isExcluded(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String pattern : nonProxyHosts) {
            if (matches(h, pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Muster wie bei {@code http.nonProxyHosts}: {@code *.example.org} oder {@code 10.*}, sonst exakt. */
    static boolean matches(String host, String pattern) {
        if (pattern.isEmpty()) {
            return false;
        }
        boolean leading = pattern.startsWith("*");
        boolean trailing = pattern.endsWith("*");
        if (leading && trailing) {
            String core = pattern.substring(1, pattern.length() - 1);
            return core.isEmpty() || host.contains(core);
        }
        if (leading) {
            return host.endsWith(pattern.substring(1));
        }
        if (trailing) {
            return host.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return host.equals(pattern);
    }

    @Override
    public String toString() {
        return "ProxyPolicy[" + mode + (manualAddress == null ? "" : " " + manualAddress.getHostString() + ":"
                + manualAddress.getPort()) + (nonProxyHosts.isEmpty() ? "" : " except " + nonProxyHosts) + "]";
    }

    /** Der installierte Selector; delegiert an die Regel, Verbindungsfehler gehen an den System-Selector. */
    private final class Selector extends ProxySelector {

        @Override
        public List<Proxy> select(URI uri) {
            return ProxyPolicy.this.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            if (mode == ProxyMode.SYSTEM && systemSelector != null) {
                systemSelector.connectFailed(uri, sa, ioe);
            }
        }
    }
}
