package com.aresstack.enterpriseai.integration.live;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Stufe 4 der Live-Verifikation: die tatsächliche Dimension und die Form der {@code /embeddings}-Antwort
 * erfassen (Roh-Probe mit einem Einzeltext), damit {@code embedding.dimension} nicht geraten werden muss.
 * Parameter wie {@link LiveEmbeddingsIT}; ist {@code -Dlive.embedding.dimension} gesetzt, wird der Wert gegen die
 * Antwort geprüft, sonst nur gemeldet. Zusatzbefund: ob der Dienst {@code encoding_format=float} annimmt
 * (UNVERIFIED laut Nachtrag; der Adapter sendet das Feld nicht).
 */
public class LiveEmbeddingDimensionIT {

    private static final int STAGE = 4;

    @Test
    public void reportsTheActualDimensionAndResponseShape() throws Exception {
        LiveSettings.withoutSecretLeak(() -> {
            EmbeddingEndpointProbe probe = LiveEmbeddingSupport.probe();
            String model = LiveEmbeddingSupport.model();

            EmbeddingEndpointProbe.Report report = probe.single(model, LiveEmbeddingSupport.TEXT_A);
            LiveSettings.report(STAGE, "Antwort auf einen Einzeltext: " + report.describe());
            assertTrue("keine für den Adapter verwendbare Antwort: " + report.describe(), report.usableByAdapter(1));
            int actual = report.dimension();

            Integer configured = LiveSettings.optionalInteger("live.embedding.dimension");
            if (configured == null) {
                LiveSettings.report(STAGE, "BEFUND: tatsächliche Dimension " + actual
                        + "; Empfehlung: embedding.dimension=" + actual + " konfigurieren");
            } else {
                assertEquals("konfigurierte Dimension stimmt nicht: tatsächlich " + actual
                        + " -> embedding.dimension=" + actual + " setzen", actual, configured.intValue());
                LiveSettings.report(STAGE, "BEFUND: tatsächliche Dimension " + actual + " entspricht der Konfiguration");
            }

            EmbeddingEndpointProbe.Report withFormat = probe.single(model, LiveEmbeddingSupport.TEXT_A, "float");
            String verdict = withFormat.usableByAdapter(1) && withFormat.dimension() == actual
                    ? "angenommen, Antwort wie ohne das Feld"
                    : (withFormat.isOk() ? "angenommen, Antwort aber anders" : "abgelehnt");
            LiveSettings.report(STAGE, "Zusatzbefund encoding_format=float: " + verdict + " (" + withFormat.describe() + ")");
        }, LiveSettings.API_KEY_ENV);
    }
}
