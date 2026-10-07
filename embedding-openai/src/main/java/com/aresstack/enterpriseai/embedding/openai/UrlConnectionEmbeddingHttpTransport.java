package com.aresstack.enterpriseai.embedding.openai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;

/**
 * {@link EmbeddingHttpTransport} über {@link HttpURLConnection} (nur JDK).
 *
 * <p>Header wie für {@code /chat/completions} real erprobt (Nachtrag, Abschnitte 7 und 13):
 * {@code Content-Type: application/json; charset=utf-8}, {@code Authorization: Bearer ...}, kein eigener
 * {@code Accept}-Header. Request und Response werden explizit als UTF-8 behandelt.
 * Übernommen aus askai-java8 ({@code UrlConnectionEmbeddingHttpTransport}) und MainframeMate
 * ({@code MultiProviderEmbeddingClient#httpPost}: Bearer-Header, optionaler Proxy).
 */
final class UrlConnectionEmbeddingHttpTransport implements EmbeddingHttpTransport {

    static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_ERROR_BODY_BYTES = 64 * 1024;

    private final OpenAiCompatibleEmbeddingConfiguration configuration;

    UrlConnectionEmbeddingHttpTransport(OpenAiCompatibleEmbeddingConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public HttpResult post(URI endpoint, String jsonBody, char[] bearerToken) throws IOException {
        URLConnection raw = configuration.proxy() == null
                ? endpoint.toURL().openConnection()
                : endpoint.toURL().openConnection(configuration.proxy());
        if (!(raw instanceof HttpURLConnection)) {
            throw new IOException("not an HTTP endpoint: " + endpoint);
        }
        HttpURLConnection connection = (HttpURLConnection) raw;
        try {
            connection.setConnectTimeout(configuration.connectTimeoutMillis());
            connection.setReadTimeout(configuration.readTimeoutMillis());
            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", CONTENT_TYPE);
            if (bearerToken != null && bearerToken.length > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + new String(bearerToken));
            }
            byte[] body = jsonBody.getBytes(UTF8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new HttpResult(status, readAll(in, status >= 400 ? MAX_ERROR_BODY_BYTES : Integer.MAX_VALUE));
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in, int limit) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = in.read(chunk)) != -1 && buffer.size() < limit) {
                buffer.write(chunk, 0, Math.min(read, limit - buffer.size()));
            }
        } finally {
            in.close();
        }
        return new String(buffer.toByteArray(), UTF8);
    }
}
