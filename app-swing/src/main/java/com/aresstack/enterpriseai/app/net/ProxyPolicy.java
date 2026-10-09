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
 * Entscheidet je Ziel-URI, ob ein Proxy benutzt wird. {@link ProxyMode#AUTO} fragt zuerst das PAC-/WPAD-Skript
 * des Unternehmens ({@link PacProxyRoutes}, Adresse aus {@code network.proxy.pacUrl} oder den
 * Windows-Einstellungen) und fällt ohne Entscheidung des Skripts auf die Systemeinstellungen zurück;
 * {@link ProxyMode#SYSTEM} übernimmt nur die Proxy-Einstellungen des Betriebssystems (unter Windows der fest
 * eingetragene Proxy der Internetoptionen) bzw. die JVM-Properties {@code http(s).proxyHost}, wenn sie gesetzt
 * sind; {@link ProxyMode#NONE} verbindet immer direkt, {@link ProxyMode#MANUAL} nutzt Host und Port aus der
 * Konfiguration mit Ausnahmen. Loopback-Ziele (der lokale MCP-Endpoint, KeePassRPC) gehen nie über einen Proxy.
 *
 * <p>Java liest die Systemeinstellungen nur, wenn {@code java.net.useSystemProxies=true} gesetzt ist, bevor der
 * Standard-{@link ProxySelector} zum ersten Mal erzeugt wird. Der öffentliche Konstruktor setzt die Property
 * deshalb für {@link ProxyMode#AUTO} und {@link ProxyMode#SYSTEM}, sofern sie nicht schon gesetzt ist, und
 * erfasst erst danach den Standard-Selector. Java 8 wertet unter Windows selbst nur feste Proxy-Einträge aus,
 * keine PAC-Skripte; die übernimmt {@link PacProxyRoutes}.
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
    private final PacProxyRoutes pacRoutes;
    private final Selector installed;
    private final boolean systemSettingsDeferred;

    public ProxyPolicy(NetworkConfig config) {
        this(config, Jvm.capture(config));
    }

    private ProxyPolicy(NetworkConfig config, Jvm jvm) {
        this(config, jvm.systemSelector, config.proxyMode() == ProxyMode.AUTO ? PacProxyRoutes.from(config) : null,
                jvm.systemSettingsDeferred);
    }

    /**
     * Für {@link ProxyMode#AUTO} und {@link ProxyMode#SYSTEM}: Systemeinstellungen einschalten, bevor der
     * Standard-Selector entsteht. Ist schon eine Regel installiert (laufende Anwendung, etwa beim Verbindungstest
     * des Einstellungen-Dialogs), zählt der Selector darunter, damit eine neue Regel die Systemeinstellungen fragt
     * und nicht die laufende Regel.
     */
    static ProxySelector defaultSelectorFor(NetworkConfig config) {
        if (config != null && usesSystemSettings(config.proxyMode()) && System.getProperty(USE_SYSTEM_PROXIES) == null) {
            System.setProperty(USE_SYSTEM_PROXIES, "true");
        }
        return beneathInstalledRules(ProxySelector.getDefault());
    }

    static ProxySelector beneathInstalledRules(ProxySelector current) {
        ProxySelector selector = current;
        while (selector instanceof Selector) {
            selector = ((Selector) selector).system();
        }
        return selector;
    }

    /**
     * Was die JVM beim Erzeugen hergibt. {@code java.net.useSystemProxies} liest der Standard-Selector nur bei seiner
     * Initialisierung; entsteht eine Regel mit Systemeinstellungen erst, wenn schon eine andere installiert ist und
     * die Eigenschaft bis dahin fehlte, gelten die Systemeinstellungen erst nach einem Neustart.
     */
    private static final class Jvm {
        final ProxySelector systemSelector;
        final boolean systemSettingsDeferred;

        private Jvm(ProxySelector systemSelector, boolean systemSettingsDeferred) {
            this.systemSelector = systemSelector;
            this.systemSettingsDeferred = systemSettingsDeferred;
        }

        static Jvm capture(NetworkConfig config) {
            boolean deferred = config != null && usesSystemSettings(config.proxyMode())
                    && System.getProperty(USE_SYSTEM_PROXIES) == null
                    && ProxySelector.getDefault() instanceof Selector;
            return new Jvm(defaultSelectorFor(config), deferred);
        }
    }

    private static boolean usesSystemSettings(ProxyMode mode) {
        return mode == ProxyMode.AUTO || mode == ProxyMode.SYSTEM;
    }

    ProxyPolicy(NetworkConfig config, ProxySelector systemSelector) {
        this(config, systemSelector, config != null && config.proxyMode() == ProxyMode.AUTO
                ? PacProxyRoutes.from(config) : null);
    }

    ProxyPolicy(NetworkConfig config, ProxySelector systemSelector, PacProxyRoutes pacRoutes) {
        this(config, systemSelector, pacRoutes, false);
    }

    private ProxyPolicy(NetworkConfig config, ProxySelector systemSelector, PacProxyRoutes pacRoutes,
                        boolean systemSettingsDeferred) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.mode = config.proxyMode();
        this.manualAddress = mode == ProxyMode.MANUAL
                ? InetSocketAddress.createUnresolved(config.proxyHost(), config.proxyPort())
                : null;
        this.nonProxyHosts = config.nonProxyHosts();
        this.systemSelector = systemSelector;
        this.pacRoutes = mode == ProxyMode.AUTO ? pacRoutes : null;
        this.installed = new Selector();
        this.systemSettingsDeferred = systemSettingsDeferred;
    }

    public ProxyMode mode() {
        return mode;
    }

    /**
     * {@code true}, wenn diese Regel Systemeinstellungen nutzen soll, die laufende JVM sie aber erst nach einem
     * Neustart liest (sie wurde ohne AUTO/SYSTEM gestartet); {@link #describeRoute} sagt das dazu.
     */
    public boolean systemSettingsDeferred() {
        return systemSettingsDeferred;
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

    /**
     * Für das Protokoll: wie ein Ziel geroutet wird und warum (Ausnahme, PAC-Ergebnis, Systemeinstellung). Bei
     * {@link ProxyMode#AUTO} löst das die erste PAC-Auswertung aus, damit Fehler schon beim Start sichtbar sind.
     */
    public String describeRoute(URI target) {
        if (target == null || target.getHost() == null) {
            return "direkt (kein Host)";
        }
        if (isLoopback(target.getHost())) {
            return "direkt (Loopback)";
        }
        if (isExcluded(target.getHost())) {
            return "direkt (network.proxy.nonProxyHosts)";
        }
        switch (mode) {
            case NONE:
                return "direkt (NONE)";
            case MANUAL:
                return "PROXY " + manualAddress.getHostString() + ":" + manualAddress.getPort() + " (MANUAL)";
            case AUTO:
                String pac = pacRoutes == null ? "kein PAC-Skript" : pacRoutes.describe(target);
                Proxy decided = pacRoutes == null ? null : pacRoutes.resolve(target);
                if (decided != null) {
                    return pac;
                }
                return pac + "; Systemeinstellungen: " + describeSystem(target);
            case SYSTEM:
            default:
                return describeSystem(target) + " (SYSTEM)";
        }
    }

    private String describeSystem(URI target) {
        Proxy proxy = systemProxyFor(target);
        String text;
        if (proxy.type() == Proxy.Type.DIRECT) {
            text = "direkt";
        } else if (proxy.address() instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) proxy.address();
            text = proxy.type() + " " + inet.getHostString() + ":" + inet.getPort();
        } else {
            text = proxy.type() + " " + proxy.address();
        }
        if (systemSettingsDeferred) {
            text += " [Systemeinstellungen liest diese laufende Anwendung erst nach einem Neustart]";
        }
        return text;
    }

    private Proxy systemProxyFor(URI target) {
        for (Proxy proxy : systemProxies(target)) {
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
            case AUTO:
                Proxy decided = pacRoutes == null ? null : pacRoutes.resolve(uri);
                if (decided != null) {
                    return Collections.singletonList(decided);
                }
                return systemProxies(uri);
            case SYSTEM:
            default:
                return systemProxies(uri);
        }
    }

    private List<Proxy> systemProxies(URI uri) {
        if (systemSelector == null) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }
        List<Proxy> proxies = systemSelector.select(uri);
        return proxies == null || proxies.isEmpty() ? Collections.singletonList(Proxy.NO_PROXY) : proxies;
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
                + manualAddress.getPort()) + (pacRoutes == null ? "" : " " + pacRoutes)
                + (nonProxyHosts.isEmpty() ? "" : " except " + nonProxyHosts) + "]";
    }

    /** Der installierte Selector; delegiert an die Regel, Verbindungsfehler gehen an den System-Selector. */
    private final class Selector extends ProxySelector {

        /** Der Selector, den diese Regel beim Erzeugen vorfand (Systemeinstellungen). */
        ProxySelector system() {
            return systemSelector;
        }

        @Override
        public List<Proxy> select(URI uri) {
            return ProxyPolicy.this.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            if (usesSystemSettings(mode) && systemSelector != null) {
                systemSelector.connectFailed(uri, sa, ioe);
            }
        }
    }
}
