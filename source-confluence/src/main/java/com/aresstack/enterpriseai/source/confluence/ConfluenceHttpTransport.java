package com.aresstack.enterpriseai.source.confluence;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * HTTP-Naht des Confluence-Adapters: ein einzelner GET. Produktiv {@link UrlConnectionConfluenceTransport};
 * in Tests ein Fake oder ein lokaler HTTP-Server.
 *
 * <p>Proxy, TLS-Client-Zertifikat (mTLS) und Timeouts sind Sache der Implementierung und werden in der
 * Composition Root festgelegt; der Adapter selbst kennt nur diese Schnittstelle. Header können ein
 * {@code Authorization} enthalten; Implementierungen loggen Header nie.
 */
public interface ConfluenceHttpTransport {

    /**
     * @param uri      absolute URI
     * @param headers  Request-Header (unveränderlich)
     * @param maxBytes Obergrenze für den Antwortkörper; längere Antworten werden mit {@link IOException} abgelehnt
     * @return die Antwort; Fehlerstatus (4xx/5xx) sind normale Antworten, keine Ausnahme
     * @throws IOException bei Verbindungs-, Timeout- oder Größenfehlern
     */
    ConfluenceHttpResponse get(URI uri, Map<String, String> headers, int maxBytes) throws IOException;
}
