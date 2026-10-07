package com.aresstack.enterpriseai.security.keepassrpc;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Echter WebSocket-Roundtrip gegen {@link FakeKeePassRpcServer}: SRP-Pairing, Key-Challenge-Response,
 * verschlüsseltes JSON-RPC und Fehlerfälle.
 */
public class WebSocketKeePassRpcTransportTest {

    private FakeKeePassRpcServer server;
    private KeePassRpcConfig config;

    @Before
    public void startServer() throws Exception {
        server = new FakeKeePassRpcServer().startAndWait();
        server.addEntry("Confluence Prod", "alice", "pässwörd", false);
        server.addEntry("Confluence Prod Archiv", "old", "old-password", false);
        server.addEntry("Wiki", "bob", "wiki-pw", true);
        config = KeePassRpcConfig.builder().port(server.boundPort()).timeoutMillis(5000).build();
    }

    @After
    public void stopServer() throws Exception {
        server.stop(1000);
    }

    @Test
    public void pairsAndReadsAnEntryOverTheEncryptedChannel() throws Exception {
        WebSocketKeePassRpcTransport transport = new WebSocketKeePassRpcTransport(config);
        char[] key = transport.pair(name -> server.pairingPassword.toCharArray());

        assertNotNull(key);
        assertEquals(64, key.length);
        assertEquals("Client und Server müssen denselben Sitzungsschlüssel ableiten",
                server.pairedKeyOf(KeePassRpcConfig.DEFAULT_CLIENT_ID), new String(key));

        try (KeePassRpcTransport.Session session = transport.open(key)) {
            KeePassEntry entry = session.findEntryByTitle("confluence prod");
            assertEquals("Confluence Prod", entry.title());
            assertEquals("alice", entry.userName());
            assertArrayEquals("pässwörd".toCharArray(), entry.password());
            assertNull(session.findEntryByTitle("Unbekannt"));
        }
    }

    @Test
    public void readsFormFieldEntriesAndFallsBackToGetAllEntries() throws Exception {
        server.findLoginsEmpty = true;
        WebSocketKeePassRpcTransport transport = new WebSocketKeePassRpcTransport(config);
        char[] key = transport.pair(name -> server.pairingPassword.toCharArray());
        try (KeePassRpcTransport.Session session = transport.open(key)) {
            KeePassEntry entry = session.findEntryByTitle("Wiki");
            assertEquals("bob", entry.userName());
            assertArrayEquals("wiki-pw".toCharArray(), entry.password());
        }
    }

    @Test
    public void passwordsNeverTravelInPlaintextAfterLogin() throws Exception {
        WebSocketKeePassRpcTransport transport = new WebSocketKeePassRpcTransport(config);
        char[] key = transport.pair(name -> server.pairingPassword.toCharArray());
        try (KeePassRpcTransport.Session session = transport.open(key)) {
            session.findEntryByTitle("Confluence Prod");
        }
        for (String message : server.receivedMessages()) {
            assertFalse(message, message.contains("Confluence Prod"));
            assertFalse(message, message.contains(new String(key)));
            assertFalse(message, message.contains(server.pairingPassword));
        }
    }

    @Test
    public void wrongPairingPasswordIsAuthFailure() throws Exception {
        WebSocketKeePassRpcTransport transport = new WebSocketKeePassRpcTransport(config);
        try {
            transport.pair(name -> "falsch".toCharArray());
            fail();
        } catch (KeePassRpcException e) {
            assertEquals(KeePassRpcException.Kind.AUTH_FAILED, e.kind());
        }
        assertNull(server.pairedKeyOf(KeePassRpcConfig.DEFAULT_CLIENT_ID));
    }

    @Test
    public void cancelledPairingReturnsNull() throws Exception {
        assertNull(new WebSocketKeePassRpcTransport(config).pair(KeePassPairingCallback.unavailable()));
    }

