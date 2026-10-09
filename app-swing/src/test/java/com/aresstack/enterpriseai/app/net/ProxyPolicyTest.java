package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ProxyPolicyTest {

    private final ProxySelector original = ProxySelector.getDefault();

    @After
    public void restore() {
        ProxySelector.setDefault(original);
    }

    private static NetworkConfig network(String mode, String host, String port, String exceptions) throws Exception {
        Properties p = new Properties();
        p.setProperty("chat.baseUrl", "https://ki.example/v1");
        p.setProperty("chat.model", "m");
        p.setProperty("chat.apiKeyRef", "k");
        p.setProperty("embedding.model", "e");
        p.setProperty("embedding.dimension", "4");
        p.setProperty("security.keepass.enabled", "true");
        p.setProperty("network.proxy.mode", mode);
        if (host != null) {
            p.setProperty("network.proxy.host", host);
        }
        if (port != null) {
            p.setProperty("network.proxy.port", port);
        }
        if (exceptions != null) {
            p.setProperty("network.proxy.nonProxyHosts", exceptions);
        }
        AppConfig config = AppConfigLoader.fromProperties(p);
        return config.network();
    }

    private static final class FixedSelector extends ProxySelector {
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("system-proxy", 8080));

        @Override
        public List<Proxy> select(URI uri) {
            return Collections.singletonList(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    }

    @Test
    public void manualProxyAppliesExceptForLoopbackAndExcludedHosts() throws Exception {
        ProxyPolicy policy = new ProxyPolicy(network("MANUAL", "proxy.example", "3128", "*.intern.example, 10.*"),
                new FixedSelector());
        Proxy proxy = policy.proxyFor(URI.create("https://ki.example/v1/chat/completions"));
        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals("proxy.example", ((InetSocketAddress) proxy.address()).getHostString());
        assertEquals(3128, ((InetSocketAddress) proxy.address()).getPort());
        assertSame(Proxy.NO_PROXY, policy.proxyFor(URI.create("http://127.0.0.1:12546/")));
        assertSame(Proxy.NO_PROXY, policy.proxyFor(URI.create("http://localhost:8080/mcp")));
        assertSame(Proxy.NO_PROXY, policy.proxyFor(URI.create("https://wiki.intern.example/w/api.php")));
        assertSame(Proxy.NO_PROXY, policy.proxyFor(URI.create("https://10.1.2.3/")));
        assertSame(Proxy.NO_PROXY, policy.proxyFor(null));
    }

    @Test
    public void noneNeverProxiesAndSystemDelegates() throws Exception {
        FixedSelector system = new FixedSelector();
        ProxyPolicy none = new ProxyPolicy(network("NONE", null, null, null), system);
        assertSame(Proxy.NO_PROXY, none.proxyFor(URI.create("https://ki.example/")));
        ProxyPolicy sys = new ProxyPolicy(network("SYSTEM", null, null, "*.intern.example"), system);
        assertSame(system.proxy, sys.proxyFor(URI.create("https://ki.example/")));
        assertSame(Proxy.NO_PROXY, sys.proxyFor(URI.create("https://wiki.intern.example/")));
        assertSame(Proxy.NO_PROXY, sys.proxyFor(URI.create("http://[::1]:1234/")));
    }

    @Test
    public void installReplacesTheJvmSelectorAndUninstallRestoresIt() throws Exception {
        ProxySelector before = ProxySelector.getDefault();
        ProxyPolicy policy = new ProxyPolicy(network("NONE", null, null, null), before);
        policy.install();
        assertNotSame(before, ProxySelector.getDefault());
        assertSame(Proxy.NO_PROXY, ProxySelector.getDefault().select(URI.create("https://ki.example/")).get(0));
        policy.install();
        policy.uninstall();
        assertSame(before, ProxySelector.getDefault());
    }

    @Test
    public void systemModeSwitchesOnTheOperatingSystemProxySettingsUnlessAlreadyDecided() throws Exception {
        String before = System.getProperty(ProxyPolicy.USE_SYSTEM_PROXIES);
        try {
            System.clearProperty(ProxyPolicy.USE_SYSTEM_PROXIES);
            ProxyPolicy.defaultSelectorFor(network("NONE", null, null, null));
            assertNull("NONE lässt die Property unangetastet", System.getProperty(ProxyPolicy.USE_SYSTEM_PROXIES));
            ProxyPolicy.defaultSelectorFor(network("MANUAL", "proxy.example", "3128", null));
            assertNull(System.getProperty(ProxyPolicy.USE_SYSTEM_PROXIES));
            assertNotNull(ProxyPolicy.defaultSelectorFor(network("AUTO", null, null, null)));
            assertEquals("AUTO fällt auf die Systemeinstellungen zurück und schaltet sie ein", "true",
                    System.getProperty(ProxyPolicy.USE_SYSTEM_PROXIES));
            System.clearProperty(ProxyPolicy.USE_SYSTEM_PROXIES);
            assertNotNull(ProxyPolicy.defaultSelectorFor(network("SYSTEM", null, null, null)));
            assertEquals("true", System.getProperty(ProxyPolicy.USE_SYSTEM_PROXIES));
            System.setProperty(ProxyPolicy.USE_SYSTEM_PROXIES, "false");
            ProxyPolicy.defaultSelectorFor(network("SYSTEM", null, null, null));
            assertEquals("eine gesetzte Property gewinnt", "false", System.getProperty(ProxyPolicy.USE_SYSTEM_PROXIES));
            assertNotNull(new ProxyPolicy(network("SYSTEM", null, null, null)));
        } finally {
            if (before == null) {
                System.clearProperty(ProxyPolicy.USE_SYSTEM_PROXIES);
            } else {
                System.setProperty(ProxyPolicy.USE_SYSTEM_PROXIES, before);
            }
        }
    }

    /** Zählender Ersatz für das PAC-Skript: entscheidet für einen Host, für alle anderen nicht. */
    private static PacProxyRoutes pacDecidingOnlyFor(final String host, final com.aresstack.winproxy.ProxyResult decision) {
        return PacProxyRoutes.of(new PacProxyRoutes.RouteSource() {
            @Override
            public com.aresstack.winproxy.ProxyResult routeFor(String targetUrl) {
                return URI.create(targetUrl).getHost().equals(host) ? decision
                        : com.aresstack.winproxy.ProxyResult.error(PacProxyRoutes.NO_PAC_CONFIGURED);
            }
        }, "Test-PAC", new java.util.function.LongSupplier() {
            @Override
            public long getAsLong() {
                return 0L;
            }
        });
    }

    @Test
    public void autoAsksThePacScriptFirstAndFallsBackToTheSystemSelector() throws Exception {
        final Proxy systemProxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("system.example", 8080));
        ProxySelector system = new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(systemProxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        };
        PacProxyRoutes pac = pacDecidingOnlyFor("ki.example", com.aresstack.winproxy.ProxyResult.of("pac.example", 3128));
        ProxyPolicy policy = new ProxyPolicy(network("AUTO", null, null, "*.intern"), system, pac);

        Proxy viaPac = policy.proxyFor(URI.create("https://ki.example/v1/chat/completions"));
        assertEquals(Proxy.Type.HTTP, viaPac.type());
        assertEquals("pac.example", ((InetSocketAddress) viaPac.address()).getHostString());
        assertEquals(3128, ((InetSocketAddress) viaPac.address()).getPort());
        assertSame("ohne PAC-Entscheidung gilt die Systemeinstellung", systemProxy,
                policy.proxyFor(URI.create("https://wiki.example/w/api.php")));
        assertSame("Ausnahmen gehen vor", Proxy.NO_PROXY, policy.proxyFor(URI.create("https://ki.intern/")));
        assertSame("Loopback nie über Proxy", Proxy.NO_PROXY, policy.proxyFor(URI.create("http://127.0.0.1:8000/")));

        String route = policy.describeRoute(URI.create("https://ki.example/v1"));
        assertTrue(route, route.contains("PROXY pac.example:3128"));
        String fallback = policy.describeRoute(URI.create("https://wiki.example/"));
        assertTrue(fallback, fallback.contains("Systemeinstellungen: HTTP system.example:8080"));
        assertEquals("direkt (network.proxy.nonProxyHosts)", policy.describeRoute(URI.create("https://ki.intern/")));
        assertEquals("direkt (Loopback)", policy.describeRoute(URI.create("http://localhost/")));
        assertTrue(policy.toString(), policy.toString().contains("AUTO Test-PAC"));

        PacProxyRoutes direct = pacDecidingOnlyFor("ki.example", com.aresstack.winproxy.ProxyResult.direct("pac-direct"));
        ProxyPolicy directPolicy = new ProxyPolicy(network("AUTO", null, null, null), system, direct);
        assertSame("DIRECT des Skripts schlägt den System-Proxy", Proxy.NO_PROXY,
                directPolicy.proxyFor(URI.create("https://ki.example/")));
    }

    @Test
    public void describeRouteNamesTheOtherModes() throws Exception {
        assertEquals("direkt (NONE)", new ProxyPolicy(network("NONE", null, null, null), null)
                .describeRoute(URI.create("https://ki.example/")));
        assertEquals("PROXY proxy.example:3128 (MANUAL)", new ProxyPolicy(network("MANUAL", "proxy.example", "3128", null), null)
                .describeRoute(URI.create("https://ki.example/")));
        assertEquals("direkt (SYSTEM)", new ProxyPolicy(network("SYSTEM", null, null, null), null)
                .describeRoute(URI.create("https://ki.example/")));
        assertEquals("direkt (kein Host)", new ProxyPolicy(network("SYSTEM", null, null, null), null)
                .describeRoute(URI.create("mailto:a@b")));
    }

    @Test
    public void hostPatternsWorkLikeNonProxyHosts() {
        assertTrue(ProxyPolicy.matches("wiki.intern.example", "*.intern.example"));
        assertTrue(ProxyPolicy.matches("10.1.2.3", "10.*"));
        assertTrue(ProxyPolicy.matches("ki.example", "ki.example"));
        assertTrue(ProxyPolicy.matches("a.intern.b", "*intern*"));
        assertTrue(!ProxyPolicy.matches("ki.example", "*.intern.example"));
        assertTrue(ProxyPolicy.isLoopback("127.0.0.1"));
        assertTrue(ProxyPolicy.isLoopback("LOCALHOST"));
        assertTrue(ProxyPolicy.isLoopback(null));
    }
}
