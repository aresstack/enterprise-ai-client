package com.aresstack.enterpriseai.embedding.openai;

import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;

/**
 * Bildet HTTP-Status auf portneutrale {@link EmbeddingFailureKind}s ab. Die Fehlermeldung enthält Status und eine
 * gekürzte Servermeldung, nie Request-Header (Bearer-Token) und nie den Request-Body (Eingabetexte).
 */
final class EmbeddingErrorMapper {

    static final int MAX_DETAIL_LENGTH = 300;

    private EmbeddingErrorMapper() {
    }

    static EmbeddingFailureKind kindOf(int status) {
        if (status == 401 || status == 403) {
            return EmbeddingFailureKind.AUTHENTICATION;
        }
        if (status == 429) {
            return EmbeddingFailureKind.RATE_LIMITED;
        }
        if (status == 408) {
            return EmbeddingFailureKind.UNAVAILABLE;
        }
        if (status >= 500) {
            return EmbeddingFailureKind.PROVIDER_ERROR;
        }
        if (status >= 400) {
            return EmbeddingFailureKind.REJECTED;
        }
        // 1xx/3xx (Redirects folgt der Adapter bewusst nicht) und unerwartete 2xx
        return EmbeddingFailureKind.INVALID_RESPONSE;
    }

    static EmbeddingException fromStatus(String endpoint, int status, String body) {
        StringBuilder message = new StringBuilder("embedding endpoint ").append(endpoint)
                .append(" returned HTTP ").append(status);
        String detail = EmbeddingResponseParser.errorMessage(body);
        if (detail == null) {
            detail = body;
        }
        detail = sanitize(detail);
        if (!detail.isEmpty()) {
            message.append(": ").append(detail);
        }
        return new EmbeddingException(kindOf(status), message.toString());
    }

    private static String sanitize(String detail) {
        if (detail == null) {
            return "";
        }
        String oneLine = detail.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (oneLine.length() > MAX_DETAIL_LENGTH) {
            return oneLine.substring(0, MAX_DETAIL_LENGTH) + "…";
        }
        return oneLine;
    }
}
