package com.aresstack.enterpriseai.integration.live;

import com.aresstack.enterpriseai.app.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die Roh-Probe der Live-Verifikation gegen den lokalen Fake-Endpunkt: sie sendet die Header des Adapters,
 * beschreibt Erfolgs- und Fehlerantworten und nennt dabei weder Token noch Antworttext.
 */
public class EmbeddingEndpointProbeTest {

    private static final String TOKEN = "live-probe-test-token-0123456789";
    private static final String MODEL = "fake-embedding-model";

    private FakeEmbeddingsServer server;
    private EmbeddingEndpointProbe probe;

    @Before
    public void setUp() throws Exception {
        server = new FakeEmbeddingsServer(16);
        OpenAiCompatibleEmbeddingConfiguration configuration =
                OpenAiCompatibleEmbeddingConfiguration.builder(server.baseUrl(), MODEL, 1).build();
        BearerTokenSource token = () -> TOKEN.toCharArray();
        probe = new EmbeddingEndpointProbe(configuration, token);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void singleInputIsDescribedWithDimensionAndUsage() throws Exception {
        EmbeddingEndpointProbe.Report report = probe.single(MODEL, "Die Kündigungsfrist beträgt drei Monate.");

        assertTrue(report.describe(), report.isOk());
        assertEquals(1, report.dataCount());
        assertEquals(EmbeddingEndpointProbe.EmbeddingShape.FLOAT_ARRAY, report.shape());
        assertEquals(16, report.dimension());
        assertTrue(report.indexEverywhere());
        assertTrue(report.indexAscending());
        assertTrue(report.allFinite());
        assertTrue(report.usagePresent());
        assertTrue(report.modelEchoed());
        assertTrue(report.usableByAdapter(1));
        assertFalse(report.usableByAdapter(2));
        assertEquals(1, server.requests().size());
        assertEquals("Bearer " + TOKEN, server.requests().get(0).authorization());
        assertNull("ohne Angabe wird kein encoding_format gesendet", server.requests().get(0).encodingFormat());
        assertEquals(Arrays.asList("Die Kündigungsfrist beträgt drei Monate."), server.requests().get(0).inputs());
        assertTrue(report.describe(), report.describe().contains("Dimension 16"));
        assertTrue(report.describe(), report.describe().contains("usage vorhanden"));
        assertFalse(report.describe(), report.describe().contains(TOKEN));
        assertFalse(report.describe(), report.describe().contains("Kündigungsfrist"));
    }

    @Test
    public void arrayInputIsDescribedInListOrder() throws Exception {
        List<String> texts = Arrays.asList("Erster Text über Urlaub.", "Zweiter Text über Drucker.", "Dritter Text.");
        EmbeddingEndpointProbe.Report report = probe.array(MODEL, texts);

        assertTrue(report.describe(), report.usableByAdapter(3));
        assertEquals(3, report.dataCount());
        assertEquals(Arrays.asList(16, 16, 16), report.dimensions());
        assertEquals(texts, server.requests().get(0).inputs());
        List<float[]> vectors = report.vectors();
        for (int i = 0; i < texts.size(); i++) {
            double same = EmbeddingEndpointProbe.cosine(vectors.get(i), server.vectorFor(texts.get(i)));
            assertEquals("Eintrag " + i + " entspricht nicht dem Vektor des Fakes", 1.0, same, 1e-5);
        }
    }

    @Test
    public void encodingFormatIsSentAlong() throws Exception {
        EmbeddingEndpointProbe.Report report = probe.single(MODEL, "Text", "float");

        assertTrue(report.describe(), report.isOk());
        assertEquals(1, server.requests().size());
        assertEquals("float", server.requests().get(0).encodingFormat());
    }

    @Test
    public void errorsAreReportedAsStatusAndCodeOnly() throws Exception {
        server.failWith(400, "{\"error\":{\"message\":\"input " + TOKEN + " rejected\",\"type\":\"invalid_request_error\"}}");

        EmbeddingEndpointProbe.Report report = probe.single(MODEL, "Text");

        assertFalse(report.isOk());
        assertEquals(400, report.status());
        assertEquals("invalid_request_error", report.errorCode());
        assertEquals(-1, report.dataCount());
        assertFalse(report.usableByAdapter(1));
        assertTrue(report.describe(), report.describe().startsWith("HTTP 400"));
        assertFalse(report.describe(), report.describe().contains(TOKEN));
        assertFalse(report.describe(), report.describe().contains("rejected"));
    }

    @Test
    public void nonJsonErrorHasNoCode() throws Exception {
        server.failWith(502, "<html>Bad Gateway</html>");

        EmbeddingEndpointProbe.Report report = probe.single(MODEL, "Text");

        assertEquals(502, report.status());
        assertNull(report.errorCode());
        assertTrue(report.describe(), report.describe().contains("kein JSON-Objekt"));
        assertFalse(report.describe(), report.describe().contains("Gateway"));
    }

    @Test
    public void unexpectedShapesAreDescribedNotRejected() {
        EmbeddingEndpointProbe.Report base64 = EmbeddingEndpointProbe.describe(200,
                "{\"object\":\"list\",\"data\":[{\"index\":0,\"embedding\":\"AAAA\"}]}", MODEL);
        assertEquals(EmbeddingEndpointProbe.EmbeddingShape.STRING, base64.shape());
        assertFalse(base64.usableByAdapter(1));
        assertFalse(base64.modelEchoed());

        EmbeddingEndpointProbe.Report withoutIndex = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"embedding\":[0.5,0.5]},{\"embedding\":[1,0]}],\"model\":\"" + MODEL + "\"}", MODEL);
        assertFalse(withoutIndex.indexEverywhere());
        assertTrue(withoutIndex.usableByAdapter(2));
        assertEquals(2, withoutIndex.dimension());
        assertTrue(withoutIndex.modelEchoed());

        EmbeddingEndpointProbe.Report shuffled = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"index\":1,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]}", MODEL);
        assertEquals(EmbeddingEndpointProbe.IndexMode.REORDERED, shuffled.indexMode());
        assertTrue(shuffled.indexEverywhere());
        assertFalse(shuffled.indexAscending());
        assertTrue("der Adapter sortiert nach index um", shuffled.usableByAdapter(2));
        assertEquals("Vektoren in Adapter-Reihenfolge", 2f, shuffled.vectors().get(0)[0], 0f);
        assertEquals(1f, shuffled.vectors().get(1)[0], 0f);
        assertTrue(shuffled.describe(), shuffled.describe().contains("Adapter sortiert um"));

