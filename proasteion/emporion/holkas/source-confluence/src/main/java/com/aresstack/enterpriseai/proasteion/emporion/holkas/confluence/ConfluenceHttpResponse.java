package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Antwort eines {@link ConfluenceHttpTransport}: Status, Content-Type, Körper und gegebenenfalls Weiterleitungsziel. */
public final class ConfluenceHttpResponse {

    private final int status;
    private final String contentType;
    private final byte[] body;
    private final String location;
    private final List<String> cookies;

    public ConfluenceHttpResponse(int status, String contentType, byte[] body) {
        this(status, contentType, body, null);
    }

    /** @param location {@code Location}-Header einer nicht verfolgten Weiterleitung oder {@code null} */
    public ConfluenceHttpResponse(int status, String contentType, byte[] body, String location) {
        this(status, contentType, body, location, Collections.<String>emptyList());
    }

    /**
     * @param cookies {@code name=wert} aus den {@code Set-Cookie}-Headern (ohne Attribute); Anmeldematerial, wird
     *                nie geloggt oder ausgegeben
     */
    public ConfluenceHttpResponse(int status, String contentType, byte[] body, String location, List<String> cookies) {
        this.status = status;
        this.contentType = contentType == null ? "" : contentType;
        this.body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        this.location = location == null || location.trim().isEmpty() ? null : location.trim();
        this.cookies = cookies == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(cookies));
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

    /** Gesetzte Cookies als {@code name=wert}; leer, wenn keine. */
    public List<String> cookies() {
        return cookies;
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
