package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.openai.EmbeddingInputMode;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Stufen 2 und 3 der Live-Verifikation gegen den echten {@code /embeddings}-Endpunkt (laut Nachtrag UNVERIFIED).
 * Parameter: {@code -Dlive.embedding.model=…}, optional {@code -Dlive.embedding.baseUrl=…} (sonst
 * {@code live.chat.baseUrl}) und {@code -Dlive.embedding.dimension=…} (sonst aus der ersten Antwort übernommen);
 * API-Key in {@code ENTERPRISE_AI_LIVE_API_KEY}.
 *
 * <p>Stufe 2 ({@code singleInput…}): der Adapter im Standardmodus {@code SINGLE_STRING}. Stufe 3
 * ({@code arrayInput…}): Kontrollanfrage mit einem Text, Roh-Probe mit Array-Eingabe, danach, falls der Dienst
 * Arrays in der richtigen Reihenfolge beantwortet, der Adapter im Modus {@code ARRAY_UNVERIFIED}. Das Ergebnis von
 * Stufe 3 ist ein Befund in der Ausgabe ({@code BEFUND:}-Zeile); rot wird die Stufe, wenn der Dienst selbst nicht
 * funktioniert (Kontrollanfrage scheitert, oder die Array-Probe endet mit etwas anderem als 200 oder einem
 * Validierungsstatus). Ob aus dem Befund ein neuer Standard wird, entscheidet der Auftraggeber.
 */
public class LiveEmbeddingsIT {

