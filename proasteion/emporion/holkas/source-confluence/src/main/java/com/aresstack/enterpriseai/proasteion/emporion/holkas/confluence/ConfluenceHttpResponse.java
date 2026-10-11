package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Antwort eines {@link ConfluenceHttpTransport}: Status, Content-Type, Körper und gegebenenfalls Weiterleitungsziel. */
public final class ConfluenceHttpResponse {

    private final int status;
    private final String contentType;
    private final byte[] body;
    private final String location;

    public ConfluenceHttpResponse(int status, String contentType, byte[] body) {
        this(status, contentType, body, null);
    }

    /** @param location {@code Location}-Header einer nicht verfolgten Weiterleitung oder {@code null} */
    public ConfluenceHttpResponse(int status, String contentType, byte[] body, String location) {
        this.status = status;
        this.contentType = contentType == null ? "" : contentType;
        this.body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        this.location = location == null || location.trim().isEmpty() ? null : location.trim();
    }

    public int status() {
        return status;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    /** Länge des Körpers in Bytes. */
    public int length() {
        return body.length;
    }

    /** Ziel einer nicht verfolgten Weiterleitung oder {@code null}. */
    public String location() {
        return location;
    }

    /** Körper als UTF-8 (Confluence liefert JSON immer in UTF-8). */
    public String bodyAsUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /** Ohne Körper: Antworten können Seiteninhalte enthalten. */
    @Override
    public String toString() {
        return "ConfluenceHttpResponse[" + status + ", " + contentType + ", " + body.length + " Bytes]";
    }
}
