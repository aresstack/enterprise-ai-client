package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.config.PacDiscovery;
import com.aresstack.winproxy.ProxyConfiguration;
import com.aresstack.winproxy.ProxyResult;
import com.aresstack.winproxy.WindowsProxyResolver;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Proxy je Ziel aus dem PAC-/WPAD-Skript des Unternehmens, so wie Browser und PowerShell ihn bestimmen. Java 8
 * wertet ein solches Skript nicht aus ({@code java.net.useSystemProxies} kennt unter Windows nur den fest
 * eingetragenen Proxy); deshalb scheiterte der erste Start gegen die Enterprise-API hinter einem Firmen-Proxy,
 * obwohl derselbe Aufruf in PowerShell funktionierte.
 *
 * <p>Die Auswertung übernimmt die Bibliothek {@code com.aresstack:win-proxy-java} (Angelos Bibliothek, in
 * MainframeMate, corenth und askai-java8 im Einsatz): Adresse des Skripts aus {@code network.proxy.pacUrl} oder
 * aus den Windows-Einstellungen ({@link PacDiscovery}), Download, {@code FindProxyForURL(url, host)} mit GraalJS.
 * Diese Klasse ergänzt, was die Anwendung braucht: Ergebnisse je Ziel-Host zwischenspeichern (die erste
 * Auswertung dauert rund eine Sekunde, jede weitere Millisekunden; das Skript selbst würde je Aufruf neu geladen),
 * Fehler protokollieren und als "kein Ergebnis" ({@code null}) melden, damit {@link ProxyPolicy} explizit auf die
 * Systemeinstellungen zurückfällt. Die Bibliothek meldet Fehler nie als DIRECT; dieser Rückfall ist eine bewusste
 * Entscheidung der Anwendung und steht mit Grund im Protokoll.
 *
 * <p>Ohne {@code pacUrl} ist die Erkennung aus den Windows-Einstellungen nur unter Windows möglich; auf anderen
 * Systemen liefert {@link #resolve(URI)} immer {@code null}. Keine statischen Felder, keine Werte aus dem Skript
 * im Protokoll außer Proxy-Adresse und technischem Grund.
 */
public final class PacProxyRoutes {

    static final long SUCCESS_CACHE_MILLIS = 10 * 60 * 1000L;
    static final long FAILURE_CACHE_MILLIS = 60 * 1000L;
    /** Grund der Bibliothek, wenn Windows keine PAC-Adresse kennt: kein Fehler, nur "kein PAC". */
    static final String NO_PAC_CONFIGURED = "pac-url-not-found";

    private static final Logger LOG = Logger.getLogger(PacProxyRoutes.class.getName());

    /** Die eine Stelle, an der die Bibliothek gefragt wird (Testnaht). */
    interface RouteSource {
        ProxyResult routeFor(String targetUrl);
    }

    private static final class CachedRoute {
        final ProxyResult result;
        final long expiresAt;

        CachedRoute(ProxyResult result, long expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }
    }

    private final RouteSource source;
    private final String description;
    private final String unavailableReason;
    private final LongSupplier clock;
    private final Map<String, CachedRoute> cache = new HashMap<String, CachedRoute>();
    private String lastFailure;

    private PacProxyRoutes(RouteSource source, String description, String unavailableReason, LongSupplier clock) {
        this.source = source;
        this.description = description;
        this.unavailableReason = unavailableReason;
        this.clock = clock;
    }

    /** Aus der Konfiguration, für das laufende Betriebssystem. */
    public static PacProxyRoutes from(NetworkConfig config) {
        return from(config, System.getProperty("os.name", ""));
    }

    static PacProxyRoutes from(NetworkConfig config, String osName) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        ProxyConfiguration.Builder builder = ProxyConfiguration.builder();
        String description;
        if (config.pacUrl() != null) {
            builder.mode(com.aresstack.winproxy.ProxyMode.PAC_URL_MANUAL).pacUrl(config.pacUrl());
            description = "PAC-Skript aus network.proxy.pacUrl";
        } else if (!TrustPolicy.isWindows(osName)) {
            return new PacProxyRoutes(null, "PAC-Skript aus den Windows-Einstellungen",
                    "PAC-Erkennung aus den Windows-Einstellungen gibt es nur unter Windows; ohne network.proxy.pacUrl "
                            + "gelten die Systemeinstellungen.", systemClock());
        } else if (config.pacDiscovery() == PacDiscovery.POWERSHELL) {
            builder.mode(com.aresstack.winproxy.ProxyMode.PAC_URL_POWERSHELL);
            description = "PAC-Skript aus den Windows-Einstellungen (PowerShell)";
        } else {
            builder.mode(com.aresstack.winproxy.ProxyMode.PAC_URL_WINDOWS_SETTINGS);
            description = "PAC-Skript aus den Windows-Einstellungen (reg.exe)";
        }
        final WindowsProxyResolver resolver = new WindowsProxyResolver(builder.build());
        return new PacProxyRoutes(new RouteSource() {
            @Override
            public ProxyResult routeFor(String targetUrl) {
                return resolver.resolve(targetUrl);
            }
        }, description, null, systemClock());
    }

    /** Mit eigener Quelle und Uhr (Tests). */
    static PacProxyRoutes of(RouteSource source, String description, LongSupplier clock) {
        return new PacProxyRoutes(source, description, null, clock);
    }

    private static LongSupplier systemClock() {
        return new LongSupplier() {
            @Override
            public long getAsLong() {
                return System.currentTimeMillis();
            }
        };
    }

    /**
     * Der Proxy für ein Ziel laut PAC-Skript: ein HTTP-{@link Proxy}, {@link Proxy#NO_PROXY} für DIRECT, oder
     * {@code null}, wenn das Skript nichts entscheidet (keine PAC-Adresse bekannt, Download oder Auswertung
     * fehlgeschlagen, kein Windows); dann gelten die Systemeinstellungen.
     */
    public synchronized Proxy resolve(URI target) {
        if (target == null || target.getHost() == null) {
            return null;
        }
        ProxyResult result = resultFor(target);
        if (result == null) {
            return null;
        }
        if (result.isProxy()) {
            return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(result.getHost(), result.getPort()));
        }
        if (result.isDirect()) {
            return Proxy.NO_PROXY;
        }
        return null;
    }

    /** Für das Protokoll: Ergebnis oder Grund, ohne Anmeldedaten (ein PAC-Ergebnis enthält keine). */
    public synchronized String describe(URI target) {
        if (unavailableReason != null) {
            return unavailableReason;
        }
        ProxyResult result = target == null || target.getHost() == null ? null : resultFor(target);
        if (result == null) {
            return "keine Entscheidung (" + description + ")";
        }
        if (result.isProxy() || result.isDirect()) {
            return result + " laut " + description;
        }
        return result + ": " + description + " nicht nutzbar, es gelten die Systemeinstellungen";
    }

    /** Der zuletzt protokollierte Grund eines Fehlschlags oder {@code null}. */
    public synchronized String lastFailure() {
        return lastFailure;
    }

    private ProxyResult resultFor(URI target) {
        if (source == null) {
            return null;
        }
        String key = cacheKey(target);
        long now = clock.getAsLong();
        CachedRoute cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.result;
        }
        ProxyResult result;
        try {
            result = source.routeFor(target.toString());
        } catch (RuntimeException e) {
            result = ProxyResult.error("pac-resolver-failed: " + e.getClass().getSimpleName());
        }
        if (result == null) {
            result = ProxyResult.error("pac-resolver-returned-null");
        }
        long ttl = SUCCESS_CACHE_MILLIS;
        if (result.isProxy() || result.isDirect()) {
            LOG.info("Proxy für " + key + ": " + result + " laut " + description);
        } else if (NO_PAC_CONFIGURED.equals(result.getReason())) {
            LOG.info("Keine PAC-Adresse gefunden (" + description + "); für " + key
                    + " gelten die Systemeinstellungen.");
            lastFailure = result.getReason();
        } else {
            ttl = FAILURE_CACHE_MILLIS;
            lastFailure = result.getReason();
            LOG.warning("PAC-Auswertung für " + key + " fehlgeschlagen: " + result.getReason() + " (" + description
                    + "); es gelten die Systemeinstellungen. Nächster Versuch in " + (ttl / 1000) + " s.");
        }
        cache.put(key, new CachedRoute(result, now + ttl));
        return result;
    }

    static String cacheKey(URI target) {
        String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
        int port = target.getPort();
        if (port < 0) {
            port = "https".equals(scheme) ? 443 : "http".equals(scheme) ? 80 : -1;
        }
        return scheme + "://" + target.getHost().toLowerCase(Locale.ROOT) + (port < 0 ? "" : ":" + port);
    }

    @Override
    public String toString() {
        return description;
    }
}
