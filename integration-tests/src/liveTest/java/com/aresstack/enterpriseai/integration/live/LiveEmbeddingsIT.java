package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;
import com.aresstack.enterpriseai.embedding.openai.EmbeddingInputMode;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * Slice B gegen den echten {@code /embeddings}-Endpunkt (laut Nachtrag UNVERIFIED). Parameter:
 * {@code -Dlive.embedding.baseUrl=… (sonst live.chat.baseUrl) -Dlive.embedding.model=… -Dlive.embedding.dimension=…},
 * API-Key in {@code ENTERPRISE_AI_LIVE_API_KEY}. Die Array-Probe ({@code -Dlive.embedding.probeArray=true})
 * beantwortet die offene Frage, ob der Dienst mehrere Texte je Anfrage annimmt.
 */
public class LiveEmbeddingsIT {

    private static BearerTokenSource apiKey() {
        final char[] key = LiveSettings.secret(LiveSettings.API_KEY_ENV);
        return () -> key.clone();
    }

    private static OpenAiCompatibleEmbeddingConfiguration.Builder configuration() {
        String baseUrl = LiveSettings.optional("live.embedding.baseUrl");
        if (baseUrl == null) {
            baseUrl = LiveSettings.required("live.chat.baseUrl");
        }
        String model = LiveSettings.required("live.embedding.model");
        int dimension = Integer.parseInt(LiveSettings.required("live.embedding.dimension"));
        return OpenAiCompatibleEmbeddingConfiguration.builder(baseUrl, model, dimension);
    }

    @Test
    public void singleStringInputReturnsAVectorOfTheConfiguredDimension() {
        OpenAiCompatibleEmbeddingConfiguration config = configuration().inputMode(EmbeddingInputMode.SINGLE_STRING).build();
        OpenAiCompatibleEmbeddingAdapter adapter = new OpenAiCompatibleEmbeddingAdapter(config, apiKey());
        EmbeddingBatch batch = adapter.embed(Arrays.asList("Die Kündigungsfrist beträgt drei Monate zum Quartalsende."));
        assertEquals(1, batch.size());
        assertEquals(config.dimension(), batch.get(0).dimension());
        System.out.println("[live] embeddings: 1 Vektor mit " + batch.get(0).dimension() + " Dimensionen");
    }

    /** UNVERIFIED laut Nachtrag: nur auf ausdrücklichen Wunsch, das Ergebnis geht an Strang C. */
    @Test
    public void arrayInputProbe() {
        assumeTrue("Array-Probe nur mit -Dlive.embedding.probeArray=true", LiveSettings.flag("live.embedding.probeArray"));
        OpenAiCompatibleEmbeddingConfiguration config = configuration()
                .inputMode(EmbeddingInputMode.ARRAY_UNVERIFIED)
                .maxBatchSize(2)
                .build();
        OpenAiCompatibleEmbeddingAdapter adapter = new OpenAiCompatibleEmbeddingAdapter(config, apiKey());
        EmbeddingBatch batch = adapter.embed(Arrays.asList("Erster Text.", "Zweiter Text."));
        assertEquals(2, batch.size());
        assertEquals(config.dimension(), batch.get(1).dimension());
        System.out.println("[live] embeddings: Array-Eingabe mit 2 Texten wurde angenommen");
    }
}
