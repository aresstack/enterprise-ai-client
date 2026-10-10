package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression zum ersten Start gegen die echte API: Ein Serverzertifikat, dem der JVM-Truststore nicht vertraut,
 * wird mit einer konfigurierten CA-Datei akzeptiert; ohne sie bleibt der Fehler, nennt aber die befragten
 * Quellen. Gegen einen lokalen HTTPS-Server mit selbstsigniertem Zertifikat (Testressourcen).
 */
public class TrustPolicyTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private HttpsServer server;
    private Path serverCertificate;
    private Path otherCertificate;
    private SSLSocketFactory originalDefault;

    @Before
    public void startServer() throws Exception {
        originalDefault = HttpsURLConnection.getDefaultSSLSocketFactory();
        serverCertificate = copyResource("localhost-cert.pem");
        otherCertificate = copyResource("other-ca.pem");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream in = TrustPolicyTest.class.getResourceAsStream("localhost.jks")) {
            assertNotNull("Testressource localhost.jks fehlt", in);
            keyStore.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, "changeit".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keys.getKeyManagers(), null, null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/v1/models", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                OutputStream out = exchange.getResponseBody();
                out.write(body);
                out.close();
            }
        });
        server.start();
    }

    @After
    public void stopServer() {
        HttpsURLConnection.setDefaultSSLSocketFactory(originalDefault);
        if (server != null) {
            server.stop(0);
        }
    }

    private Path copyResource(String name) throws IOException {
        Path target = temp.getRoot().toPath().resolve(name);
        try (InputStream in = TrustPolicyTest.class.getResourceAsStream(name)) {
            assertNotNull("Testressource " + name + " fehlt", in);
            Files.copy(in, target);
        }
        return target;
    }

    private static NetworkConfigBuilder network() {
        return new NetworkConfigBuilder();
    }

    private String url() {
        return "https://127.0.0.1:" + server.getAddress().getPort() + "/v1/models";
    }

    private static int get(String url, SSLSocketFactory factory) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        if (factory != null) {
            connection.setSSLSocketFactory(factory);
        }
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    @Test
    public void jvmTruststoreAloneRejectsTheSelfSignedServerAndNamesTheConsultedSources() throws Exception {
        TrustPolicy policy = TrustPolicy.build(true, false, false, null, "Linux");
        assertEquals(java.util.Collections.singletonList(TrustPolicy.JVM_SOURCE), policy.sources());
        try {
            get(url(), policy.socketFactory());
            fail("selbstsigniertes Zertifikat darf ohne CA-Datei nicht akzeptiert werden");
        } catch (SSLException expected) {
            String detail = ConnectionDiagnosis.detail(expected);
            assertTrue(detail, detail.contains("keiner Vertrauensquelle akzeptiert"));
            assertTrue(detail, detail.contains(TrustPolicy.JVM_SOURCE));
            assertTrue(detail, detail.toLowerCase().contains("pkix") || detail.contains("certification path"));
            assertNotNull(ConnectionDiagnosis.hint(expected));
            assertTrue(ConnectionDiagnosis.hint(expected).contains("network.tls.caCertificatesFile"));
        }
    }

    @Test
    public void configuredCaFileMakesTheServerTrustedNextToTheJvmTruststore() throws Exception {
        TrustPolicy policy = TrustPolicy.build(true, false, false, serverCertificate, "Linux");
        assertEquals(2, policy.sources().size());
        assertTrue(policy.sources().get(1), policy.sources().get(1).startsWith(TrustPolicy.FILE_SOURCE + " (1 "));
        assertEquals(200, get(url(), policy.socketFactory()));
        assertTrue("JVM-Truststore bleibt enthalten (getAcceptedIssuers nicht leer)",
                policy.trustManager().getAcceptedIssuers().length >= 1);
    }

    @Test
    public void aForeignCaFileDoesNotHelp() throws Exception {
        TrustPolicy policy = TrustPolicy.build(true, false, false, otherCertificate, "Linux");
        try {
            get(url(), policy.socketFactory());
            fail("fremde CA darf das Serverzertifikat nicht beglaubigen");
        } catch (SSLException expected) {
            assertTrue(ConnectionDiagnosis.detail(expected).contains(TrustPolicy.FILE_SOURCE));
        }
    }

    @Test
    public void clientKeyStoreFromTheJvmPropertiesStaysInTheReplacementContext() throws Exception {
        Path keyStore = temp.newFile("client.jks").toPath();
        try (InputStream in = TrustPolicyTest.class.getResourceAsStream("localhost.jks")) {
            assertNotNull("Testressource localhost.jks fehlt", in);
            Files.copy(in, keyStore, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        String previousStore = System.getProperty(TrustPolicy.KEY_STORE_PROPERTY);
        String previousPassword = System.getProperty("javax.net.ssl.keyStorePassword");
        System.setProperty(TrustPolicy.KEY_STORE_PROPERTY, keyStore.toString());
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        try {
            TrustPolicy policy = TrustPolicy.jvmOnly();
            assertEquals(1, policy.notices().size());
            assertTrue(policy.notices().get(0), policy.notices().get(0)
                    .contains(TrustPolicy.KEY_STORE_PROPERTY + ": 1 Schlüssel"));
            assertFalse("das Passwort gehört nicht in die Hinweise", policy.notices().get(0).contains("changeit"));

            System.setProperty("javax.net.ssl.keyStorePassword", "falsch");
            try {
                TrustPolicy.jvmOnly();
                fail("expected AppConfigException");
            } catch (AppConfigException e) {
                assertTrue(e.getMessage(), e.getMessage().contains(TrustPolicy.KEY_STORE_PROPERTY
                        + ": Client-Schlüsselspeicher nicht ladbar (IOException)"));
                assertFalse(e.getMessage(), e.getMessage().contains("falsch"));
            }
        } finally {
            restoreProperty(TrustPolicy.KEY_STORE_PROPERTY, previousStore);
            restoreProperty("javax.net.ssl.keyStorePassword", previousPassword);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    public void windowsStoreIsSkippedWithANoticeOutsideWindows() {
        TrustPolicy policy = TrustPolicy.build(true, true, true, null, "Linux");
        assertEquals(1, policy.sources().size());
        assertEquals(1, policy.notices().size());
        assertTrue(policy.notices().get(0), policy.notices().get(0).contains("nur unter Windows"));
        TrustPolicy quiet = TrustPolicy.build(true, false, false, null, "Windows 11");
        assertTrue(quiet.notices().isEmpty());
        assertFalse("die Bibliothek meldet ihre Quellen", quiet.diagnostics().isEmpty());
    }

    @Test
    public void windowsStoreOnWindowsIsUsedOrReportedButNeverFatal() {
        // Auf Windows-Runnern liefert SunMSCAPI den Speicher; auf anderen JDKs fehlt der KeyStore-Typ und die Regel
        // meldet das als Hinweis statt zu scheitern.
        TrustPolicy policy = TrustPolicy.build(true, true, false, null, "Windows 10");
        assertFalse(policy.sources().isEmpty());
        assertEquals(TrustPolicy.JVM_SOURCE, policy.sources().get(0));
        if (policy.sources().size() > 1) {
            assertTrue(policy.sources().get(1), policy.sources().get(1).startsWith(TrustPolicy.WINDOWS_ROOT_SOURCE));
        } else {
            assertFalse(policy.diagnostics().isEmpty());
        }
    }

    @Test
    public void pemFileWithSeveralCertificatesAndDerFileAreRead() throws Exception {
        Path bundle = temp.newFile("bundle.pem").toPath();
        Files.write(bundle, (new String(Files.readAllBytes(serverCertificate), StandardCharsets.UTF_8)
                + new String(Files.readAllBytes(otherCertificate), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        List<X509Certificate> certificates = TrustPolicy.loadCertificates(bundle);
        assertEquals(2, certificates.size());
        assertTrue(certificates.get(0).getSubjectX500Principal().getName().contains("CN=localhost"));

        Path der = temp.newFile("single.der").toPath();
        Files.write(der, certificates.get(1).getEncoded());
        assertEquals(1, TrustPolicy.loadCertificates(der).size());
        assertEquals(1, TrustPolicy.build(true, false, false, der, "Linux").sources().size() - 1);
    }

    @Test
    public void unreadableOrEmptyCaFileIsAConfigurationProblemNamingTheKeyOnly() throws Exception {
        try {
            TrustPolicy.build(true, false, false, temp.getRoot().toPath().resolve("fehlt.pem"), "Linux");
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.tls.caCertificatesFile"));
            assertFalse(e.problems().toString().contains("fehlt.pem"));
        }
        Path garbage = temp.newFile("kein-zertifikat.pem").toPath();
        Files.write(garbage, "nur Text".getBytes(StandardCharsets.UTF_8));
        try {
            TrustPolicy.build(true, false, false, garbage, "Linux");
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.tls.caCertificatesFile"));
        }
    }

    @Test
    public void fromConfigurationReadsTheTlsKeys() throws Exception {
        AppConfig config = network().caFile(serverCertificate).windowsStore(false).build();
        TrustPolicy policy = TrustPolicy.from(config.network());
        assertEquals(2, policy.sources().size());
        assertEquals(200, get(url(), policy.socketFactory()));
        assertTrue(config.network().toString(), config.network().toString().contains("tls=JVM-Truststore, CA-Datei"));
        TrustPolicy.verify(config.network());
    }

    @Test
    public void deferredFactoryBuildsOnFirstConnectionOnly() throws Exception {
        AppConfig config = network().caFile(serverCertificate).windowsStore(false).build();
        SSLSocketFactory deferred = TrustPolicy.deferred(config.network());
        assertFalse(((LazySslSocketFactory) deferred).isLoaded());
        assertEquals(200, get(url(), deferred));
        assertTrue(((LazySslSocketFactory) deferred).isLoaded());
    }

    @Test
    public void compositeRejectionCarriesTheFirstReasonAndSuppressesTheRest() throws Exception {
        TrustPolicy policy = TrustPolicy.build(true, false, false, otherCertificate, "Linux");
        X509Certificate[] chain = TrustPolicy.loadCertificates(serverCertificate).toArray(new X509Certificate[0]);
        try {
            policy.trustManager().checkServerTrusted(chain, "RSA");
            fail();
        } catch (CertificateException e) {
            assertNotNull(e.getCause());
            assertEquals(1, e.getCause().getSuppressed().length);
            assertTrue(e.getMessage(), e.getMessage().contains(TrustPolicy.FILE_SOURCE));
        }
        X509Certificate[] trusted = TrustPolicy.loadCertificates(otherCertificate).toArray(new X509Certificate[0]);
        policy.trustManager().checkServerTrusted(trusted, "RSA");
        policy.trustManager().checkClientTrusted(trusted, "RSA");
        assertEquals(HttpURLConnection.HTTP_OK, 200);
    }

    /** Kleine Konfiguration ohne Netz; nur die Netzwerk-Schlüssel variieren. */
    private static final class NetworkConfigBuilder {
        private final Properties p = new Properties();

        NetworkConfigBuilder() {
            p.setProperty("chat.baseUrl", "https://ki.example/v1");
            p.setProperty("chat.model", "m");
            p.setProperty("chat.apiKeyRef", "k");
            p.setProperty("embedding.model", "e");
            p.setProperty("embedding.dimension", "4");
            p.setProperty("security.keepass.enabled", "true");
            p.setProperty("network.proxy.mode", "DISABLED");
        }

        NetworkConfigBuilder caFile(Path file) {
            p.setProperty("network.tls.caCertificatesFile", file.toString());
            return this;
        }

        NetworkConfigBuilder windowsStore(boolean value) {
            p.setProperty("network.tls.useWindowsRoot", String.valueOf(value));
            p.setProperty("network.tls.useWindowsCaStores", String.valueOf(value));
            return this;
        }

        AppConfig build() {
            return AppConfigLoader.fromProperties(p);
        }
    }
}
