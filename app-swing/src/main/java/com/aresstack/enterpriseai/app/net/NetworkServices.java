package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.NetworkConfig;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Was die Composition Root den Adaptern aus der Netzkonfiguration mitgibt: die Proxy-Route je Ziel
 * ({@link HttpRoutes}), die TLS-Vertrauensregel als verzögert gebaute {@link SSLSocketFactory} und den
 * {@code User-Agent}. Nichts davon greift beim Bauen auf Netz, Registry oder PowerShell zu; die erste Anfrage
 * löst die Route auf und baut die Vertrauensregel.
 */
public final class NetworkServices {

    public static final String DEFAULT_USER_AGENT_NAME = "EnterpriseAiClient";

    private final NetworkConfig config;
    private final HttpRoutes routes;
    private final SSLSocketFactory tls;
    private final String userAgent;
    private TrustPolicy trust;

    private NetworkServices(NetworkConfig config, HttpRoutes routes) {
        this.config = config;
        this.routes = routes;
        this.tls = TrustPolicy.deferred(config);
        this.userAgent = config.userAgent() != null ? config.userAgent() : defaultUserAgent();
    }

    public static NetworkServices from(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return new NetworkServices(config, HttpRoutes.from(config));
    }

    /** Mit eigener Route (Tests: ohne PowerShell). */
    public static NetworkServices create(NetworkConfig config, HttpRoutes routes) {
        if (config == null || routes == null) {
            throw new IllegalArgumentException("config and routes must not be null");
        }
        return new NetworkServices(config, routes);
    }

    /** {@code EnterpriseAiClient/<Version aus dem Manifest>}, ohne Manifest nur der Name. */
    public static String defaultUserAgent() {
        Package pkg = NetworkServices.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.trim().isEmpty() ? DEFAULT_USER_AGENT_NAME
                : DEFAULT_USER_AGENT_NAME + "/" + version.trim();
    }

    public NetworkConfig config() {
        return config;
    }

    public HttpRoutes routes() {
        return routes;
    }

    /** Verzögert gebaut; je HTTPS-Verbindung zu setzen, nie prozessweit. */
    public SSLSocketFactory tls() {
        return tls;
    }

    public String userAgent() {
        return userAgent;
    }

    /** Die gebaute Vertrauensregel (blockierend beim ersten Aufruf, danach gehalten); nie auf dem EDT rufen. */
    public synchronized TrustPolicy trust() {
        if (trust == null) {
            trust = TrustPolicy.from(config);
        }
        return trust;
    }

    /** Der Trust-Manager der Regel, z. B. für mTLS-Kontexte, die ihn mit einem Client-Schlüssel verbinden. */
    public X509TrustManager trustManager() {
        return trust().trustManager();
    }

    @Override
    public String toString() {
        return "NetworkServices[" + routes.describe() + ", tls=" + config.describeTrust() + ", userAgent=" + userAgent + "]";
    }
}
