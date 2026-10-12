package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Web-Sitzung einer Confluence-Quelle für Dateiabrufe (paketintern, nur im Speicher, je Quelle eine).
 *
 * <p>Ein Zwei-Faktor-Plugin kann {@code /download/...} sperren, bis der Benutzer für eine Web-Sitzung einen Einmalcode
 * bestätigt hat; REST-Aufrufe mit Basic sind davon nicht betroffen. Diese Sitzung hält die Cookies, die Confluence bei
 * Dateiabrufen und beim Absenden des Codes setzt, damit weitere Dateien ohne neue Abfrage laden. Werte verlassen die
 * Klasse nur als {@code Cookie}-Header; geloggt werden höchstens Namen. Ein Neustart beginnt ohne Sitzung.
 */
final class ConfluenceWebSession {

    private final Map<String, String> cookies = new LinkedHashMap<String, String>();
    private int generation;
    private boolean verified;

    /** Übernimmt {@code name=wert}-Paare aus {@code Set-Cookie}; leerer Wert löscht den Namen. */
    synchronized void absorb(List<String> setCookies) {
        for (String pair : setCookies) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            cookies.remove(name);
            if (!value.isEmpty()) {
                cookies.put(name, value);
            }
        }
    }

    /** Der {@code Cookie}-Header oder {@code null} ohne Cookies. */
    synchronized String cookieHeader() {
        if (cookies.isEmpty()) {
            return null;
        }
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            header.append(header.length() == 0 ? "" : "; ").append(cookie.getKey()).append('=')
                    .append(cookie.getValue());
        }
        return header.toString();
    }

    /** Namen der gehaltenen Cookies (für Logzeilen). */
    synchronized List<String> cookieNames() {
        return new ArrayList<String>(cookies.keySet());
    }

    /** Zählt jede Bestätigung; ein Abruf, der vor ihr begann, erkennt daran, dass er neu laden kann. */
    synchronized int generation() {
        return generation;
    }

    /**
     * {@code true}, solange nach einer Bestätigung die Cookies allein (ohne Authorization, wie der Browser) gereicht
     * haben oder noch nicht versucht wurden.
     */
    synchronized boolean verified() {
        return verified;
    }

    /** Der Einmalcode wurde angenommen. */
    synchronized void markVerified() {
        verified = true;
        generation++;
    }

    /** Die Cookies allein reichen nicht (etwa ohne Benutzer in der Sitzung); weiter mit Authorization und Cookies. */
    synchronized void cookiesAloneInsufficient() {
        verified = false;
    }

    @Override
    public synchronized String toString() {
        return "ConfluenceWebSession[cookies=" + cookies.keySet() + ", verified=" + verified + "]";
    }
}
