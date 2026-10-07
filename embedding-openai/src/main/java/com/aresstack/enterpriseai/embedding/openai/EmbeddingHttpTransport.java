package com.aresstack.enterpriseai.embedding.openai;

import java.io.IOException;
import java.net.URI;

/**
 * Schmale HTTP-Naht des Adapters: JSON per POST senden, Status und Body zurückgeben. Getrennt, damit
 * Request-Aufbau, Antwort-Parsing und Fehlerabbildung ohne Server testbar sind.
 * Übernommen aus askai-java8 ({@code EmbeddingHttpTransport}), um Bearer-Token und Status erweitert.
 */
interface EmbeddingHttpTransport {

    /**
     * @param bearerToken {@code null} oder leer: kein {@code Authorization}-Header
     * @throws IOException nur bei Transportfehlern; HTTP-Fehlerstatus kommen als {@link HttpResult}
     */
    HttpResult post(URI endpoint, String jsonBody, char[] bearerToken) throws IOException;

}