        EmbeddingEndpointProbe.Report mixed = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"embedding\":[2]}]}", MODEL);
        assertEquals(EmbeddingEndpointProbe.IndexMode.MIXED, mixed.indexMode());
        assertFalse(mixed.indexEverywhere());
        assertFalse("ein Gemisch lehnt der Adapter ab", mixed.usableByAdapter(2));

        EmbeddingEndpointProbe.Report duplicate = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]}", MODEL);
        assertEquals(EmbeddingEndpointProbe.IndexMode.INVALID, duplicate.indexMode());
        assertFalse(duplicate.usableByAdapter(2));

        EmbeddingEndpointProbe.Report outOfRange = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":2,\"embedding\":[2]}]}", MODEL);
        assertEquals(EmbeddingEndpointProbe.IndexMode.INVALID, outOfRange.indexMode());
        assertFalse(outOfRange.usableByAdapter(2));

        EmbeddingEndpointProbe.Report fractional = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"index\":0.5,\"embedding\":[1]}]}", MODEL);
        assertEquals(EmbeddingEndpointProbe.IndexMode.INVALID, fractional.indexMode());
        assertFalse(fractional.usableByAdapter(1));

        EmbeddingEndpointProbe.Report uneven = EmbeddingEndpointProbe.describe(200,
                "{\"data\":[{\"index\":0,\"embedding\":[1,2]},{\"index\":1,\"embedding\":[3]}]}", MODEL);
        assertEquals(-1, uneven.dimension());
        assertFalse(uneven.usableByAdapter(2));

        EmbeddingEndpointProbe.Report notJson = EmbeddingEndpointProbe.describe(200, "ok", MODEL);
        assertEquals(-1, notJson.dataCount());
        assertEquals(EmbeddingEndpointProbe.IndexMode.NONE, notJson.indexMode());
        assertTrue(notJson.describe(), notJson.describe().contains("kein data-Array"));
    }

    @Test
    public void objectFieldIsClassifiedNeverEchoed() {
        EmbeddingEndpointProbe.Report list = EmbeddingEndpointProbe.describe(200,
                "{\"object\":\"list\",\"data\":[{\"index\":0,\"embedding\":[1]}]}", MODEL);
        assertTrue(list.describe(), list.describe().contains("object=list"));

        EmbeddingEndpointProbe.Report reflecting = EmbeddingEndpointProbe.describe(200,
                "{\"object\":\"Bearer " + TOKEN + " ki.intern.example\",\"data\":[]}", MODEL);
        assertTrue(reflecting.describe(), reflecting.describe().contains("object=unerwartet"));
        assertFalse(reflecting.describe(), reflecting.describe().contains(TOKEN));
        assertFalse(reflecting.describe(), reflecting.describe().contains("intern.example"));

        EmbeddingEndpointProbe.Report missing = EmbeddingEndpointProbe.describe(200, "{\"data\":[]}", MODEL);
        assertTrue(missing.describe(), missing.describe().contains("object=fehlt"));
    }

    @Test
    public void cosineOfIdenticalVectorsIsOne() {
        assertEquals(1.0, EmbeddingEndpointProbe.cosine(new float[] {1, 2, 3}, new float[] {2, 4, 6}), 1e-9);
        assertEquals(0.0, EmbeddingEndpointProbe.cosine(new float[] {1, 0}, new float[] {0, 1}), 1e-9);
        assertEquals(0.0, EmbeddingEndpointProbe.cosine(new float[] {0, 0}, new float[] {0, 1}), 1e-9);
    }
}
