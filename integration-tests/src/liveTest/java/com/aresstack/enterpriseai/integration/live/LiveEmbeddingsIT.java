package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.openai.EmbeddingInputMode;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Stufen 2 und 3 der Live-Verifikation gegen den echten {@code /embeddings}-Endpunkt (laut Nachtrag UNVERIFIED).
 * Parameter: {@code -Dlive.embedding.model=…}, optional {@code -Dlive.embedding.baseUrl=…} (sonst
 * {@code live.chat.baseUrl}) und {@code -Dlive.embedding.dimension=…} (sonst aus der ersten Antwort übernommen);
 * API-Key in {@code ENTERPRISE_AI_LIVE_API_KEY}.
 *
 * <p>Stufe 2 ({@code singleInput…}): der Adapter im Standardmodus {@code SINGLE_STRING}. Stufe 3
 * ({@code arrayInput…}): Roh-Probe mit Array-Eingabe, danach, falls der Dienst Arrays annimmt, der Adapter im Modus
 * {@code ARRAY_UNVERIFIED}. Das Ergebnis von Stufe 3 ist ein Befund in der Ausgabe; ob daraus ein neuer Standard
 * wird, entscheidet der Auftraggeber.
 */
public class LiveEmbeddingsIT {

    private static final List<String> TEXTS = Arrays.asList(LiveEmbeddingSupport.TEXT_A,
            LiveEmbeddingSupport.TEXT_A_PARAPHRASE, LiveEmbeddingSupport.TEXT_B);
    private static final double SAME_VECTOR = 0.999;

    @Test
    public void singleInputReturnsOneVectorPerText() throws Exception {
        final int stage = 2;
        LiveSettings.withoutSecretLeak(() -> {
            EmbeddingEndpointProbe probe = LiveEmbeddingSupport.probe();
            int dimension = LiveEmbeddingSupport.dimension(stage, probe);
            OpenAiCompatibleEmbeddingAdapter adapter = new OpenAiCompatibleEmbeddingAdapter(
                    LiveEmbeddingSupport.configuration(dimension).inputMode(EmbeddingInputMode.SINGLE_STRING).build(),
                    LiveSettings.apiKey());

            EmbeddingBatch one = adapter.embed(Arrays.asList(LiveEmbeddingSupport.TEXT_A));
            assertEquals(1, one.size());
            assertEquals(dimension, one.get(0).dimension());

            EmbeddingBatch three = adapter.embed(TEXTS);
            assertEquals("ein Vektor je Text", TEXTS.size(), three.size());
            for (int i = 0; i < three.size(); i++) {
                assertEquals("Dimension von Vektor " + i, dimension, three.get(i).dimension());
            }
            LiveSettings.report(stage, "Adapter SINGLE_STRING: 1 Text -> 1 Vektor, 3 Texte -> 3 Vektoren, Dimension "
                    + dimension + ", Norm " + String.format(java.util.Locale.ROOT, "%.3f", one.get(0).norm()));
            LiveSettings.report(stage, "Cosinus gleicher Text (zwei Anfragen) " + LiveEmbeddingSupport.cosine(
                    one.get(0).values(), three.get(0).values()) + ", Paraphrase " + LiveEmbeddingSupport.cosine(
                    three.get(0).values(), three.get(1).values()) + ", fremdes Thema " + LiveEmbeddingSupport.cosine(
                    three.get(0).values(), three.get(2).values()));
        }, LiveSettings.API_KEY_ENV);
    }