    @Test
    public void callbackSeesTheConfiguredDisplayName() throws Exception {
        KeePassRpcConfig named = KeePassRpcConfig.builder().port(server.boundPort()).timeoutMillis(5000)
                .clientDisplayName("Mein Client").build();
        String[] seen = new String[1];
        new WebSocketKeePassRpcTransport(named).pair(name -> {
            seen[0] = name;
            return null;
        });
        assertEquals("Mein Client", seen[0]);
    }

    @Test
    public void revokedPairingIsAuthFailure() throws Exception {
        WebSocketKeePassRpcTransport transport = new WebSocketKeePassRpcTransport(config);
        char[] key = transport.pair(name -> server.pairingPassword.toCharArray());
        server.revokeAllPairings();
        try {
            transport.open(key);
            fail();
        } catch (KeePassRpcException e) {
            assertEquals(KeePassRpcException.Kind.AUTH_FAILED, e.kind());
        }
    }

    @Test
    public void wrongStoredKeyIsAuthFailure() throws Exception {
        WebSocketKeePassRpcTransport transport = new WebSocketKeePassRpcTransport(config);
        transport.pair(name -> server.pairingPassword.toCharArray());
        char[] wrong = new char[64];
        java.util.Arrays.fill(wrong, 'a');
        try {
            transport.open(wrong);
            fail();
        } catch (KeePassRpcException e) {
            assertEquals(KeePassRpcException.Kind.AUTH_FAILED, e.kind());
        }
    }

    @Test
    public void missingServerIsNotAvailable() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        KeePassRpcConfig nowhere = KeePassRpcConfig.builder().port(freePort).timeoutMillis(2000).build();
        try {
            new WebSocketKeePassRpcTransport(nowhere).open(new char[64]);
            fail();
        } catch (KeePassRpcException e) {
            assertEquals(KeePassRpcException.Kind.NOT_AVAILABLE, e.kind());
        }
    }

    @Test
    public void foreignOriginIsRejectedLikeKeePassRpc() throws Exception {
        KeePassRpcConfig foreign = KeePassRpcConfig.builder().port(server.boundPort()).timeoutMillis(2000)
                .origin("http://example.org").build();
        try {
            new WebSocketKeePassRpcTransport(foreign).pair(name -> server.pairingPassword.toCharArray());
            fail();
        } catch (KeePassRpcException e) {
            assertEquals(KeePassRpcException.Kind.NOT_AVAILABLE, e.kind());
        }
    }

    @Test
    public void providerPairsOnceAndReusesTheStoredKey() throws Exception {
        AtomicInteger pairings = new AtomicInteger();
        InMemoryPairingKeyStore store = new InMemoryPairingKeyStore();
        KeePassRpcSecretProvider provider = new KeePassRpcSecretProvider(config, store, name -> {
            pairings.incrementAndGet();
            return server.pairingPassword.toCharArray();
        });

        String user = provider.withSecret(SecretRef.of("keepass:Confluence Prod"), SecretMaterial::principal);
        char[] secret = provider.withSecret(SecretRef.of("Wiki"), SecretMaterial::copySecret);

        assertEquals("alice", user);
        assertArrayEquals("wiki-pw".toCharArray(), secret);
        assertEquals(1, pairings.get());
        assertNotNull(store.load());
    }

    @Test
    public void providerRepairsAfterRevocationAndReportsNotFound() throws Exception {
        AtomicInteger pairings = new AtomicInteger();
        KeePassRpcSecretProvider provider = new KeePassRpcSecretProvider(config, new InMemoryPairingKeyStore(),
                name -> {
                    pairings.incrementAndGet();
                    return server.pairingPassword.toCharArray();
                });
        provider.resolve(SecretRef.of("Wiki")).close();
        server.revokeAllPairings();
        provider.resolve(SecretRef.of("Wiki")).close();
        assertEquals(2, pairings.get());

        try {
            provider.resolve(SecretRef.of("keepass:Gibt es nicht"));
            fail();
        } catch (SecretUnavailableException e) {
            assertEquals(SecretUnavailableException.Reason.NOT_FOUND, e.reason());
            assertTrue(e.getMessage(), e.getMessage().contains("Gibt es nicht"));
        }
    }
}
