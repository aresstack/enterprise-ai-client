package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentials;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Die Brücken holen das Secret je Aufruf, schließen das Material danach und halten es in keinem Feld. */
public class SecretBackedTokenSourcesTest {

    private static final SecretRef REF = SecretRef.of("Enterprise AI API");

    @Test
    public void chatTokenIsResolvedPerCallAndMaterialClosed() {
        RecordingSecretProvider secrets = new RecordingSecretProvider("svc", " sk-abc ");
        SecretBackedTokenSource source = new SecretBackedTokenSource(secrets, REF);
        assertEquals("sk-abc", source.token());
        assertEquals("sk-abc", source.token());
        assertEquals(2, secrets.requests.size());
        assertEquals(REF, secrets.requests.get(0));
        assertTrue(secrets.allIssuedMaterialClosed());
        assertFalse(source.toString(), source.toString().contains("sk-abc"));
    }

    @Test
    public void bearerTokenIsAFreshCopyPerCall() {
        RecordingSecretProvider secrets = new RecordingSecretProvider(null, "sk-xyz");
        SecretBackedBearerTokenSource source = new SecretBackedBearerTokenSource(secrets, REF);
        char[] first = source.bearerToken();
        char[] second = source.bearerToken();
        assertArrayEquals("sk-xyz".toCharArray(), first);
        assertArrayEquals(first, second);
        assertFalse("jeder Aufruf liefert eine eigene Kopie, die der Adapter löschen darf", first == second);
        Arrays.fill(first, '\0');
        assertArrayEquals("sk-xyz".toCharArray(), second);
        assertTrue(secrets.allIssuedMaterialClosed());
    }

    @Test
    public void bearerTokenIsTrimmedLikeTheChatTokenWithoutAStringCopy() {
        RecordingSecretProvider secrets = new RecordingSecretProvider(null, " sk-xyz\n");
        assertArrayEquals("sk-xyz".toCharArray(), new SecretBackedBearerTokenSource(secrets, REF).bearerToken());
        assertEquals("sk-xyz", new SecretBackedTokenSource(secrets, REF).token());
        assertArrayEquals("nur Rand-Whitespace wird entfernt", "a b".toCharArray(),
                new SecretBackedBearerTokenSource(new RecordingSecretProvider(null, "\ta b "), REF).bearerToken());
        assertEquals(0, new SecretBackedBearerTokenSource(new RecordingSecretProvider(null, "  "), REF)
                .bearerToken().length);
        assertTrue(secrets.allIssuedMaterialClosed());
    }

    @Test
    public void unavailableSecretBecomesUncheckedWithReasonButWithoutMaterial() {
        SecretUnavailableException cause = new SecretUnavailableException(
                SecretUnavailableException.Reason.NOT_AVAILABLE, REF, "KeePass gesperrt");
        RecordingSecretProvider secrets = new RecordingSecretProvider(cause);
        try {
            new SecretBackedTokenSource(secrets, REF).token();
            fail("expected SecretAccessException");
        } catch (SecretAccessException e) {
            assertEquals(SecretUnavailableException.Reason.NOT_AVAILABLE, e.reason());
            assertEquals(REF, e.ref());
            assertTrue(SecretAccessException.describe(e.reason(), e.ref()).contains("KeePass"));
        }
    }

    @Test
    public void unavailableEmbeddingSecretIsAnAuthenticationFailureOfTheEmbeddingPort() {
        SecretUnavailableException cause = new SecretUnavailableException(
                SecretUnavailableException.Reason.NOT_AVAILABLE, REF, "KeePass gesperrt");
        try {
            new SecretBackedBearerTokenSource(new RecordingSecretProvider(cause), REF).bearerToken();
            fail("expected EmbeddingException");
        } catch (EmbeddingException e) {
            assertEquals("bleibt im Fehlerpfad des Ports, damit die Indexierung die Ressource als fehlgeschlagen"
                    + " meldet statt abzubrechen", EmbeddingFailureKind.AUTHENTICATION, e.kind());
            assertFalse(e.isRetryable());
            assertTrue(e.getMessage(), e.getMessage().contains("KeePass"));
            assertTrue(e.getMessage(), e.getMessage().contains(REF.id()));
            assertTrue(e.getCause() instanceof SecretUnavailableException);
        }
    }

    @Test
    public void mediaWikiCredentialsCarryPrincipalAndPassword() throws Exception {
        RecordingSecretProvider secrets = new RecordingSecretProvider("wikiuser", "pw-123");
        SecretBackedMediaWikiCredentialsProvider provider =
                new SecretBackedMediaWikiCredentialsProvider(secrets, SecretRef.of("Intranet-Wiki"));
        MediaWikiCredentials credentials = provider.credentialsFor(
                MediaWikiSiteConfig.builder("intern", "https://wiki.example/w/api.php").build());
        assertEquals("wikiuser", credentials.username());
        assertTrue(secrets.allIssuedMaterialClosed());
        assertFalse(credentials.toString().contains("pw-123"));
        credentials.clear();
    }

    @Test
    public void mediaWikiEntryWithoutUsernameIsNotFound() {
        RecordingSecretProvider secrets = new RecordingSecretProvider(null, "pw");
        try {
            new SecretBackedMediaWikiCredentialsProvider(secrets, REF).credentialsFor(
                    MediaWikiSiteConfig.builder("intern", "https://wiki.example/w/api.php").build());
            fail("expected SecretUnavailableException");
        } catch (SecretUnavailableException e) {
            assertEquals(SecretUnavailableException.Reason.NOT_FOUND, e.reason());
        }
        assertTrue(secrets.allIssuedMaterialClosed());
    }

    @Test
    public void noFieldOfTheBridgesHoldsSecretMaterial() throws Exception {
        for (Class<?> type : new Class<?>[] {SecretBackedTokenSource.class, SecretBackedBearerTokenSource.class,
                SecretBackedMediaWikiCredentialsProvider.class}) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                assertFalse(type.getSimpleName() + "." + field.getName(),
                        fieldType == char[].class || fieldType == String.class
                                || fieldType.getName().endsWith("SecretMaterial"));
            }
        }
    }

    @Test
    public void unavailableProviderExplainsItself() {
        UnavailableSecretProvider provider = new UnavailableSecretProvider("KeePassRPC ist deaktiviert");
        try {
            provider.resolve(REF);
            fail("expected SecretUnavailableException");
        } catch (SecretUnavailableException e) {
            assertEquals(SecretUnavailableException.Reason.NOT_AVAILABLE, e.reason());
            assertEquals(REF, e.ref());
            assertTrue(e.getMessage(), e.getMessage().contains("deaktiviert"));
        }
    }
}