    @Test
    public void arrayInputProbe() throws Exception {
        final int stage = 3;
        LiveSettings.withoutSecretLeak(() -> {
            EmbeddingEndpointProbe probe = LiveEmbeddingSupport.probe();
            String model = LiveEmbeddingSupport.model();

            EmbeddingEndpointProbe.Report array = probe.array(model, TEXTS);
            LiveSettings.report(stage, "Array-Eingabe mit " + TEXTS.size() + " Texten: " + array.describe());
            if (!array.isOk()) {
                LiveSettings.report(stage, "BEFUND: Array-Eingabe abgelehnt; Empfehlung: SINGLE_STRING bleibt Standard");
                return;
            }
            if (!array.usableByAdapter(TEXTS.size())) {
                LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, aber die Antwort ist für den Adapter nicht "
                        + "verwendbar (Anzahl, Form, index oder Werte); Empfehlung: SINGLE_STRING bleibt Standard");
                return;
            }

            List<float[]> singles = new ArrayList<float[]>();
            for (String text : TEXTS) {
                EmbeddingEndpointProbe.Report single = probe.single(model, text);
                assertTrue("Einzeltext-Antwort nicht verwendbar: " + single.describe(), single.usableByAdapter(1));
                singles.add(single.vectors().get(0));
            }
            boolean ordered = compareWithSingles(stage, "Roh-Probe", array.vectors(), singles);
            if (!ordered) {
                LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, Reihenfolge gegenüber Einzelanfragen NICHT "
                        + "bestätigt; Empfehlung: SINGLE_STRING bleibt Standard, Ergebnis an Strang C");
                return;
            }

            int dimension = LiveEmbeddingSupport.dimension(stage, probe);
            OpenAiCompatibleEmbeddingAdapter adapter = new OpenAiCompatibleEmbeddingAdapter(
                    LiveEmbeddingSupport.configuration(dimension)
                            .inputMode(EmbeddingInputMode.ARRAY_UNVERIFIED)
                            .maxBatchSize(2)
                            .build(),
                    LiveSettings.apiKey());
            EmbeddingBatch batch = adapter.embed(TEXTS);
            assertEquals("Adapter ARRAY_UNVERIFIED: ein Vektor je Text", TEXTS.size(), batch.size());
            List<float[]> adapterVectors = new ArrayList<float[]>();
            for (int i = 0; i < batch.size(); i++) {
                assertEquals(dimension, batch.get(i).dimension());
                adapterVectors.add(batch.get(i).values());
            }
            boolean adapterOrdered = compareWithSingles(stage, "Adapter ARRAY_UNVERIFIED (Teil-Batches 2+1)",
                    adapterVectors, singles);
            assertTrue("Adapter im Array-Modus liefert andere Vektoren als Einzelanfragen", adapterOrdered);
            LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, je Eingabe ein Eintrag, Reihenfolge bestätigt; "
                    + "Empfehlung: embedding.inputMode=ARRAY_UNVERIFIED kann Standard werden (Entscheidung Auftraggeber)");
        }, LiveSettings.API_KEY_ENV);
    }

    /** Vergleicht Vektor i mit dem Einzeltext-Vektor i; meldet die Cosinus-Werte und ob alle als gleich gelten. */
    private static boolean compareWithSingles(int stage, String label, List<float[]> vectors, List<float[]> singles) {
        StringBuilder detail = new StringBuilder();
        boolean ordered = true;
        for (int i = 0; i < vectors.size(); i++) {
            double same = EmbeddingEndpointProbe.cosine(vectors.get(i), singles.get(i));
            if (i > 0) {
                detail.append(", ");
            }
            detail.append("Text ").append(i + 1).append(": ").append(String.format(java.util.Locale.ROOT, "%.4f", same));
            if (same < SAME_VECTOR) {
                ordered = false;
                int best = -1;
                double bestValue = -2;
                for (int j = 0; j < singles.size(); j++) {
                    double other = EmbeddingEndpointProbe.cosine(vectors.get(i), singles.get(j));
                    if (other > bestValue) {
                        bestValue = other;
                        best = j;
                    }
                }
                detail.append(" (am nächsten an Einzeltext ").append(best + 1).append(')');
            }
        }
        LiveSettings.report(stage, label + ", Cosinus zu den Einzelanfragen: " + detail + " -> Reihenfolge "
                + (ordered ? "bestätigt" : "NICHT bestätigt") + " (Schwelle " + SAME_VECTOR + ")");
        return ordered;
    }
}
