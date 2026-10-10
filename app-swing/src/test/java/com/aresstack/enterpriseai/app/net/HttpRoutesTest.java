package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.winproxy.ProxyConfiguration;
import com.aresstack.winproxy.ProxyDiagnostics;
import com.aresstack.winproxy.ProxyMode;
import com.aresstack.winproxy.ProxyResult;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Route je Ziel über win-proxy-java: Übersetzung der Ergebnisse, Ausnahmen, Cache, Frist, Diagnose. */
public class HttpRoutesTest {

    private static final URI TARGET = URI.create("https://ki.intern.example/v1/models");

    private final AtomicInteger calls = new AtomicInteger();
    private final long[] now = {1000L};

    private HttpRoutes routes(NetworkConfig config, final ProxyResult result) {
        return HttpRoutes.create(config, new HttpRoutes.Resolver() {
            @Override
            public ProxyResult resolve(String url) {
                calls.incrementAndGet();
                return result;
            }

            @Override
            public ProxyDiagnostics diagnose(String url) {
                throw new UnsupportedOperationException();
            }
        }, new HttpRoutes.Clock() {
            @Override
            public long now() {
                return now[0];
            }
        });
    }

    private static NetworkConfig pac() {
        return NetworkConfig.builder().proxyMode(ProxyMode.PAC_URL_POWERSHELL).testUrl(TARGET).build();
    }

    @Test
    public void proxyAndDirectResultsBecomeRoutes() {
        HttpRoute proxy = routes(pac(), ProxyResult.proxy("proxy.intern.example", 3128, "pac")).routeFor(TARGET);
        assertTrue(proxy.isProxy());
        assertEquals("proxy.intern.example", proxy.proxyHost());
        assertEquals(3128, proxy.proxyPort());
        assertTrue(routes(pac(), ProxyResult.direct("pac-direct")).routeFor(TARGET).isDirect());
    }

    @Test
    public void errorAndNotImplementedAreUnavailableNeverDirect() {
        HttpRoute error = routes(pac(), ProxyResult.error("pac-download-failed", "HTTP 404")).routeFor(TARGET);
        assertTrue(error.isUnavailable());
        assertEquals("pac-download-failed", error.reason());
        assertTrue(error.detail().contains("HTTP 404"));
        HttpRoute notImplemented = routes(NetworkConfig.builder()
                .proxyMode(ProxyMode.WINDOWS_NATIVE_ROUTE_RESOLVER).build(),
                ProxyResult.notImplemented("windows-native-route-resolver-not-implemented")).routeFor(TARGET);
        assertTrue(notImplemented.isUnavailable());
        assertTrue(notImplemented.describe(), notImplemented.describe().contains("nicht umgesetzt"));
    }

    @Test
    public void loopbackNonProxyHostsAndDisabledNeverAskTheResolver() {
        NetworkConfig config = NetworkConfig.builder().proxyMode(ProxyMode.PAC_URL_POWERSHELL)
                .nonProxyHosts(Arrays.asList("*.intern.example")).build();
        HttpRoutes routes = routes(config, ProxyResult.error("x"));
        assertEquals(HttpRoutes.REASON_LOOPBACK, routes.routeFor(URI.create("http://127.0.0.1:12546/")).reason());
        assertEquals(HttpRoutes.REASON_NON_PROXY_HOST, routes.routeFor(TARGET).reason());
        assertEquals(HttpRoutes.REASON_DISABLED, routes(NetworkConfig.direct(), ProxyResult.error("x"))
                .routeFor(TARGET).reason());
        assertEquals(0, calls.get());
    }

    @Test
    public void resultsAreCachedPerHostUntilTheyExpire() {
        HttpRoutes routes = routes(pac(), ProxyResult.proxy("p", 8080, "pac"));
        routes.routeFor(TARGET);
        routes.routeFor(URI.create("https://ki.intern.example/v1/chat/completions"));
        assertEquals(1, calls.get());
        now[0] += HttpRoutes.SUCCESS_TTL_MILLIS + 1;
        routes.routeFor(TARGET);
        assertEquals(2, calls.get());
        routes.invalidate();
        routes.routeFor(TARGET);
        assertEquals(3, calls.get());
    }

    @Test
    public void failuresAreCachedShorter() {
        HttpRoutes routes = routes(pac(), ProxyResult.error("e"));
        routes.routeFor(TARGET);
        now[0] += HttpRoutes.FAILURE_TTL_MILLIS + 1;
        routes.routeFor(TARGET);
        assertEquals(2, calls.get());
    }

    @Test
    public void aHangingResolverEndsAsUnavailableAfterTheTimeout() {
        NetworkConfig config = NetworkConfig.builder().proxyMode(ProxyMode.PAC_URL_POWERSHELL)
                .resolveTimeoutMillis(200).build();
        HttpRoutes routes = HttpRoutes.create(config, new HttpRoutes.Resolver() {
            @Override
            public ProxyResult resolve(String url) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ProxyResult.direct();
            }

            @Override
            public ProxyDiagnostics diagnose(String url) {
                return null;
            }
        }, new HttpRoutes.Clock() {
            @Override
            public long now() {
                return 0;
            }
        });
        long start = System.nanoTime();
        HttpRoute route = routes.routeFor(TARGET);
        assertTrue(route.isUnavailable());
        assertEquals(HttpRoutes.REASON_TIMEOUT, route.reason());
        assertTrue((System.nanoTime() - start) / 1000000L < 5000);
    }

    @Test
    public void diagnoseUsesTheLibraryAndPrintsResolvingAndResult() {
        NetworkConfig config = NetworkConfig.builder().proxyMode(ProxyMode.MANUAL_PROXY)
                .proxy("proxy.intern.example", 8080).testUrl(TARGET).build();
        HttpRoutes.Diagnosis diagnosis = HttpRoutes.from(config).diagnose(TARGET);
        assertEquals("Resolving " + TARGET + " ...", diagnosis.lines().get(0));
        String last = diagnosis.lines().get(diagnosis.lines().size() - 1);
        assertTrue(last, last.startsWith("Result: PROXY proxy.intern.example:8080"));
        assertTrue(diagnosis.route().isProxy());
    }

    @Test
    public void libraryConfigurationAlwaysCarriesOurTestUrlAndValidates() {
        ProxyConfiguration library = HttpRoutes.libraryConfiguration(pac());
        assertEquals(TARGET.toString(), library.getTestUrl());
        assertTrue(library.getPacUrlDiscoveryScript().contains("AutoConfigURL"));
        assertTrue(HttpRoutes.validationProblems(pac()).isEmpty());
        assertFalse(HttpRoutes.validationProblems(NetworkConfig.builder().proxyMode(ProxyMode.MANUAL_PROXY).build())
                .isEmpty());
        assertFalse(HttpRoutes.validationProblems(NetworkConfig.builder()
                .proxyMode(ProxyMode.WINDOWS_NATIVE_PROXY_SETTINGS).build()).isEmpty());
    }

    @Test
    public void defaultDiscoveryScriptsComeFromTheLibrary() {
        assertTrue(HttpRoutes.defaultDiscoveryScript(ProxyMode.PAC_URL_POWERSHELL).contains("AutoConfigURL"));
        assertTrue(HttpRoutes.defaultDiscoveryScript(ProxyMode.PAC_URL_WSCRIPT).contains("AutoConfigURL"));
    }
}
