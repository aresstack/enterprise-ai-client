package com.aresstack.enterpriseai.chat.api;

/**
 * Werkzeugfähige Antworten (Tool-Calling) über einen eigenen Endpunkt, getrennt vom gestreamten Chat
 * ({@link ChatCompletionPort}). Blockiert bis zur vollständigen Antwort; ausführen und weiterreichen von
 * Werkzeugaufrufen ist Sache des Aufrufers.
 */
public interface ResponsesPort {

    /** @throws ChatCompletionException bei Transport-, Protokoll- oder Serverfehlern (Meldung ohne Secrets) */
    ResponsesResult create(ResponsesRequest request);
}
