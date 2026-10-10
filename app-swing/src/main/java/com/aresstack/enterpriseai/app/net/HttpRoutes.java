package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.winproxy.ProxyConfiguration;
import com.aresstack.winproxy.ProxyDiagnostics;
import com.aresstack.winproxy.ProxyMode;
import com.aresstack.winproxy.ProxyResult;
import com.aresstack.winproxy.WindowsProxyResolver;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Proxy-Route der Anwendung ({@link HttpRoutePort}): je Ziel fragt sie den {@link WindowsProxyResolver} von
 * win-proxy-java im konfigurierten {@link ProxyMode} (PAC-Adresse per PowerShell, VBScript oder
 * Windows-Einstellungen, feste PAC-Adresse, fester Proxy, Windows-Proxyeinstellungen, keiner) und übersetzt das
 * {@link ProxyResult} in eine {@link HttpRoute}: PROXY und DIRECT so, ERROR und NOT_IMPLEMENTED als
 * {@link HttpRoute#unavailable} mit Grund und Detail, nie still als Direktverbindung. Die Adapter geben die Route
 * jeder {@code HttpURLConnection} einzeln mit; es gibt keinen prozessweiten {@code ProxySelector}.
 *
 * <p>Eigenes der Anwendung: Loopback und {@code network.proxy.nonProxyHosts} gehen immer direkt; Ergebnisse werden
 * je Schema, Host und Port zwischengespeichert ({@value #SUCCESS_TTL_MILLIS} ms, Fehler
 * {@value #FAILURE_TTL_MILLIS} ms); jede Auflösung läuft auf einem Daemon-Thread mit der Frist
 * {@code network.proxy.resolveTimeoutMillis} über allen Schritten der Bibliothek (PowerShell, cscript, reg.exe,
 * PAC-Download und -Auswertung haben darin eigene Fristen); die erste Auflösung geschieht bei der ersten Anfrage,
 * nie beim Start. {@link #diagnose} liefert die Schrittliste der Bibliothek für „Proxy auflösen“.
 */
public final class HttpRoutes implements HttpRoutePort {

    /** Zugriff auf die Bibliothek; Naht für Tests ohne PowerShell. */
    public interface Resolver {
        ProxyResult resolve(String url);

        ProxyDiagnostics diagnose(String url);
    }

    static final long SUCCESS_TTL_MILLIS = 10 * 60 * 1000L;
    static final long FAILURE_TTL_MILLIS = 60 * 1000L;
    static final String REASON_LOOPBACK = "loopback";
    static final String REASON_NON_PROXY_HOST = "non-proxy-host";
    static final String REASON_DISABLED = "disabled";
    static final String REASON_TIMEOUT = "resolve-timeout";
    static final String REASON_RESOLVER_FAILED = "resolver-failed";

    private static final Logger LOG = Logger.getLogger(HttpRoutes.class.getName());
    private static final ExecutorService RESOLVERS = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "enterprise-ai-proxy-resolve");
            t.setDaemon(true);
            return t;
        }
    });

    private final NetworkConfig config;
    private final Resolver resolver;
    private final Clock clock;
    private final Map<String, Entry> cache = new LinkedHashMap<String, Entry>();

    /** Zeitquelle für die Cache-Fristen; Tests setzen eine eigene. */
    public interface Clock {
        long now();
    }

    private HttpRoutes(NetworkConfig config, Resolver resolver, Clock clock) {
        this.config = config;
        this.resolver = resolver;
        this.clock = clock;
    }

    /** Produktiv: der Resolver der Bibliothek mit der Konfiguration aus {@link #libraryConfiguration}. */
    public static HttpRoutes from(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        final WindowsProxyResolver library = new WindowsProxyResolver(libraryConfiguration(config));
        return create(config, new Resolver() {
            @Override
            public ProxyResult resolve(String url) {
                return library.resolve(url);
            }

            @Override
            public ProxyDiagnostics diagnose(String url) {
                return library.diagnose(url);
            }
        }, new Clock() {
            @Override
            public long now() {
                return System.currentTimeMillis();
            }
        });
    }

    public static HttpRoutes create(NetworkConfig config, Resolver resolver, Clock clock) {
        if (config == null || resolver == null || clock == null) {
            throw new IllegalArgumentException("config, resolver and clock must not be null");
        }
        return new HttpRoutes(config, resolver, clock);
    }

    /**
     * Die Konfiguration der Bibliothek aus der Anwendungskonfiguration. Die Test-URL wird immer gesetzt (die
     * Bibliothek hätte sonst eine eigene Vorgabe mit fremdem Host); fehlt eine Test-URL, zählt ein Loopback-Ziel,
     * das nur bei leerem Ziel überhaupt zum Zug käme.
     */
    public static ProxyConfiguration libraryConfiguration(NetworkConfig config) {
        ProxyConfiguration.Builder b = ProxyConfiguration.builder().mode(config.proxyMode());
        b.testUrl(config.testUrl() == null ? "http://127.0.0.1/" : config.testUrl().toString());
        if (config.pacUrl() != null) {
            b.pacUrl(config.pacUrl());
        }
        if (config.pacDiscoveryScript() != null) {
            b.pacUrlDiscoveryScript(config.pacDiscoveryScript());
        }
        if (config.proxyHost() != null) {
            b.manualProxyHost(config.proxyHost());
        }
        if (config.proxyPort() > 0) {
            b.manualProxyPort(config.proxyPort());
        }
        return b.build();
    }

    /** Die Prüfung der Bibliothek ({@code validate()}), als Liste; leer, wenn der Modus so laufen kann. */
    public static List<String> validationProblems(NetworkConfig config) {
        return libraryConfiguration(config).validationProblems();
    }

    /** Das Skript, das die Bibliothek ohne eigenes Skript für den Modus nähme (PowerShell bzw. VBScript). */
    public static String defaultDiscoveryScript(ProxyMode mode) {
        return ProxyConfiguration.builder().mode(mode).build().getPacUrlDiscoveryScript();
    }

    public NetworkConfig config() {
        return config;
    }

    @Override
    public HttpRoute routeFor(URI target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        String host = target.getHost();
        if (HostPatterns.isLoopback(host)) {
            return HttpRoute.direct(REASON_LOOPBACK);
        }
        if (HostPatterns.matchesAny(host, config.nonProxyHosts())) {
            return HttpRoute.direct(REASON_NON_PROXY_HOST);
        }
        if (config.proxyMode() == ProxyMode.DISABLED) {
            return HttpRoute.direct(REASON_DISABLED);
        }
        String key = key(target);
        long now = clock.now();
        synchronized (cache) {
            Entry cached = cache.get(key);
            if (cached != null && cached.expiresAt > now) {
                return cached.route;
            }
        }
        HttpRoute route = resolve(target);
        long ttl = route.isUnavailable() ? FAILURE_TTL_MILLIS : SUCCESS_TTL_MILLIS;
        synchronized (cache) {
            cache.put(key, new Entry(route, now + ttl));
        }
        LOG.log(route.isUnavailable() ? Level.WARNING : Level.INFO,
                "Proxy-Route " + key + " (" + config.proxyMode() + "): " + route.describe());
        return route;
    }

    /** Vergisst alle zwischengespeicherten Routen (z. B. nach einem Netzwechsel). */
    public void invalidate() {
        synchronized (cache) {
            cache.clear();
        }
    }

    /**
     * Die Auflösung mit allen Schritten der Bibliothek, ohne Cache und ohne Ausnahmen (DNS des Ziels, TLS,
     * Anmeldung sind nicht Teil davon), als Zeilen für die Oberfläche:
     * {@code Resolving <url> ...}, je Schritt eine Zeile, zuletzt {@code Result: ...}.
     */
    public Diagnosis diagnose(URI target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        List<String> lines = new ArrayList<String>();
        lines.add("Resolving " + target + " ...");
        lines.add("Mode: " + config.proxyMode());
        String host = target.getHost();
        if (HostPatterns.isLoopback(host)) {
            return Diagnosis.of(lines, HttpRoute.direct(REASON_LOOPBACK), "Loopback-Ziel, nie über einen Proxy");
        }
        if (HostPatterns.matchesAny(host, config.nonProxyHosts())) {
            return Diagnosis.of(lines, HttpRoute.direct(REASON_NON_PROXY_HOST),
                    "Host steht in network.proxy.nonProxyHosts");
        }
        if (config.proxyMode() == ProxyMode.DISABLED) {
            return Diagnosis.of(lines, HttpRoute.direct(REASON_DISABLED), "Modus DISABLED");
        }
        final String url = target.toString();
        ProxyDiagnostics diagnostics;
        try {
            diagnostics = withTimeout(new Callable<ProxyDiagnostics>() {
                @Override
                public ProxyDiagnostics call() {
                    return resolver.diagnose(url);
                }
            });
        } catch (TimeoutException e) {
            return Diagnosis.of(lines, HttpRoute.unavailable(REASON_TIMEOUT, timeoutDetail()), null);
        } catch (Exception e) {
            return Diagnosis.of(lines, HttpRoute.unavailable(REASON_RESOLVER_FAILED, failureDetail(e)), null);
        }
        for (String step : diagnostics.getSteps()) {
            lines.add("- " + step);
        }
        if (diagnostics.getPacUrl() != null) {
            lines.add("PAC URL: " + diagnostics.getPacUrl()
                    + (diagnostics.getPacUrlSource() == null ? "" : " (" + diagnostics.getPacUrlSource() + ")"));
        }
        if (diagnostics.getPacScriptLength() > 0) {
            lines.add("PAC script: " + diagnostics.getPacScriptLength() + " bytes");
        }
        if (diagnostics.getFailureDetail() != null && !diagnostics.getFailureDetail().isEmpty()) {
            lines.add("Failure: " + diagnostics.getFailureDetail());
        }
        HttpRoute route = toRoute(diagnostics.getResult());
        return Diagnosis.of(lines, route, diagnostics.getDurationMillis() + " ms");
    }

    /** Konfiguration in einer Zeile für das Protokoll beim Start, ohne etwas aufzulösen. */
    public String describe() {
        StringBuilder text = new StringBuilder(config.proxyMode().name());
        switch (config.proxyMode()) {
            case MANUAL_PROXY:
                text.append(" ").append(config.proxyHost()).append(":").append(config.proxyPort());
                break;
            case PAC_URL_MANUAL:
                text.append(" ").append(config.pacUrl());
                break;
            case PAC_URL_POWERSHELL:
            case PAC_URL_WSCRIPT:
                text.append(config.pacDiscoveryScript() == null ? " (Standardskript)" : " (eigenes Skript)");
                break;
            default:
                break;
        }
        if (!config.nonProxyHosts().isEmpty()) {
            text.append(", ohne Proxy: ").append(config.nonProxyHosts());
        }
        text.append(", Frist ").append(config.resolveTimeoutMillis()).append(" ms");
        List<String> problems = validationProblems(config);
        if (!problems.isEmpty()) {
            text.append(", Konfigurationsproblem: ").append(problems);
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "HttpRoutes[" + describe() + "]";
    }

    private HttpRoute resolve(URI target) {
        final String url = target.toString();
        ProxyResult result;
        try {
            result = withTimeout(new Callable<ProxyResult>() {
                @Override
                public ProxyResult call() {
                    return resolver.resolve(url);
                }
            });
        } catch (TimeoutException e) {
            return HttpRoute.unavailable(REASON_TIMEOUT, timeoutDetail());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Proxy-Auflösung für " + key(target) + " gescheitert", e);
            return HttpRoute.unavailable(REASON_RESOLVER_FAILED, failureDetail(e));
        }
        return toRoute(result);
    }

    static HttpRoute toRoute(ProxyResult result) {
        if (result == null) {
            return HttpRoute.unavailable(REASON_RESOLVER_FAILED, "kein Ergebnis");
        }
        String reason = result.getReason() == null ? result.getKind().name().toLowerCase(Locale.ROOT)
                : result.getReason();
        switch (result.getKind()) {
            case PROXY:
                return HttpRoute.proxy(result.getHost(), result.getPort(), reason);
            case DIRECT:
                return HttpRoute.direct(reason);
            case NOT_IMPLEMENTED:
                return HttpRoute.unavailable(reason, "Modus in win-proxy-java 0.2.0 nicht umgesetzt"
                        + (result.getDetail() == null ? "" : ": " + result.getDetail()));
            default:
                return HttpRoute.unavailable(reason, result.getDetail() == null ? "" : result.getDetail());
        }
    }

    private <T> T withTimeout(Callable<T> work) throws Exception {
        Future<T> future = RESOLVERS.submit(work);
        try {
            return future.get(config.resolveTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private String timeoutDetail() {
        return "keine Antwort der Proxy-Auflösung innerhalb von " + config.resolveTimeoutMillis()
                + " ms (network.proxy.resolveTimeoutMillis)";
    }

    private static String failureDetail(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static String key(URI target) {
        String scheme = target.getScheme() == null ? "http" : target.getScheme().toLowerCase(Locale.ROOT);
        int port = target.getPort() >= 0 ? target.getPort() : "https".equals(scheme) ? 443 : 80;
        return scheme + "://" + (target.getHost() == null ? "" : target.getHost().toLowerCase(Locale.ROOT)) + ":" + port;
    }

    private static final class Entry {
        final HttpRoute route;
        final long expiresAt;

        Entry(HttpRoute route, long expiresAt) {
            this.route = route;
            this.expiresAt = expiresAt;
        }
    }

    /** Ergebnis von {@link #diagnose}: Zeilen für die Anzeige und die Route. */
    public static final class Diagnosis {
        private final List<String> lines;
        private final HttpRoute route;

        private Diagnosis(List<String> lines, HttpRoute route) {
            this.lines = Collections.unmodifiableList(new ArrayList<String>(lines));
            this.route = route;
        }

        static Diagnosis of(List<String> lines, HttpRoute route, String suffix) {
            List<String> all = new ArrayList<String>(lines);
            all.add("Result: " + route.describe() + (suffix == null ? "" : " [" + suffix + "]"));
            return new Diagnosis(all, route);
        }

        /** Alle Zeilen; die erste {@code Resolving <url> ...}, die letzte {@code Result: ...}. */
        public List<String> lines() {
            return lines;
        }

        public HttpRoute route() {
            return route;
        }

        public String text() {
            StringBuilder text = new StringBuilder();
            for (String line : lines) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(line);
            }
            return text.toString();
        }
    }
}
