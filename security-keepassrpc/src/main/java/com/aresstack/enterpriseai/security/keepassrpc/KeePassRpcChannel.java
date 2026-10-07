package com.aresstack.enterpriseai.security.keepassrpc;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Eine WebSocket-Verbindung zu KeePassRPC mit synchronem Anfrage/Antwort-Muster (KeePassRPC beantwortet jede
 * Nachricht mit genau einer). Nachrichteninhalte werden nie geloggt: nach dem Login sind sie verschlüsselt,
 * davor enthalten sie Pairing-Werte.
 */
final class KeePassRpcChannel implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(KeePassRpcChannel.class.getName());

    private final KeePassRpcConfig config;
    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<Object>();
    private final WebSocketClient socket;

    private KeePassRpcChannel(KeePassRpcConfig config, URI uri) {
        this.config = config;
        this.socket = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                LOG.fine("KeePassRPC-WebSocket offen");
            }

            @Override
            public void onMessage(String message) {
                inbox.add(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                inbox.add(new Closed(code, remote));
            }

            @Override
            public void onError(Exception error) {
                inbox.add(error);
            }
        };
        // KeePassRPC weist Verbindungen ohne erlaubten Origin stillschweigend ab.
        socket.addHeader("Origin", config.origin());
        socket.setConnectionLostTimeout(0);
    }

    /** Öffnet die Verbindung; KeePassRPC sendet kein Hello, der Client spricht zuerst. */
    static KeePassRpcChannel open(KeePassRpcConfig config) throws KeePassRpcException {
        URI uri;
        try {
            uri = new URI("ws", null, config.host(), config.port(), "/", null, null);
        } catch (URISyntaxException e) {
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                    "ungültige KeePassRPC-Adresse " + config.host() + ":" + config.port(), e);
        }
        KeePassRpcChannel channel = new KeePassRpcChannel(config, uri);
        boolean connected;
        try {
            connected = channel.socket.connectBlocking(config.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.close();
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE, "Verbindungsaufbau unterbrochen", e);
        }
        if (!connected) {
            Object reason = channel.inbox.poll();
            channel.close();
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                    "keine Verbindung zu KeePassRPC auf " + config.host() + ":" + config.port()
                            + " (KeePass mit KeePassRPC-Plugin gestartet?)",
                    reason instanceof Exception ? (Exception) reason : null);
        }
        return channel;
    }

    /** Sendet eine Nachricht und wartet auf die nächste Antwort. */
    String exchange(String message, String step) throws KeePassRpcException {
        inbox.clear();
        try {
            socket.send(message);
        } catch (RuntimeException e) {
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                    "Senden fehlgeschlagen bei " + step + " (Verbindung geschlossen)", e);
        }
        Object reply;
        try {
            reply = inbox.poll(config.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE, "unterbrochen bei " + step, e);
        }
        if (reply == null) {
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                    "Timeout bei " + step + " (KeePass gestartet und Datenbank geöffnet?)");
        }
        if (reply instanceof Exception) {
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                    "Verbindungsfehler bei " + step, (Exception) reply);
        }
        if (reply instanceof Closed) {
            throw new KeePassRpcException(KeePassRpcException.Kind.NOT_AVAILABLE,
                    "KeePassRPC hat die Verbindung bei " + step + " geschlossen (" + reply + ")");
        }
        return (String) reply;
    }

    boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public void close() {
        try {
            socket.closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Schließen ist best effort.
        }
    }

    private static final class Closed {

        private final int code;
        private final boolean remote;

        Closed(int code, boolean remote) {
            this.code = code;
            this.remote = remote;
        }

        @Override
        public String toString() {
            return "Code " + code + (remote ? ", vom Server" : ", vom Client");
        }
    }
}
