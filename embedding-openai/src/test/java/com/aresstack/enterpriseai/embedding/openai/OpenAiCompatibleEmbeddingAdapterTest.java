package com.aresstack.enterpriseai.embedding.openai;

import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Adapterlogik ohne Netz, über die paketinterne Transport-Naht. */
public class OpenAiCompatibleEmbeddingAdapterTest {

    private static final OpenAiCompatibleEmbeddingConfiguration CONFIG =
            OpenAiCompatibleEmbeddingConfiguration.builder("https://ai.intern/v1", "m", 2).build();

    @Test
    public void emptyInputDoesNotCallTheServer() {
        RecordingTransport transport = new RecordingTransport("{\"data\":[]}");
        new OpenAiCompatibleEmbeddingAdapter(CONFIG, tokens(), transport).embed(Collections.<String>emptyList());
        assertTrue(transport.bodies.isEmpty());
    }

    @Test
    public void tokenIsReadPerRequestAndWipedAfterwards() {
        final List<char[]> handedOut = new ArrayList<char[]>();
        BearerTokenSource source = new BearerTokenSource() {
            @Override
            public char[] bearerToken() {
                char[] token = "tok".toCharArray();
                handedOut.add(token);
                return token;
            }
        };
        RecordingTransport transport = new RecordingTransport("{\"data\":[{\"embedding\":[1,0]}]}");
        new OpenAiCompatibleEmbeddingAdapter(CONFIG, source, transport).embed(Arrays.asList("a", "b"));

        assertEquals(2, handedOut.size());
        assertEquals(Arrays.asList("tok", "tok"), transport.tokens);
        for (char[] token : handedOut) {
            assertArrayEquals(new char[] {0, 0, 0}, token);
        }
    }

    @Test
    public void transportExceptionsBecomeUnavailable() {
        EmbeddingHttpTransport failing = new EmbeddingHttpTransport() {
            @Override
            public HttpResult post(URI endpoint, String jsonBody, char[] bearerToken) throws IOException {
                throw new SocketTimeoutException("Read timed out");
            }
        };
        try {
            new OpenAiCompatibleEmbeddingAdapter(CONFIG, tokens(), failing).embed(Collections.singletonList("a"));
            fail("expected UNAVAILABLE");
        } catch (EmbeddingException expected) {
            assertEquals(EmbeddingFailureKind.UNAVAILABLE, expected.kind());
            assertTrue(expected.getMessage().contains("SocketTimeoutException"));
        }
    }

    @Test
    public void rejectsMissingCollaborators() {
        try {
            new OpenAiCompatibleEmbeddingAdapter(null, tokens());
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new OpenAiCompatibleEmbeddingAdapter(CONFIG, null);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static BearerTokenSource tokens() {
        return new BearerTokenSource() {
            @Override
            public char[] bearerToken() {
                return "tok".toCharArray();
            }
        };
    }

    private static final class RecordingTransport implements EmbeddingHttpTransport {
        private final String response;
        final List<String> bodies = new ArrayList<String>();
        final List<String> tokens = new ArrayList<String>();

        RecordingTransport(String response) {
            this.response = response;
        }

        @Override
        public HttpResult post(URI endpoint, String jsonBody, char[] bearerToken) {
            bodies.add(jsonBody);
            tokens.add(bearerToken == null ? null : new String(bearerToken));
            return new HttpResult(200, response);
        }
    }
}