    private static final List<String> TEXTS = Arrays.asList(LiveEmbeddingSupport.TEXT_A,
            LiveEmbeddingSupport.TEXT_A_PARAPHRASE, LiveEmbeddingSupport.TEXT_B);
    /** Ab diesem Cosinus gelten zwei Vektoren als identisch (derselbe Text, deterministische Antwort). */
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
                    + dimension + ", Norm " + String.format(Locale.ROOT, "%.3f", one.get(0).norm()));
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

            // Kontrolle: Ein Einzeltext muss funktionieren, sonst sagt die Array-Probe nichts über Arrays aus.
            EmbeddingEndpointProbe.Report control = probe.single(model, TEXTS.get(0));
            assertTrue("Kontrollanfrage mit einem Text nicht verwendbar: " + control.describe(),
                    control.usableByAdapter(1));
            LiveSettings.report(stage, "Kontrolle mit einem Text: " + control.describe());

            EmbeddingEndpointProbe.Report array = probe.array(model, TEXTS);
            LiveSettings.report(stage, "Array-Eingabe mit " + TEXTS.size() + " Texten: " + array.describe());
            if (!array.isOk()) {
                if (isValidationRejection(array.status())) {
                    LiveSettings.report(stage, "BEFUND: Array-Eingabe abgelehnt (HTTP " + array.status()
                            + "); Empfehlung: SINGLE_STRING bleibt Standard");
                    return;
                }
                fail("Array-Probe scheiterte am Dienst, nicht an der Eingabeform (Einzeltext ging, Array: "
                        + array.describe() + ")");
            }
            if (!array.usableByAdapter(TEXTS.size())) {
                LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, aber die Antwort ist für den Adapter nicht "
                        + "verwendbar (Anzahl, Form, index oder Werte); Empfehlung: SINGLE_STRING bleibt Standard");
                return;
            }
            if (array.dimension() != control.dimension()) {
                LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, aber Dimension " + array.dimension()
                        + " statt " + control.dimension() + " wie beim Einzeltext; nicht verwendbar; Empfehlung: "
                        + "SINGLE_STRING bleibt Standard");
                return;
            }

            List<float[]> singles = new ArrayList<float[]>();
            singles.add(control.vectors().get(0));
            for (int i = 1; i < TEXTS.size(); i++) {
                EmbeddingEndpointProbe.Report single = probe.single(model, TEXTS.get(i));
                assertTrue("Einzeltext " + (i + 1) + " nicht verwendbar: " + single.describe(), single.usableByAdapter(1));
                if (single.dimension() != array.dimension()) {
                    LiveSettings.report(stage, "BEFUND: Dimension uneinheitlich (Einzeltext " + (i + 1) + ": "
                            + single.dimension() + ", Array: " + array.dimension() + "); nicht verwendbar; Empfehlung: "
                            + "SINGLE_STRING bleibt Standard");
                    return;
                }
                singles.add(single.vectors().get(0));
            }
            Comparison raw = compareWithSingles(stage, "Roh-Probe", array.vectors(), singles);
            if (!raw.ordered) {
                LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, Reihenfolge gegenüber Einzelanfragen NICHT "
                        + "bestätigt; Empfehlung: SINGLE_STRING bleibt Standard, Ergebnis an Strang C");
                return;
            }

            // Der Adapter läuft mit der gemessenen Dimension; ein abweichender Vorgabewert wird nur gemeldet.
            int dimension = array.dimension();
            Integer configured = LiveSettings.optionalInteger("live.embedding.dimension");
            if (configured != null && configured.intValue() != dimension) {
                LiveSettings.report(stage, "Hinweis: -Dlive.embedding.dimension=" + configured + " weicht von der gemessenen "
                        + "Dimension " + dimension + " ab; der Adapter läuft hier mit " + dimension);
            }
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
            Comparison viaAdapter = compareWithSingles(stage, "Adapter ARRAY_UNVERIFIED (Teil-Batches 2+1)",
                    adapterVectors, singles);
            if (!viaAdapter.ordered) {
                LiveSettings.report(stage, "BEFUND: Roh-Probe in Reihenfolge, aber der Adapter im Modus ARRAY_UNVERIFIED "
                        + "liefert nicht die Vektoren der Einzelanfragen; Empfehlung: SINGLE_STRING bleibt Standard, "
                        + "Ergebnis an Strang C");
                return;
            }
            LiveSettings.report(stage, "BEFUND: Array-Eingabe angenommen, je Eingabe ein Eintrag, Reihenfolge bestätigt, "
                    + "Vektoren " + (raw.identical && viaAdapter.identical ? "identisch mit den Einzelanfragen"
                    : "nur ähnlich (Dienst antwortet nicht deterministisch)") + "; Empfehlung: "
                    + "embedding.inputMode=ARRAY_UNVERIFIED kann Standard werden (Entscheidung Auftraggeber)");
        }, LiveSettings.API_KEY_ENV);
    }

    /**
     * Nur ein Validierungsstatus belegt "Array-Eingabe nicht akzeptiert": 400 (Bad Request), 413 (Payload Too Large),
     * 415 (Unsupported Media Type), 422 (Unprocessable Entity). Enger als {@code EmbeddingFailureKind.REJECTED} des
     * Adapters, denn 402, 407, 409, 423 oder 424 wären Betriebsprobleme und kein Befund über Arrays.
     */
    static boolean isValidationRejection(int status) {
        return status == 400 || status == 413 || status == 415 || status == 422;
    }

    /** Ergebnis des Vergleichs: Reihenfolge (je Text ist der Einzelvektor gleicher Position der nächste) und Gleichheit. */
    private static final class Comparison {
        final boolean ordered;
        final boolean identical;

        Comparison(boolean ordered, boolean identical) {
            this.ordered = ordered;
            this.identical = identical;
        }
    }

    /**
     * Vergleicht Vektor i mit allen Einzeltext-Vektoren. Reihenfolge gilt als bestätigt, wenn für jeden Text der
     * Einzelvektor gleicher Position der nächste ist; ob die Vektoren darüber hinaus identisch sind
     * (Cosinus ≥ {@link #SAME_VECTOR}), wird getrennt gemeldet und entscheidet nicht über die Reihenfolge.
     */
    private static Comparison compareWithSingles(int stage, String label, List<float[]> vectors, List<float[]> singles) {
        StringBuilder detail = new StringBuilder();
        boolean ordered = true;
        boolean identical = true;
        for (int i = 0; i < vectors.size(); i++) {
            double same = EmbeddingEndpointProbe.cosine(vectors.get(i), singles.get(i));
            int best = -1;
            double bestValue = -2;
            for (int j = 0; j < singles.size(); j++) {
                double other = EmbeddingEndpointProbe.cosine(vectors.get(i), singles.get(j));
                if (other > bestValue) {
                    bestValue = other;
                    best = j;
                }
            }
            ordered &= best == i;
            identical &= same >= SAME_VECTOR;
            if (i > 0) {
                detail.append(", ");
            }
            detail.append("Text ").append(i + 1).append(": ").append(String.format(Locale.ROOT, "%.4f", same))
                    .append(best == i ? "" : " (am nächsten an Einzeltext " + (best + 1) + ")");
        }
        LiveSettings.report(stage, label + ", Cosinus zum Einzeltext gleicher Position: " + detail + " -> Reihenfolge "
                + (ordered ? "bestätigt" : "NICHT bestätigt") + " (je Text ist der Einzelvektor gleicher Position der "
                + "nächste), identisch (Cosinus >= " + SAME_VECTOR + "): " + (identical ? "ja" : "nein"));
        return new Comparison(ordered, identical);
    }
}
