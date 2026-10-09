package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.winproxy.ProxyResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.LongSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * PAC-Auswertung mit win-proxy-java gegen ein lokales Skript (Sentinel-Proxy aus dem RFC-5737-Dokumentationsnetz,
 * damit das Ergebnis nur aus dem Skript stammen kann), dazu Cache, Fehlerbehandlung und der Rückfall auf die
 * Systemeinstellungen als {@code null}.
 */
public class PacProxyRoutesTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final URI CHAT = URI.create("https://ki.example/v1/chat/completions");
    private static final URI WIKI = URI.create("https://wiki.example.test/w/api.php");

    private NetworkConfig autoWithPac(String pacUrl) throws Exception {
        Properties p = new Properties();
        p.setProperty("chat.baseUrl", "https://ki.example/v1");
        p.setProperty("chat.model", "m");
        p.setProperty("chat.apiKeyRef", "k");
        p.setProperty("embedding.model", "e");
        p.setProperty("embedding.dimension", "4");
        p.setProperty("security.keepass.enabled", "true");
        p.setProperty("network.proxy.mode", "AUTO");
        if (pacUrl != null) {
            p.setProperty("network.proxy.pacUrl", pacUrl);
        }
        return AppConfigLoader.fromProperties(p).network();
    }

    private String sentinelPac() throws Exception {
        Path file = temp.getRoot().toPath().resolve("sentinel.pac");
        try (InputStream in = PacProxyRoutesTest.class.getResourceAsStream("sentinel.pac")) {
            assertNotNull("Testressource sentinel.pac fehlt", in);
            Files.copy(in, file);
        }
        return file.toUri().toString();
    }

    @Test
    public void evaluatesTheConfiguredPacScriptPerTargetHost() throws Exception {
        PacProxyRoutes routes = PacProxyRoutes.from(autoWithPac(sentinelPac()), "Linux");
        Proxy chat = routes.resolve(CHAT);
        assertNotNull(chat);
        assertEquals(Proxy.Type.HTTP, chat.type());
        InetSocketAddress address = (InetSocketAddress) chat.address();
        assertEquals("192.0.2.123", address.getHostString());
        assertEquals(18080, address.getPort());
        assertSame("DIRECT laut Skript", Proxy.NO_PROXY, routes.resolve(WIKI));
        String described = routes.describe(CHAT);
        assertTrue(described, described.contains("PROXY 192.0.2.123:18080"));
        assertTrue(described, described.contains("network.proxy.pacUrl"));
        assertNull(routes.lastFailure());
        assertTrue(routes.toString().contains("network.proxy.pacUrl"));
    }

    @Test
    public void unreachablePacScriptYieldsNoDecisionAndNamesTheReason() throws Exception {
        PacProxyRoutes routes = PacProxyRoutes.from(
                autoWithPac(temp.getRoot().toPath().resolve("fehlt.pac").toUri().toString()), "Windows 11");
        assertNull("ohne Skript entscheidet die Systemeinstellung", routes.resolve(CHAT));
        assertEquals("pac-download-failed", routes.lastFailure());
        String described = routes.describe(CHAT);
        assertTrue(described, described.contains("pac-download-failed"));
        assertTrue(described, described.contains("Systemeinstellungen"));
    }

    @Test
    public void windowsDiscoveryIsNotAttemptedOutsideWindows() throws Exception {
        PacProxyRoutes routes = PacProxyRoutes.from(autoWithPac(null), "Linux");
        assertNull(routes.resolve(CHAT));
        assertTrue(routes.describe(CHAT), routes.describe(CHAT).contains("nur unter Windows"));
        assertNull(routes.lastFailure());
        PacProxyRoutes windows = PacProxyRoutes.from(autoWithPac(null), "Windows 10");
        assertTrue(windows.toString(), windows.toString().contains("reg.exe"));
        Properties p = new Properties();
        p.setProperty("chat.baseUrl", "https://ki.example/v1");
        p.setProperty("chat.model", "m");
        p.setProperty("chat.apiKeyRef", "k");
        p.setProperty("embedding.model", "e");
        p.setProperty("embedding.dimension", "4");
        p.setProperty("security.keepass.enabled", "true");
        p.setProperty("network.proxy.pacDiscovery", "POWERSHELL");
        assertTrue(PacProxyRoutes.from(AppConfigLoader.fromProperties(p).network(), "Windows 10").toString()
                .contains("PowerShell"));
    }

    /** Zählende Quelle statt Bibliothek: Cache je Host, Ablauf, Fehler kürzer gecacht, Ausnahmen gefangen. */
    private static final class CountingSource implements PacProxyRoutes.RouteSource {
        final List<String> calls = new ArrayList<String>();
        ProxyResult next = ProxyResult.of("proxy.example", 3128);
        RuntimeException failure;

        @Override
        public ProxyResult routeFor(String targetUrl) {
            calls.add(targetUrl);
            if (failure != null) {
                throw failure;
            }
            return next;
        }
    }

    private static final class FakeClock implements LongSupplier {
        long now = 1000000L;

        @Override
        public long getAsLong() {
            return now;
        }
    }

    @Test
    public void resultsAreCachedPerHostUntilTheyExpire() {
        CountingSource source = new CountingSource();
        FakeClock clock = new FakeClock();
        PacProxyRoutes routes = PacProxyRoutes.of(source, "Test-PAC", clock);
        assertEquals(Proxy.Type.HTTP, routes.resolve(CHAT).type());
        assertEquals(Proxy.Type.HTTP, routes.resolve(URI.create("https://ki.example/v1/models")).type());
        assertEquals(Proxy.Type.HTTP, routes.resolve(URI.create("HTTPS://KI.EXAMPLE:443/x")).type());
        assertEquals("ein Host, eine Auswertung", 1, source.calls.size());
        assertEquals(CHAT.toString(), source.calls.get(0));
        routes.resolve(WIKI);
        assertEquals(2, source.calls.size());
        clock.now += PacProxyRoutes.SUCCESS_CACHE_MILLIS - 1;
        routes.resolve(CHAT);
        assertEquals(2, source.calls.size());
        clock.now += 2;
        routes.resolve(CHAT);
        assertEquals("nach Ablauf neu ausgewertet", 3, source.calls.size());
        assertEquals("https://ki.example:443", PacProxyRoutes.cacheKey(CHAT));
        assertEquals("http://h:80", PacProxyRoutes.cacheKey(URI.create("http://h/")));
        assertEquals("http://h:8080", PacProxyRoutes.cacheKey(URI.create("http://h:8080/")));
    }

    @Test
    public void errorsFallBackToSystemSettingsAndAreRetriedSooner() {
        CountingSource source = new CountingSource();
        FakeClock clock = new FakeClock();
        PacProxyRoutes routes = PacProxyRoutes.of(source, "Test-PAC", clock);
        source.next = ProxyResult.error("pac-evaluation-failed");
        assertNull(routes.resolve(CHAT));
        assertEquals("pac-evaluation-failed", routes.lastFailure());
        clock.now += PacProxyRoutes.FAILURE_CACHE_MILLIS - 1;
        assertNull(routes.resolve(CHAT));
        assertEquals("Fehler bleibt eine Minute gecacht", 1, source.calls.size());
        clock.now += 2;
        source.next = ProxyResult.direct("pac-direct");
        assertSame(Proxy.NO_PROXY, routes.resolve(CHAT));
        assertEquals(2, source.calls.size());

        source.next = ProxyResult.error(PacProxyRoutes.NO_PAC_CONFIGURED);
        assertNull(routes.resolve(WIKI));
        clock.now += PacProxyRoutes.FAILURE_CACHE_MILLIS + 1;
        assertNull(routes.resolve(WIKI));
        assertEquals("kein PAC ist ein stabiler Zustand und bleibt länger gecacht", 3, source.calls.size());

        source.failure = new IllegalStateException("GraalJS fehlt");
        assertNull(routes.resolve(URI.create("https://dritter.example/")));
        assertTrue(routes.lastFailure(), routes.lastFailure().startsWith("pac-resolver-failed: IllegalStateException"));
        source.failure = null;
        source.next = null;
        assertNull(routes.resolve(URI.create("https://vierter.example/")));
        assertEquals("pac-resolver-returned-null", routes.lastFailure());
        assertNull(routes.resolve(null));
        assertNull(routes.resolve(URI.create("mailto:x@example")));
    }
}
