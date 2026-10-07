package com.aresstack.enterpriseai.embedding.openai;

/** Status und UTF-8-dekodierter Body einer Antwort von {@link EmbeddingHttpTransport}. */
final class HttpResult {

    final int status;
    final String body;

    HttpResult(int status, String body) {
        this.status = status;
        this.body = body == null ? "" : body;
    }
}
