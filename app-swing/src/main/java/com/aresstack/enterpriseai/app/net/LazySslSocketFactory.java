package com.aresstack.enterpriseai.app.net;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * {@link SSLSocketFactory}, die ihre eigentliche Factory erst beim ersten {@code createSocket} über {@link #load}
 * baut und dann behält. Schlägt das Laden fehl, scheitert nur dieser Verbindungsaufbau mit {@link IOException};
 * der nächste versucht es erneut. Cipher-Suites kommen bis zum Laden vom JDK-Standard. So wartet weder der Start
 * noch der EDT auf PowerShell-Exporte oder Tresorzugriffe.
 */
public abstract class LazySslSocketFactory extends SSLSocketFactory {

    private final String name;
    private SSLSocketFactory loaded;

    protected LazySslSocketFactory(String name) {
        this.name = name;
    }

    /** Baut die eigentliche Factory; wird höchstens so oft gerufen, bis es einmal gelingt. */
    protected abstract SSLSocketFactory load() throws IOException;

    private synchronized SSLSocketFactory delegate() throws IOException {
        if (loaded == null) {
            loaded = load();
        }
        return loaded;
    }

    private SSLSocketFactory loadedOrDefault() {
        synchronized (this) {
            if (loaded != null) {
                return loaded;
            }
        }
        return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public final synchronized boolean isLoaded() {
        return loaded != null;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return loadedOrDefault().getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return loadedOrDefault().getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return delegate().createSocket();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return delegate().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate().createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return delegate().createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate().createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return delegate().createSocket(address, port, localAddress, localPort);
    }

    @Override
    public synchronized String toString() {
        return name + "[loaded=" + (loaded != null) + "]";
    }
}
