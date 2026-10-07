package com.aresstack.enterpriseai.integration.live;

import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Die Schwärzung, mit der die Live-Tests jede Exception weitergeben: kein Secret, kein Host in der Ausgabe. */
public class FailureRedactionTest {

    private static final String SECRET = "sk-live-0123456789/abc+def=";
    private static final List<String[]> SECRETS =
            Collections.singletonList(FailureRedaction.secretVariants("ENTERPRISE_AI_LIVE_API_KEY", " " + SECRET + "\n"));
    private static final List<String> ADDRESSES = FailureRedaction.addressVariants(
            Arrays.asList("https://ki.intern.example/v1", "http://wiki.intern.example:8080/w", "127.0.0.1"));

    @Test
    public void hostnamesAndUrlsAreReplacedThroughoutTheChain() {
        UnknownHostException root = new UnknownHostException("ki.intern.example");
        IOException transport = new IOException("connection to ki.intern.example failed: UnknownHostException", root);
        RuntimeException adapter = new RuntimeException(
                "embedding request to https://ki.intern.example/v1/embeddings failed: IOException", transport);
        adapter.addSuppressed(new IllegalStateException("wiki http://wiki.intern.example:8080/w/api.php unreachable"));

        FailureRedaction.RedactedFailure redacted = FailureRedaction.redact(adapter, SECRETS, ADDRESSES);

        assertEquals("java.lang.RuntimeException: embedding request to <host>/embeddings failed: IOException",
                redacted.getMessage());
        assertEquals("java.io.IOException: connection to <host> failed: UnknownHostException",
                redacted.getCause().getMessage());
        assertEquals("java.net.UnknownHostException: <host>", redacted.getCause().getCause().getMessage());
        assertNull(redacted.getCause().getCause().getCause());
        assertEquals(1, redacted.getSuppressed().length);
        assertEquals("java.lang.IllegalStateException: wiki <host>/api.php unreachable",
                redacted.getSuppressed()[0].getMessage());
        assertFalse(String.valueOf(redacted), String.valueOf(redacted).contains("intern.example"));
        assertSame(adapter.getStackTrace()[0], redacted.getStackTrace()[0]);
        assertEquals("java.lang.RuntimeException", redacted.originalClassName());
    }

    @Test
    public void messagesContainingTheSecretAreReplacedEntirely() {
        IllegalArgumentException raw = new IllegalArgumentException(
                "Illegal character(s) in message header value: Bearer " + SECRET);
        RuntimeException encoded = new RuntimeException(
                "login failed for lgpassword=" + "sk-live-0123456789%2Fabc%2Bdef%3D" + " at ki.intern.example", raw);

        FailureRedaction.RedactedFailure redacted = FailureRedaction.redact(encoded, SECRETS, ADDRESSES);

        assertEquals("java.lang.RuntimeException: <Meldung der RuntimeException geschwärzt: enthält das Secret aus "
                + "ENTERPRISE_AI_LIVE_API_KEY>", redacted.getMessage());
        assertEquals("java.lang.IllegalArgumentException: <Meldung der IllegalArgumentException geschwärzt: enthält das "
                + "Secret aus ENTERPRISE_AI_LIVE_API_KEY>", redacted.getCause().getMessage());
        assertFalse(String.valueOf(redacted).contains("0123456789"));
    }

    @Test
    public void messagesWithoutSecretsOrHostsStayReadable() {
        AssertionError failure = new AssertionError("leere Antwort ohne Streaming");

        FailureRedaction.RedactedFailure redacted = FailureRedaction.redact(failure, SECRETS, ADDRESSES);

        assertEquals("java.lang.AssertionError: leere Antwort ohne Streaming", redacted.getMessage());
        assertNull(redacted.getCause());
        assertEquals("java.lang.IllegalStateException",
                FailureRedaction.redact(new IllegalStateException(), SECRETS, ADDRESSES).getMessage());
    }

    @Test
    public void circularCausesDoNotRecurseForever() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        FailureRedaction.RedactedFailure redacted = FailureRedaction.redact(b, SECRETS, ADDRESSES);

        assertNotNull(redacted.getCause());
        assertNull(redacted.getCause().getCause());
    }

    @Test
    public void emptySecretsProduceNoVariants() {
        assertNull(FailureRedaction.secretVariants("X", null));
        assertNull(FailureRedaction.secretVariants("X", "  "));
        assertTrue(FailureRedaction.addressVariants(Arrays.asList(null, " ")).isEmpty());
        assertEquals(Arrays.asList("https://ki.intern.example/v1", "ki.intern.example"),
                FailureRedaction.addressVariants(Collections.singletonList("https://ki.intern.example/v1")));
    }
}
