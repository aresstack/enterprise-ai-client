package com.aresstack.enterpriseai.source.confluence;

import com.aresstack.enterpriseai.http.api.HttpRoutePort;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.util.Map;

/**
 * {@link ConfluenceHttpTransport} über {@link HttpURLConnection} (Java 8, keine Zusatzbibliothek), wie
 * MainframeMate {@code ConfluenceRestClient}.
 *
 * <p>Proxy und TLS werden von außen hereingereicht (Composition Root): die Proxy-Route je Ziel über den
 * {@link HttpRoutePort} (Vorrang) oder ein fester {@link Proxy}; die {@link SSLSocketFactory} trägt die
 * Vertrauensquellen und gegebenenfalls das Client-Zertifikat ({@link ClientCertificates}). Der Adapter liest
 * keine globalen Proxy-Einstellungen und setzt nichts prozessweit. Weiterleitungen werden nicht verfolgt, damit ein
 * {@code Authorization}-Header nie an ein anderes Ziel geht; Confluence DC leitet nicht angemeldete Zugriffe auf
 * die Login-Seite um, das meldet der Adapter als verweigerten Zugriff.
 */
public final class UrlConnectionConfluenceTransport implements ConfluenceHttpTransport {

    private final Proxy proxy;
    private final HttpRoutePort routes;
    private final SSLSocketFactory sslSocketFactory;
    private final String userAgent;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    private UrlConnectionConfluenceTransport(Builder builder) {
        this.proxy = builder.proxy;
        this.routes = builder.routes;
        this.sslSocketFactory = builder.sslSocketFactory;
        this.userAgent = builder.userAgent;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Folgt Weiterleitungen wie MainframeMate ({@code setInstanceFollowRedirects(true)}), aber nur auf demselben
     * Host und ohne Wechsel von https auf http, damit der Anmelde-Header den Server nicht verlässt; höchstens
     * fünf Schritte. Andere Weiterleitungen (z. B. auf eine Anmeldeseite) kommen als Antwort zurück.
     */
    @Override
    public ConfluenceHttpResponse get(URI uri, Map<String, String> headers, int maxBytes) throws IOException {
        URI current = uri;
        for (int hop = 0; ; hop++) {
            String[] location = new String[1];
            ConfluenceHttpResponse response = getOnce(current, headers, maxBytes, location);
            URI next = hop < 5 ? sameHostRedirect(current, response.status(), location[0]) : null;
            if (next == null) {
                return response;
            }
            current = next;
        }
    }

    static URI sameHostRedirect(URI from, int status, String location) {
        if ((status != 301 && status != 302 && status != 303 && status != 307 && status != 308)
                || location == null || location.trim().isEmpty()) {
            return null;
        }
        URI target;
        try {
            target = from.resolve(location.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (target.getHost() == null || !target.getHost().equalsIgnoreCase(from.getHost())
                || effectivePort(target) != effectivePort(from) || target.getRawUserInfo() != null) {
            return null;
        }
        if ("https".equalsIgnoreCase(from.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
            return null;
        }
        return target;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private ConfluenceHttpResponse getOnce(URI uri, Map<String, String> headers, int maxBytes, String[] location)
            throws IOException {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IOException("nur http/https unterstützt: " + scheme);
        }
        HttpURLConnection connection = open(uri);
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int status = connection.getResponseCode();
            location[0] = connection.getHeaderField("Location");
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = stream == null ? new byte[0] : readLimited(stream, maxBytes);
            return new ConfluenceHttpResponse(status, connection.getContentType(), body);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(URI uri) throws IOException {
        if (routes != null) {
            return RouteConnections.open(uri, routes, sslSocketFactory, userAgent);
        }
        HttpURLConnection connection = (HttpURLConnection) (proxy == null
                ? uri.toURL().openConnection()
                : uri.toURL().openConnection(proxy));
        if (sslSocketFactory != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
        }
        if (userAgent != null) {
            connection.setRequestProperty("User-Agent", userAgent);
        }
        return connection;
    }

    private static byte[] readLimited(InputStream stream, int maxBytes) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > maxBytes) {
                    throw new IOException("Antwort größer als " + maxBytes + " Bytes");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    @Override
    public String toString() {
        return "UrlConnectionConfluenceTransport[proxy=" + (routes != null ? routes : proxy == null ? "direkt" : proxy)
                + ", tls=" + (sslSocketFactory != null) + "]";
    }

    /** Builder; Defaults: direkte Verbindung, Standard-TLS, 15 s Verbindungs- und 60 s Lese-Timeout. */
    public static final class Builder {

        private Proxy proxy;
        private HttpRoutePort routes;
        private SSLSocketFactory sslSocketFactory;
        private String userAgent;
        private int connectTimeoutMillis = 15000;
        private int readTimeoutMillis = 60000;

        private Builder() {
        }

        /** Fester Proxy; {@code null} = direkt. Ein gesetzter {@link #routes(HttpRoutePort)} hat Vorrang. */
        public Builder proxy(Proxy value) {
            this.proxy = value;
            return this;
        }

        /** Proxy-Route je Ziel aus der Composition Root; {@code null} = {@link #proxy(Proxy)} bzw. direkt. */
        public Builder routes(HttpRoutePort value) {
            this.routes = value;
            return this;
        }

        /**
         * TLS-Fabrik mit den Vertrauensquellen der Anwendung und gegebenenfalls Client-Zertifikat, z. B.
         * {@link ClientCertificates#windowsMy(String, javax.net.ssl.X509TrustManager)}; {@code null} = JVM-Standard.
         */
        public Builder sslSocketFactory(SSLSocketFactory value) {
            this.sslSocketFactory = value;
            return this;
        }

        /** {@code User-Agent}-Header; {@code null} oder leer = JVM-Standard. */
        public Builder userAgent(String value) {
            this.userAgent = value == null || value.trim().isEmpty() ? null : value.trim();
            return this;
        }

        public Builder connectTimeoutMillis(int value) {
            this.connectTimeoutMillis = positive(value, "connectTimeoutMillis");
            return this;
        }

        public Builder readTimeoutMillis(int value) {
            this.readTimeoutMillis = positive(value, "readTimeoutMillis");
            return this;
        }

        public UrlConnectionConfluenceTransport build() {
            return new UrlConnectionConfluenceTransport(this);
        }

        private static int positive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " muss positiv sein");
            }
            return value;
        }
    }
}
