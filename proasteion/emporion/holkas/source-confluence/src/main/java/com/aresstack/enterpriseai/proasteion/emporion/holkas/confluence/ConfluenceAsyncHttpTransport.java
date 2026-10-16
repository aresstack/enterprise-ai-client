package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Asynchrone HTTP-Naht des Confluence-Adapters neben {@link ConfluenceHttpTransport}: derselbe GET mit derselben
 * Bedeutung, aber ohne den aufrufenden Thread bis zur Antwort zu belegen. Gedacht für Dienste, die viele Seiten
 * parallel laden (etwa mit Spring {@code WebClient} oder JDK {@code HttpClient.sendAsync}); der synchrone Transport
 * und die bestehende SPI bleiben unverändert.
 *
 * <p>Vertrag, gleich dem synchronen Transport:
 * <ul>
 *     <li>Die Methoden kehren zurück, ohne auf Netz-I/O zu warten. Eine Implementierung, die intern einen
 *     synchronen Aufruf blockierend ausführt ({@code block()}, {@code join()} im Aufrufer-Thread), erfüllt den
 *     Vertrag nicht.</li>
 *     <li>Fehlerstatus (4xx/5xx) sind normale Antworten. Verbindungs-, Timeout-, Routen- und Schemafehler beenden
 *     die Stufe ausnahmsweise mit einer {@link java.io.IOException}; ein zu großer Körper mit
 *     {@link ConfluenceResponseTooLargeException}. Die Methoden selbst werfen dafür nicht.</li>
 *     <li>Der Körper kommt byte-genau so, wie er nach der Transportdekodierung empfangen wurde.</li>
 *     <li>Weiterleitungen auf demselben Host (gleicher Port, ohne Userinfo, nie von https auf http) werden
 *     höchstens fünfmal verfolgt; unterwegs gesetzte Cookies gehen bei den folgenden Schritten mit und stehen in
 *     {@link ConfluenceHttpResponse#cookies()}. Andere Weiterleitungen kommen als 3xx-Antwort mit
 *     {@link ConfluenceHttpResponse#location()} zurück.</li>
 *     <li>Proxy-Route je Ziel, TLS-Client-Zertifikat und Timeouts legt die Composition Root fest. Header können ein
 *     {@code Authorization} enthalten; Implementierungen loggen Header nie.</li>
 * </ul>
 *
 * <p>Die Testfixtures enthalten {@code ConfluenceAsyncHttpTransportContract}, mit dem eine Implementierung diese
 * Punkte gegen einen lokalen Server prüft.
 */
public interface ConfluenceAsyncHttpTransport {

    /**
     * @param uri      absolute URI
     * @param headers  Request-Header (unveränderlich)
     * @param maxBytes Obergrenze für den Antwortkörper
     * @return Stufe mit der Antwort, oder ausnahmsweise beendet mit {@link java.io.IOException}
     */
    CompletionStage<ConfluenceHttpResponse> getAsync(URI uri, Map<String, String> headers, int maxBytes);

    /**
     * Wie {@link #getAsync}, aber ohne Client-Zertifikat: für Ziele außerhalb der eingestellten Confluence
     * (Weiterleitung auf einen anderen Host). Anders als {@link ConfluenceHttpTransport#getAnonymous} ohne Standard,
     * damit keine mTLS-Implementierung fremden Hosts versehentlich ihr Zertifikat zeigt.
     */
    CompletionStage<ConfluenceHttpResponse> getAnonymousAsync(URI uri, Map<String, String> headers, int maxBytes);
}
