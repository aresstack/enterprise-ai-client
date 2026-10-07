package com.aresstack.enterpriseai.embedding.openai;

import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmbeddingErrorMapperTest {

    @Test
    public void mapsStatusCodes() {
        assertEquals(EmbeddingFailureKind.AUTHENTICATION, EmbeddingErrorMapper.kindOf(401));
        assertEquals(EmbeddingFailureKind.AUTHENTICATION, EmbeddingErrorMapper.kindOf(403));
        assertEquals(EmbeddingFailureKind.RATE_LIMITED, EmbeddingErrorMapper.kindOf(429));
        assertEquals(EmbeddingFailureKind.UNAVAILABLE, EmbeddingErrorMapper.kindOf(408));
        assertEquals(EmbeddingFailureKind.REJECTED, EmbeddingErrorMapper.kindOf(400));
        assertEquals(EmbeddingFailureKind.REJECTED, EmbeddingErrorMapper.kindOf(404));
        assertEquals(EmbeddingFailureKind.REJECTED, EmbeddingErrorMapper.kindOf(422));
        assertEquals(EmbeddingFailureKind.PROVIDER_ERROR, EmbeddingErrorMapper.kindOf(500));
        assertEquals(EmbeddingFailureKind.PROVIDER_ERROR, EmbeddingErrorMapper.kindOf(503));
        assertEquals(EmbeddingFailureKind.INVALID_RESPONSE, EmbeddingErrorMapper.kindOf(302));
        assertEquals(EmbeddingFailureKind.INVALID_RESPONSE, EmbeddingErrorMapper.kindOf(204));
    }

    @Test
    public void messageCarriesStatusAndServerDetail() {
        EmbeddingException ex = EmbeddingErrorMapper.fromStatus("https://ai/v1/embeddings", 422,
                "{\"detail\":\"Input should be a valid string\"}");
        assertEquals(EmbeddingFailureKind.REJECTED, ex.kind());
        assertTrue(ex.getMessage().contains("HTTP 422"));
        assertTrue(ex.getMessage().contains("Input should be a valid string"));
    }

    @Test
    public void longOrMultilineBodiesAreShortened() {
        StringBuilder body = new StringBuilder("line1\nline2\r\n");
        for (int i = 0; i < 1000; i++) {
            body.append('x');
        }
        EmbeddingException ex = EmbeddingErrorMapper.fromStatus("e", 500, body.toString());
        assertFalse(ex.getMessage().contains("\n"));
        assertTrue(ex.getMessage().length() < EmbeddingErrorMapper.MAX_DETAIL_LENGTH + 100);
    }
}
