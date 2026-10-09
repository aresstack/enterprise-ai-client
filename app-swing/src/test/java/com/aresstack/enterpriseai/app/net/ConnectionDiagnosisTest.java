package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Ursachenkette und Hinweis für die Oberfläche: lesbar, ohne doppelte Meldungen, ohne Tokens. */
public class ConnectionDiagnosisTest {

    private static ChatCompletionException transport(Throwable cause) {
        return new ChatCompletionException(ChatErrorKind.TRANSPORT,
                "connection to ki.example failed: " + cause.getClass().getSimpleName(), cause);
    }

    @Test
    public void certificateFailureIsExplainedWithTheShortExceptionNamesOnly() {
        CertPathBuilderException root = new CertPathBuilderException(
                "unable to find valid certification path to requested target");
        CertificateException validator = new CertificateException("PKIX path building failed: "
                + "sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification "
                + "path to requested target", root);
        SSLHandshakeException handshake = new SSLHandshakeException("sun.security.validator.ValidatorException: "
                + "PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to "
                + "find valid certification path to requested target");
        handshake.initCause(validator);
        ChatCompletionException error = transport(handshake);

        String detail = ConnectionDiagnosis.detail(error);
        assertEquals("connection to ki.example failed: SSLHandshakeException | SSLHandshakeException: "
                + "ValidatorException: PKIX path building failed: SunCertPathBuilderException: unable to find valid "
                + "certification path to requested target", detail);
        String hint = ConnectionDiagnosis.hint(error);
        assertTrue(hint, hint.contains("vertraut dem Zertifikat des Servers nicht"));
        assertTrue(hint, hint.contains("network.tls.caCertificatesFile"));
    }

    @Test
    public void handshakeFailureWithoutCertificateCauseGetsTheTlsHintInsteadOfTheCertificateRemedy() {
        String handshake = ConnectionDiagnosis.hint(transport(
                new SSLHandshakeException("Received fatal alert: handshake_failure")));
        assertTrue(handshake, handshake.contains("TLS-Verhandlung"));
        assertFalse(handshake, handshake.contains("Zertifikat des Servers"));
        String version = ConnectionDiagnosis.hint(transport(
                new SSLHandshakeException("Received fatal alert: protocol_version")));
        assertTrue(version, version.contains("Java aktualisieren"));
        assertFalse(version, version.contains("network.tls.caCertificatesFile"));
    }

    @Test
    public void proxyAndNameResolutionAndTimeoutsGetTheirOwnHints() {
        String auth = ConnectionDiagnosis.hint(transport(new IOException(
                "Unable to tunnel through proxy. Proxy returns \"HTTP/1.1 407 Proxy Authentication Required\"")));
        assertTrue(auth, auth.contains("Anmeldung"));
        String refused = ConnectionDiagnosis.hint(transport(new IOException(
                "Unable to tunnel through proxy. Proxy returns \"HTTP/1.1 403 Forbidden\"")));
        assertTrue(refused, refused.contains("Proxy hat die Verbindung"));
        String unknown = ConnectionDiagnosis.hint(transport(new UnknownHostException("ki.example")));
        assertTrue(unknown, unknown.contains("network.proxy.mode=AUTO"));
        assertTrue(unknown, unknown.contains("Route zum KI-Dienst"));
        String timeout = ConnectionDiagnosis.hint(transport(new SocketTimeoutException("connect timed out")));
        assertTrue(timeout, timeout.contains("chat.connectTimeoutMillis"));
        String connect = ConnectionDiagnosis.hint(transport(new ConnectException("Connection refused: connect")));
        assertTrue(connect, connect.contains("abgelehnt"));
        assertEquals("der Hostname steckt schon in der Adaptermeldung und wird nicht wiederholt",
                "connection to ki.example failed: UnknownHostException",
                ConnectionDiagnosis.detail(transport(new UnknownHostException("ki.example"))));
        assertEquals("connection to ki.example failed: ConnectException | ConnectException: Connection refused: connect",
                ConnectionDiagnosis.detail(transport(new ConnectException("Connection refused: connect"))));
    }

    @Test
    public void keePassAndStreamProblemsAreRecognised() {
        ChatCompletionException token = new ChatCompletionException(ChatErrorKind.AUTHENTICATION,
                "token source failed: SecretAccessException");
        assertTrue(ConnectionDiagnosis.hint(token).contains("KeePass"));
        assertTrue(ConnectionDiagnosis.hint(token).contains("Protokoll"));
        ChatCompletionException stream = new ChatCompletionException(ChatErrorKind.TRANSPORT,
                "stream ended before [DONE]");
        assertTrue(ConnectionDiagnosis.hint(stream).contains("ohne sie abzuschließen"));
        assertEquals("stream ended before [DONE]", ConnectionDiagnosis.detail(stream));
    }

    @Test
    public void unknownCausesHaveNoHintAndEmptyMessagesFallBackToTheClassName() {
        ChatCompletionException provider = new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR, 500,
                "HTTP 500: internal_error", null);
        assertNull(ConnectionDiagnosis.hint(provider));
        assertEquals("HTTP 500: internal_error", ConnectionDiagnosis.detail(provider));
        assertEquals("IllegalStateException", ConnectionDiagnosis.detail(new IllegalStateException()));
        assertEquals("", ConnectionDiagnosis.detail(null));
        assertNull(ConnectionDiagnosis.hint(null));
    }

    @Test
    public void bearerTokensAreMaskedAndLongChainsAreShortened() {
        ChatCompletionException echoed = new ChatCompletionException(ChatErrorKind.AUTHENTICATION, 401,
                "HTTP 401: invalid header Authorization: Bearer sk-abcdefghijklmnopqrstuvwxyz0123456789", null);
        String detail = ConnectionDiagnosis.detail(echoed);
        assertFalse(detail, detail.contains("sk-abcdef"));
        assertTrue(detail, detail.contains(ConnectionDiagnosis.MASKED_TOKEN));
        assertEquals(ConnectionDiagnosis.MASKED_TOKEN, ConnectionDiagnosis.mask("bearer xyz"));
        assertEquals("Authorization: Bearer *** gesendet", ConnectionDiagnosis.mask("Authorization: Bearer a.b-c gesendet"));
        assertEquals("", ConnectionDiagnosis.mask(null));

        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longMessage.append("Zeile ").append(i).append('\n');
        }
        String shortened = ConnectionDiagnosis.detail(new IOException(longMessage.toString()));
        assertTrue(shortened.length() <= ConnectionDiagnosis.MAX_DETAIL_LENGTH + 3);
        assertTrue(shortened.endsWith("..."));
        assertFalse(shortened.contains("\n"));
    }

    @Test
    public void selfReferencingCausesDoNotLoop() {
        IOException a = new IOException("a");
        IOException b = new IOException("b", a);
        a.initCause(b);
        assertEquals("IOException: a | IOException: b", ConnectionDiagnosis.detail(a));
    }
}
