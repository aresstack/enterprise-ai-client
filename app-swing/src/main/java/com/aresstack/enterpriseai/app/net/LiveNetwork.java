package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.function.Supplier;

/**
 * Die Netzschicht, die Einstellungen ohne Neustart übernimmt: {@link #apply} baut neue {@link NetworkServices} aus
 * den gespeicherten Netzwerkwerten; Route ({@link #routes()}) und TLS-Vertrauen ({@link #tls()}) sind Funktionen,
 * die bei jeder Verbindung den aktuellen Stand fragen. Wer einmal gebaut wird (Quellen-Adapter), bekommt deshalb
 * diese beiden statt fester Werte und verbindet sich nach einer Änderung sofort über den neuen Proxy.
 */
public final class LiveNetwork {

    private volatile NetworkServices current;
    private final HttpRoutePort routes = new HttpRoutePort() {
        @Override
        public HttpRoute routeFor(URI target) {
            return current.routes().routeFor(target);
        }

        @Override
        public String toString() {
            return "LiveRoutes[" + current.routes().describe() + "]";
        }
    };
    private final SSLSocketFactory tls = new CurrentSslSocketFactory(new Supplier<SSLSocketFactory>() {
        @Override
        public SSLSocketFactory get() {
            return current.tls();
        }
    });

    public LiveNetwork(NetworkServices initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial must not be null");
        }
        this.current = initial;
    }

    /** Der aktuelle Stand; für Verbraucher, die {@link NetworkServices} je Aufruf brauchen (Sprachausgabe, Probe). */
    public NetworkServices current() {
        return current;
    }

    /** Übernimmt gespeicherte Netzwerkwerte; die nächste Verbindung läuft über die neue Route und TLS-Regel. */
    public void apply(NetworkConfig config) {
        current = NetworkServices.from(config);
    }

    /** Route je Verbindung aus dem aktuellen Stand. */
    public HttpRoutePort routes() {
        return routes;
    }

    /** TLS-Vertrauen je Verbindung aus dem aktuellen Stand. */
    public SSLSocketFactory tls() {
        return tls;
    }

    @Override
    public String toString() {
        return "LiveNetwork[" + current + "]";
    }

    /** Fragt bei jedem Verbindungsaufbau die aktuelle Factory. */
    private static final class CurrentSslSocketFactory extends SSLSocketFactory {

        private final Supplier<SSLSocketFactory> current;

        CurrentSslSocketFactory(Supplier<SSLSocketFactory> current) {
            this.current = current;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return current.get().getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return current.get().getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return current.get().createSocket();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return current.get().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return current.get().createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return current.get().createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return current.get().createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return current.get().createSocket(address, port, localAddress, localPort);
        }
    }
}
