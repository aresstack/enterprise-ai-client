package com.aresstack.enterpriseai.model.sidecar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;

/** JSON-Anfragen an den lokalen Sidecar: 127.0.0.1, ohne Proxy, UTF-8. */
final class LocalSidecarHttp {

    private static final int CONNECT_TIMEOUT_MILLIS = 10000;
    private static final int MAX_ERROR_BYTES = 8 * 1024;

    private LocalSidecarHttp() {
    }

    /** Öffnet {@code POST <baseUrl><path>} und schreibt den Körper; Status und Antwort liest der Aufrufer. */
    static HttpURLConnection post(String baseUrl, String path, byte[] body, int readTimeoutMillis)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL()
                .openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestMethod("POST");
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
        } finally {
            out.close();
        }
        return connection;
    }

    /** Die Fehlermeldung des Sidecars (gekürzt) oder leer. */
    static String error(HttpURLConnection connection) {
        try {
            InputStream error = connection.getErrorStream();
            return error == null ? "" : new String(read(error, MAX_ERROR_BYTES), "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    static byte[] read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[16384];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (buffer.size() + read > limit) {
                    throw new IOException("Antwort des Sidecars zu groß");
                }
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return buffer.toByteArray();
    }
}
