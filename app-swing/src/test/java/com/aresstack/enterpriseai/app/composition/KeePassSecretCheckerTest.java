package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.keepassrpc.FakeKeePassRpcServer;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import org.junit.After;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Die KeePass-Probe gegen den Fake-Server: pairt über den mitgegebenen Callback, nennt nie das Secret. */
public class KeePassSecretCheckerTest {

    private static final String SECRET = "sehr-geheimer-schluessel-4711";

    private FakeKeePassRpcServer server;

    @After
    public void stopServer() throws Exception {
        if (server != null) {
            server.stop(1000);
        }
    }

    private static KeePassConfig config(boolean enabled, int port) {
        Properties p = new Properties();
        p.setProperty("security.keepass.enabled", String.valueOf(enabled));
        p.setProperty("security.keepass.port", String.valueOf(port));
        p.setProperty("security.keepass.pairingKeyStore", "memory");
        p.setProperty("security.keepass.timeoutMillis", "5000");
        return AppConfigLoader.keePassSection(p);
    }

    private KeePassSecretChecker checkerPairingWith(final FakeKeePassRpcServer fake) {
        return new KeePassSecretChecker(new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public KeePassPairingCallback forAddress(String keePassAddress) {
                assertTrue(keePassAddress, keePassAddress.endsWith(":" + fake.boundPort()));
                return new KeePassPairingCallback() {
                    @Override
                    public char[] requestPairingPassword(String clientDisplayName) {
                        return fake.pairingPassword().toCharArray();
                    }
                };
            }
        });
    }

    @Test
    public void disabledKeePassIsReportedNotProbed() {
        SecretCheckResult result = new KeePassSecretChecker().check(config(false, 1), SecretRef.of("Enterprise AI API"));
        assertFalse(result.isSuccess());
        assertTrue(result.message(), result.message().contains("ausgeschaltet"));
    }

    @Test
    public void unreachableKeePassIsReportedWithTheAddress() throws Exception {
        int closedPort;
        ServerSocket socket = new ServerSocket(0);
        try {
            closedPort = socket.getLocalPort();
        } finally {
            socket.close();
        }
        KeePassSecretChecker checker = new KeePassSecretChecker(new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public KeePassPairingCallback forAddress(String keePassAddress) {
                return KeePassPairingCallback.unavailable();
            }
        });
        SecretCheckResult result = checker.check(config(true, closedPort), SecretRef.of("Enterprise AI API"));
        assertFalse(result.isSuccess());
        assertTrue(result.message(), result.message().contains("nicht erreichbar"));
        assertTrue(result.message(), result.message().contains(":" + closedPort));
    }

    @Test
    public void filledEntryIsFoundAndTheSecretNeverAppears() throws Exception {
        server = new FakeKeePassRpcServer().startAndWait();
        server.addEntry("Enterprise AI API", "api", SECRET, false);
        server.addEntry("Leerer Eintrag", "api", "   ", false);
        KeePassSecretChecker checker = checkerPairingWith(server);

        SecretCheckResult found = checker.check(config(true, server.boundPort()), SecretRef.of("keepass:Enterprise AI API"));
        assertTrue(found.message(), found.isSuccess());
        assertTrue(found.message(), found.message().contains("\u201eEnterprise AI API\u201c"));
        assertFalse("Präfix gehört nicht zum Titel", found.message().contains("keepass:"));
        assertFalse(found.message(), found.message().contains(SECRET));
        assertFalse(found.message(), found.message().contains(String.valueOf(SECRET.length())));

        SecretCheckResult empty = checker.check(config(true, server.boundPort()), SecretRef.of("Leerer Eintrag"));
        assertFalse(empty.isSuccess());
        assertTrue(empty.message(), empty.message().contains("leer"));

        SecretCheckResult missing = checker.check(config(true, server.boundPort()), SecretRef.of("Gibt es nicht"));
        assertFalse(missing.isSuccess());
        assertTrue(missing.message(), missing.message().contains("Kein KeePass-Eintrag"));
        assertTrue(missing.message(), missing.message().contains("Gibt es nicht"));
    }

    @Test
    public void cancelledPairingIsReported() throws Exception {
        server = new FakeKeePassRpcServer().startAndWait();
        server.addEntry("Enterprise AI API", "api", SECRET, false);
        KeePassSecretChecker checker = new KeePassSecretChecker(new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public KeePassPairingCallback forAddress(String keePassAddress) {
                return KeePassPairingCallback.unavailable();
            }
        });
        SecretCheckResult result = checker.check(config(true, server.boundPort()), SecretRef.of("Enterprise AI API"));
        assertFalse(result.isSuccess());
        assertTrue(result.message(), result.message().contains("abgebrochen"));
    }
}
