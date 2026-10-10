package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.http.api.HttpRoute;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;

/**
 * Öffnet für die Diagnosen der Anwendung (Verbindungstest, HTTPS-Test) eine {@link HttpURLConnection} genau so,
 * wie es die Adapter tun: Route ausdrücklich je Verbindung (DIRECT = {@link Proxy#NO_PROXY}, PROXY = unaufgelöste
 * Proxy-Adresse, UNAVAILABLE = {@link IOException} mit dem Grund), TLS-Factory und {@code User-Agent} je
 * Verbindung, nichts prozessweit.
 */
public final class HttpConnections {

    private HttpConnections() {
    }

    public static HttpURLConnection open(URI target, HttpRoute route, SSLSocketFactory tls, String userAgent)
            throws IOException {
        URLConnection raw = target.toURL().openConnection(toProxy(target, route));
        if (!(raw instanceof HttpURLConnection)) {
            throw new IOException("not an HTTP endpoint: " + target.getHost());
        }
        HttpURLConnection connection = (HttpURLConnection) raw;
        if (tls != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(tls);
        }
        if (userAgent != null) {
            connection.setRequestProperty("User-Agent", userAgent);
        }
        return connection;
    }

    public static Proxy toProxy(URI target, HttpRoute route) throws IOException {
        if (route == null) {
            throw new IOException("no route decision for " + target.getHost());
        }
        switch (route.kind()) {
            case DIRECT:
                return Proxy.NO_PROXY;
            case PROXY:
                return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(route.proxyHost(), route.proxyPort()));
            default:
                throw new IOException("proxy route for " + target.getHost() + " unavailable: " + route.describe());
        }
    }
}
