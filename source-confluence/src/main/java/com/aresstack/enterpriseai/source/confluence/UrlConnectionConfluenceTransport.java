package com.aresstack.enterpriseai.source.confluence;

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
 * <p>Proxy und mTLS werden von außen hereingereicht (Composition Root; z. B. {@link ClientCertificates}); der
 * Adapter liest keine globalen Proxy-Einstellungen. Weiterleitungen werden nicht verfolgt, damit ein
 * {@code Authorization}-Header nie an ein anderes Ziel geht; Confluence DC leitet nicht angemeldete Zugriffe auf
 * die Login-Seite um, das meldet der Adapter als verweigerten Zugriff.
 */
public final class UrlConnectionConfluenceTransport implements ConfluenceHttpTransport {

    private final Proxy proxy;
    private final SSLSocketFactory sslSocketFactory;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    private UrlConnectionConfluenceTransport(Builder builder) {
        this.proxy = builder.proxy;
        this.sslSocketFactory = builder.sslSocketFactory;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ConfluenceHttpResponse get(URI uri, Map<String, String> headers, int maxBytes) throws IOException {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IOException("nur http/https unterstützt: " + scheme);
        }
        HttpURLConnection connection = (HttpURLConnection) (proxy == null
                ? uri.toURL().openConnection()
                : uri.toURL().openConnection(proxy));
        try {
            if (sslSocketFactory != null && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
            }
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = stream == null ? new byte[0] : readLimited(stream, maxBytes);
            return new ConfluenceHttpResponse(status, connection.getContentType(), body);
        } finally {
            connection.disconnect();
        }
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
        return "UrlConnectionConfluenceTransport[proxy=" + (proxy == null ? "direkt" : proxy)
                + ", mTLS=" + (sslSocketFactory != null) + "]";
    }

    /** Builder; Defaults: direkte Verbindung, Standard-TLS, 15 s Verbindungs- und 60 s Lese-Timeout. */
    public static final class Builder {

        private Proxy proxy;
        private SSLSocketFactory sslSocketFactory;
        private int connectTimeoutMillis = 15000;
        private int readTimeoutMillis = 60000;

        private Builder() {
        }

        /** Proxy aus der äußeren Infrastruktur-Konfiguration; {@code null} = direkt. */
        public Builder proxy(Proxy value) {
            this.proxy = value;
            return this;
        }

        /** TLS mit Client-Zertifikat, z. B. {@link ClientCertificates#windowsMy(String)}; {@code null} = Standard. */
        public Builder sslSocketFactory(SSLSocketFactory value) {
            this.sslSocketFactory = value;
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
