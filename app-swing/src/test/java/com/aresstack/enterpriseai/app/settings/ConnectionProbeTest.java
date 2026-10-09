package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.net.ProxyPolicy;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep.Status;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Der Verbindungstest geht gegen einen lokalen HTTPS-Server mit selbstsigniertem Zertifikat (Testressourcen von
 * {@code app.net}) denselben Weg wie der Start und meldet jeden Schritt; der API-Key wandert nur in den Header,
 * nie in eine Meldung.
 */
public class ConnectionProbeTest {

    private static final String RESOURCES = "/com/aresstack/enterpriseai/app/net/";
    private static final String TOKEN = "test-token";
    private static final String MODELS = "{\"object\":\"list\",\"data\":[{\"id\":\"test-chat\",\"object\":\"model\"},"
            + "{\"id\":\"other-model\",\"object\":\"model\"}]}";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private HttpsServer server;
    private Path serverCertificate;
    private volatile int responseCode = 200;
    private volatile String responseBody = MODELS;
    private volatile String location;
    private final List<String> authorizations = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> paths = Collections.synchronizedList(new ArrayList<String>());

    @Before
    public void startServer() throws Exception {
        serverCertificate = temp.getRoot().toPath().resolve("localhost-cert.pem");
        try (InputStream in = ConnectionProbeTest.class.getResourceAsStream(RESOURCES + "localhost-cert.pem")) {
            assertNotNull("Testressource localhost-cert.pem fehlt", in);
            Files.copy(in, serverCertificate);
        }
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream in = ConnectionProbeTest.class.getResourceAsStream(RESOURCES + "localhost.jks")) {
            assertNotNull("Testressource localhost.jks fehlt", in);
            keyStore.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, "changeit".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keys.getKeyManagers(), null, null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                paths.add(exchange.getRequestURI().getPath());
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (location != null) {
                    exchange.getResponseHeaders().add("Location", location);
                }
                exchange.sendResponseHeaders(responseCode, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    OutputStream out = exchange.getResponseBody();
                    out.write(body);
                    out.close();
                } else {
                    exchange.close();
                }
            }
        });
        server.start();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "https://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private Properties properties(String baseUrl, boolean keePass, boolean trustServer) {
        Properties p = new Properties();
        p.setProperty("chat.baseUrl", baseUrl);
        p.setProperty("chat.model", "test-chat");
        p.setProperty("chat.apiKeyRef", "keepass:Enterprise AI API");
        p.setProperty("chat.connectTimeoutMillis", "5000");
        p.setProperty("chat.readTimeoutMillis", "5000");
        p.setProperty("embedding.model", "test-embedding");
        p.setProperty("embedding.dimension", "8");
        p.setProperty("security.keepass.enabled", String.valueOf(keePass));
        p.setProperty("network.proxy.mode", "NONE");
        if (trustServer) {
            p.setProperty("network.tls.caCertificatesFile", serverCertificate.toString());
        }
        return p;
    }

    private static AppConfig config(Properties properties) {
        return AppConfigLoader.fromProperties(properties);
    }

    private static ConnectionProbe.TokenLookup token(final String value) {
        return new ConnectionProbe.TokenLookup() {
            @Override
            public String token() {
                return value;
            }
        };
    }

    private static ConnectionProbe.TokenLookup failing(final String message) {
        return new ConnectionProbe.TokenLookup() {
            @Override
            public String token() throws IOException {
                throw new IOException(message);
            }
        };
    }

    private static List<ConnectionCheckStep> run(ConnectionProbe probe, boolean expectedSuccess) {
        final List<ConnectionCheckStep> steps = new ArrayList<ConnectionCheckStep>();
        boolean success = probe.run(new Consumer<ConnectionCheckStep>() {
            @Override
            public void accept(ConnectionCheckStep step) {
                steps.add(step);
            }
        });
        assertEquals("Ergebnis " + steps, expectedSuccess, success);
        for (ConnectionCheckStep step : steps) {
            assertFalse("Token in Meldung: " + step, step.detail().contains(TOKEN));
        }
        return steps;
    }

    private static String titles(List<ConnectionCheckStep> steps) {
        List<String> titles = new ArrayList<String>();
        for (ConnectionCheckStep step : steps) {
            titles.add(step.title());
        }
        return titles.toString();
    }

    private static ConnectionCheckStep last(List<ConnectionCheckStep> steps) {
        return steps.get(steps.size() - 1);
    }

    private static void assertStatus(Status expected, ConnectionCheckStep step) {
        assertEquals(step.toString(), expected, step.status());
    }

    @Test
    public void everyStepPassesAgainstTheLocalServerAndTheKeyTravelsOnlyInTheHeader() {
        ConnectionProbe probe = new ConnectionProbe(config(properties(baseUrl(), true, true)), token(" " + TOKEN + " "));
        List<ConnectionCheckStep> steps = run(probe, true);

        assertEquals("[Proxy-Route, Namensauflösung, API-Key, Verbindung und TLS, GET /models]", titles(steps));
        for (ConnectionCheckStep step : steps) {
            assertStatus(Status.OK, step);
        }
        assertTrue(steps.get(0).detail(), steps.get(0).detail().contains("direkt (Loopback)"));
        assertTrue(steps.get(0).detail(), steps.get(0).detail().contains(baseUrl() + "/models"));
        assertTrue(steps.get(1).detail(), steps.get(1).detail().contains("127.0.0.1"));
        assertTrue(steps.get(2).detail(), steps.get(2).detail().contains("Enterprise AI API"));
        assertTrue(steps.get(3).detail(), steps.get(3).detail().contains("localhost"));
        assertTrue(steps.get(3).detail(), steps.get(3).detail().contains("Vertrauensquellen"));
        assertTrue(steps.get(4).detail(), steps.get(4).detail().contains("test-chat"));
        assertEquals("[/v1/models]", paths.toString());
        assertEquals("[Bearer " + TOKEN + "]", authorizations.toString());
    }

    @Test
    public void aMissingModelIsOnlyAWarning() {
        responseBody = "{\"data\":[{\"id\":\"alpha\"},{\"id\":\"beta\"}]}";
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), true, true)), token(TOKEN)),
                true);
        assertStatus(Status.WARNING, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("test-chat"));
        assertTrue(last(steps).detail(), last(steps).detail().contains("alpha, beta"));
    }

    @Test
    public void withoutKeePassTheCallGoesWithoutKeyAndAnAuthenticationDemandCountsAsReachable() {
        responseCode = 401;
        responseBody = "{\"error\":\"missing key\"}";
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), false, true)), token(TOKEN)),
                true);
        assertStatus(Status.INFO, steps.get(2));
        assertTrue(steps.get(2).detail(), steps.get(2).detail().contains("ausgeschaltet"));
        assertStatus(Status.OK, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("401"));
        assertNull(authorizations.get(0));
    }

    @Test
    public void aRejectedKeyFailsTheCall() {
        responseCode = 401;
        responseBody = "{\"error\":\"invalid key\"}";
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), true, true)), token(TOKEN)),
                false);
        assertStatus(Status.FAILED, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("API-Key ab"));
        assertTrue(last(steps).detail(), last(steps).detail().contains("invalid key"));
    }

    @Test
    public void aFailingKeyLookupIsAWarningAndTheCallGoesOnWithoutKey() {
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), true, true)),
                failing("KeePass ist unter 127.0.0.1:12546 nicht erreichbar.")), true);
        assertStatus(Status.WARNING, steps.get(2));
        assertTrue(steps.get(2).detail(), steps.get(2).detail().contains("12546 nicht erreichbar"));
        assertTrue(steps.get(2).detail(), steps.get(2).detail().contains("ohne API-Key"));
        assertStatus(Status.OK, last(steps));
        assertNull(authorizations.get(0));
    }

    @Test
    public void anUnknownHostStopsAtNameResolutionWithTheHint() {
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(
                config(properties("https://host.invalid/v1", false, true)), token(null)), false);
        assertEquals("[Proxy-Route, Namensauflösung]", titles(steps));
        assertStatus(Status.FAILED, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("host.invalid"));
        assertTrue(last(steps).detail(), last(steps).detail().contains("UnknownHostException"));
        assertTrue(last(steps).detail(), last(steps).detail().contains("Hinweis:"));
    }

    @Test
    public void anUntrustedCertificateFailsTheTlsStep() {
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), false, false)), token(null)),
                false);
        assertEquals("[Proxy-Route, Namensauflösung, API-Key, Verbindung und TLS]", titles(steps));
        assertStatus(Status.FAILED, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("vertraut"));
        assertTrue("Kein Aufruf ohne Handshake", paths.isEmpty());
    }

    @Test
    public void aManualProxyIsResolvedInsteadOfTheTarget() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        Properties p = properties("https://host.invalid/v1", false, true);
        p.setProperty("network.proxy.mode", "MANUAL");
        p.setProperty("network.proxy.host", "127.0.0.1");
        p.setProperty("network.proxy.port", String.valueOf(closedPort));
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(p), token(null)), false);

        assertEquals("[Proxy-Route, Namensauflösung, API-Key, Verbindung und TLS]", titles(steps));
        assertStatus(Status.OK, steps.get(0));
        assertTrue(steps.get(0).detail(), steps.get(0).detail().contains("PROXY 127.0.0.1:" + closedPort + " (MANUAL)"));
        assertStatus(Status.OK, steps.get(1));
        assertTrue(steps.get(1).detail(), steps.get(1).detail().startsWith("127.0.0.1 -> 127.0.0.1"));
        assertTrue(steps.get(1).detail(), steps.get(1).detail().contains("host.invalid löst der Proxy auf"));
        assertStatus(Status.FAILED, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("Hinweis:"));
    }

    @Test
    public void aDraftWithSystemSettingsInARunningApplicationWarnsThatTheyApplyAfterARestart() {
        String before = System.getProperty("java.net.useSystemProxies");
        Properties startup = properties("https://host.invalid/v1", false, true);
        ProxyPolicy running = new ProxyPolicy(config(startup).network());
        running.install();
        try {
            System.clearProperty("java.net.useSystemProxies");
            Properties draft = properties("https://host.invalid/v1", false, true);
            draft.setProperty("network.proxy.mode", "SYSTEM");
            List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(draft), token(null)), false);
            assertStatus(Status.WARNING, steps.get(0));
            assertTrue(steps.get(0).detail(), steps.get(0).detail().contains("(SYSTEM)"));
            assertTrue(steps.get(0).detail(), steps.get(0).detail().contains("Neustart"));
            // Wie es weitergeht, hängt vom System-Selector der Test-JVM ab (direkt oder ein Proxy aus den
            // JVM-Eigenschaften); host.invalid bleibt in jedem Fall unerreichbar.
        } finally {
            running.uninstall();
            if (before == null) {
                System.clearProperty("java.net.useSystemProxies");
            } else {
                System.setProperty("java.net.useSystemProxies", before);
            }
        }
    }

    @Test
    public void notFoundPointsAtTheBaseUrlWithoutFailing() {
        responseCode = 404;
        responseBody = "";
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), false, true)), token(null)),
                true);
        assertStatus(Status.WARNING, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("404"));
        assertTrue(last(steps).detail(), last(steps).detail().contains("/v1"));
    }

    @Test
    public void aServerErrorFails() {
        responseCode = 503;
        responseBody = "<html>Service Unavailable</html>";
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), false, true)), token(null)),
                false);
        assertStatus(Status.FAILED, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("503"));
        assertTrue(last(steps).detail(), last(steps).detail().contains("Service Unavailable"));
    }

    @Test
    public void aRedirectIsReportedWithItsTargetAndMaskedTokens() {
        responseCode = 302;
        responseBody = "";
        location = "https://127.0.0.1/other/v1/models?token=Bearer%20abcdefghijklmnopqrstuvwxyz";
        List<ConnectionCheckStep> steps = run(new ConnectionProbe(config(properties(baseUrl(), false, true)), token(null)),
                true);
        assertStatus(Status.WARNING, last(steps));
        assertTrue(last(steps).detail(), last(steps).detail().contains("Umleitung nach https://127.0.0.1/other/v1/models"));
    }

    @Test
    public void helpersStripSlashesParseModelIdsAndExtractCommonNames() {
        assertEquals("https://h/v1/models", ConnectionProbe.modelsUrl(URI.create("https://h/v1//")).toString());
        assertEquals("https://h/v1/models", ConnectionProbe.modelsUrl(URI.create("https://h/v1")).toString());
        assertEquals("[a, b]", ConnectionProbe.modelIds("{\"data\":[{\"id\":\"a\"},{\"id\": \"b\"},{\"id\":\"a\"}]}")
                .toString());
        assertTrue(ConnectionProbe.modelIds("kein json").isEmpty());
        assertEquals("localhost", ConnectionProbe.commonName("CN=localhost,O=Enterprise AI Client Test"));
        assertEquals("ki intern", ConnectionProbe.commonName("O=Firma, CN=ki intern"));
        assertEquals("O=Only", ConnectionProbe.commonName("O=Only"));
        assertEquals("(leer)", ConnectionProbe.excerpt("  \n "));
        assertEquals("Bearer *** x", ConnectionProbe.excerpt("Bearer abc\n x"));
    }
}
