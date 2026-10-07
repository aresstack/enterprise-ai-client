package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Gemeinsames der Stufen 2 bis 4: Modell und Base-URL aus den Parametern, die Dimension entweder aus
 * {@code -Dlive.embedding.dimension} oder, wenn sie fehlt, aus der ersten echten Antwort (Roh-Probe). Damit
 * verlangt kein Live-Test eine geratene Dimension; die tatsächliche steht hinterher in der Ausgabe.
 */
final class LiveEmbeddingSupport {

    static final String TEXT_A = "Die Kündigungsfrist beträgt drei Monate zum Quartalsende.";
    static final String TEXT_A_PARAPHRASE = "Gekündigt werden kann mit einer Frist von drei Monaten zum Ende eines Quartals.";
    static final String TEXT_B = "Der Drucker im zweiten Stock hat kein Papier mehr.";

    private LiveEmbeddingSupport() {
    }

    static String model() {
        return LiveSettings.required("live.embedding.model");
    }

    /** Konfiguration mit Platzhalter-Dimension 1, nur für Endpunkt, Timeouts und Proxy der Roh-Probe. */
    static EmbeddingEndpointProbe probe() {
        OpenAiCompatibleEmbeddingConfiguration configuration =
                OpenAiCompatibleEmbeddingConfiguration.builder(LiveSettings.embeddingBaseUrl(), model(), 1)
                        .readTimeoutMillis(120000)
                        .build();
        return new EmbeddingEndpointProbe(configuration, LiveSettings.apiKey());
    }

    /**
     * Die zu verwendende Dimension: konfiguriert, oder aus der ersten echten Antwort übernommen (dann wird das
     * gemeldet). Antwortet der Dienst nicht mit einem brauchbaren Float-Array, scheitert der Test mit der
     * Beschreibung der Antwort.
     */
    static int dimension(int stage, EmbeddingEndpointProbe probe) throws IOException {
        Integer configured = LiveSettings.optionalInteger("live.embedding.dimension");
        if (configured != null) {
            LiveSettings.report(stage, "Dimension " + configured + " aus -Dlive.embedding.dimension (wird geprüft)");
            return configured;
        }
        EmbeddingEndpointProbe.Report report = probe.single(model(), TEXT_A);
        assertTrue("Dimension nicht ermittelbar, Antwort auf einen Einzeltext: " + report.describe(),
                report.usableByAdapter(1));
        LiveSettings.report(stage, "keine Dimension konfiguriert; Antwort auf einen Einzeltext: " + report.describe());
        LiveSettings.report(stage, "Dimension " + report.dimension() + " aus der Antwort übernommen");
        return report.dimension();
    }

    static OpenAiCompatibleEmbeddingConfiguration.Builder configuration(int dimension) {
        return OpenAiCompatibleEmbeddingConfiguration.builder(LiveSettings.embeddingBaseUrl(), model(), dimension)
                .readTimeoutMillis(120000);
    }

    static String cosine(float[] a, float[] b) {
        return String.format(java.util.Locale.ROOT, "%.3f", EmbeddingEndpointProbe.cosine(a, b));
    }
}
