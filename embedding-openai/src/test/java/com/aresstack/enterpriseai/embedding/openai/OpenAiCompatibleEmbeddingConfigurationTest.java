package com.aresstack.enterpriseai.embedding.openai;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class OpenAiCompatibleEmbeddingConfigurationTest {

    @Test
    public void defaults() {
        OpenAiCompatibleEmbeddingConfiguration config =
                OpenAiCompatibleEmbeddingConfiguration.builder("https://ai.intern/v1/", "e5", 768).build();
        assertEquals("https://ai.intern/v1/embeddings", config.endpoint().toString());
        assertEquals(EmbeddingInputMode.SINGLE_STRING, config.inputMode());
        assertEquals(768, config.dimension());
    }

    @Test
    public void identityIgnoresTransportButTracksModelSettings() {
        EmbeddingModelIdentity a = OpenAiCompatibleEmbeddingConfiguration.builder("https://a/v1", "e5", 768)
                .readTimeoutMillis(1).build().modelIdentity();
        EmbeddingModelIdentity b = OpenAiCompatibleEmbeddingConfiguration.builder("http://b:8080", "e5", 768)
                .inputMode(EmbeddingInputMode.ARRAY_UNVERIFIED).build().modelIdentity();
        EmbeddingModelIdentity c = OpenAiCompatibleEmbeddingConfiguration.builder("https://a/v1", "e5", 768)
                .identityAttribute("modelVersion", "2").build().modelIdentity();

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals("float", a.attributes().get("encoding"));
        assertEquals(768, a.dimension());
        assertEquals("e5", a.modelId());
    }

    @Test
    public void rejectsInvalidSettings() {
        assertRejected("", "m", 1);
        assertRejected("ftp://host/v1", "m", 1);
        assertRejected("not a url", "m", 1);
        assertRejected("https://user:secret@host/v1", "m", 1);
        assertRejected("https://host/v1?api-version=1", "m", 1);
        assertRejected("https://host/v1#frag", "m", 1);
        assertRejected("https://host/v1", " ", 1);
        assertRejected("https://host/v1", "m", 0);
        try {
            OpenAiCompatibleEmbeddingConfiguration.builder("https://h", "m", 1).maxBatchSize(0).build();
            fail("maxBatchSize 0 must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void credentialsInUrlAreNotEchoed() {
        try {
            OpenAiCompatibleEmbeddingConfiguration.builder("https://user:secret@host/v1", "m", 1).build();
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().contains("secret"));
        }
    }

    private static void assertRejected(String url, String model, int dimension) {
        try {
            OpenAiCompatibleEmbeddingConfiguration.builder(url, model, dimension).build();
            fail("expected rejection of " + url + "/" + model + "/" + dimension);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
