package com.aresstack.enterpriseai.embedding.openai;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingInputs;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link EmbeddingPort} für den {@code POST /embeddings}-Endpunkt der internen, OpenAI-/GPT-kompatiblen
 * Enterprise-API. Der einzige produktive Embedding-Adapter (kein Multi-Provider-Design).
 *
 * <p>Abbildung eines Port-Batches (siehe {@link EmbeddingInputMode}): im Default ein Request je Text, im
 * UNVERIFIED-Array-Modus ein Request je Teil-Batch von höchstens {@code maxBatchSize} Texten. In beiden Fällen
 * gilt die Port-Garantie: ein Vektor je Text in Eingabereihenfolge, alle mit {@link #modelIdentity()}.
 *
 * <p>Streng statt still: Transportfehler, HTTP-Fehler, falsche Anzahl, falsche Dimension oder nicht-endliche Werte
 * führen zu {@link EmbeddingException}; es gibt keine Zero-Vector-Fallbacks, keine Teilergebnisse und keinen
 * Wechsel auf ein anderes Modell.
 *
 * <p>Herkunft: Transport-Naht, strikte Validierung und „kein Fallback“ aus askai-java8
 * ({@code HttpEmbeddingPortAdapter}); OpenAI-Wireformat ({@code /embeddings}, {@code data[].embedding}, Bearer)
 * aus MainframeMate ({@code MultiProviderEmbeddingClient#embedOpenAIBatch}).
 */
public final class OpenAiCompatibleEmbeddingAdapter implements EmbeddingPort {

    private final OpenAiCompatibleEmbeddingConfiguration configuration;
    private final BearerTokenSource tokenSource;
    private final EmbeddingHttpTransport transport;
    private final EmbeddingModelIdentity identity;

    public OpenAiCompatibleEmbeddingAdapter(OpenAiCompatibleEmbeddingConfiguration configuration,
                                            BearerTokenSource tokenSource) {
        this(configuration, tokenSource, new UrlConnectionEmbeddingHttpTransport(requireConfiguration(configuration)));
    }

    OpenAiCompatibleEmbeddingAdapter(OpenAiCompatibleEmbeddingConfiguration configuration,
                                     BearerTokenSource tokenSource, EmbeddingHttpTransport transport) {
        this.configuration = requireConfiguration(configuration);
        if (tokenSource == null) {
            throw new IllegalArgumentException("tokenSource must not be null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.tokenSource = tokenSource;
        this.transport = transport;
        this.identity = configuration.modelIdentity();
    }

    @Override
    public EmbeddingModelIdentity modelIdentity() {
        return identity;
    }

    @Override
    public EmbeddingBatch embed(List<String> texts) {
        List<String> inputs = EmbeddingInputs.requireValid(texts);
        if (inputs.isEmpty()) {
            return EmbeddingBatch.empty(identity);
        }
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(inputs.size());
        if (configuration.inputMode() == EmbeddingInputMode.ARRAY_UNVERIFIED) {
            for (int from = 0; from < inputs.size(); from += configuration.maxBatchSize()) {
                List<String> chunk = inputs.subList(from, Math.min(inputs.size(), from + configuration.maxBatchSize()));
                vectors.addAll(call(EmbeddingRequestWriter.arrayInput(configuration.modelId(), chunk), chunk.size()));
            }
        } else {
            for (String text : inputs) {
                vectors.addAll(call(EmbeddingRequestWriter.singleInput(configuration.modelId(), text), 1));
            }
        }
        return EmbeddingBatch.of(identity, inputs.size(), vectors);
    }

    private List<EmbeddingVector> call(String requestBody, int expectedCount) {
        HttpResult result = post(requestBody);
        if (result.status != 200) {
            throw EmbeddingErrorMapper.fromStatus(configuration.endpoint().toString(), result.status, result.body);
        }
        List<float[]> matrix = EmbeddingResponseParser.parse(result.body, expectedCount);
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(matrix.size());
        for (int i = 0; i < matrix.size(); i++) {
            try {
                vectors.add(EmbeddingVector.of(identity, matrix.get(i)));
            } catch (IllegalArgumentException invalid) {
                throw new EmbeddingException(EmbeddingFailureKind.INVALID_RESPONSE,
                        "embedding " + i + " of model " + configuration.modelId() + " is invalid: "
                                + invalid.getMessage());
            }
        }
        return Collections.unmodifiableList(vectors);
    }

    private HttpResult post(String requestBody) {
        char[] token = tokenSource.bearerToken();
        try {
            return transport.post(configuration.endpoint(), requestBody, token);
        } catch (IOException ex) {
            // Nur Typ und Endpunkt: IOException-Texte können Proxy-/Host-Details, aber keine Header enthalten.
            throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "embedding request to "
                    + configuration.endpoint() + " failed: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " " + ex.getMessage()), ex);
        } finally {
            if (token != null) {
                Arrays.fill(token, '\0');
            }
        }
    }

    private static OpenAiCompatibleEmbeddingConfiguration requireConfiguration(
            OpenAiCompatibleEmbeddingConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        return configuration;
    }

    @Override
    public String toString() {
        return "OpenAiCompatibleEmbeddingAdapter{" + configuration + "}";
    }
}
