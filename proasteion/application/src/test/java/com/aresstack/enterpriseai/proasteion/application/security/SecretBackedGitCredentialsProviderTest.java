package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.git.GitCredentials;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class SecretBackedGitCredentialsProviderTest {

    private static final SecretRef REF = SecretRef.of("Azure Repos PAT");

    @Test
    public void tokenAndUserComeFromTheEntryAndNeverShowInText() throws Exception {
        GitCredentials credentials = new SecretBackedGitCredentialsProvider(
                ref -> new SecretMaterial(ref, "reader", "pat-123".toCharArray()), REF).credentials();
        assertEquals("reader", credentials.username());
        assertFalse(credentials.toString().contains("pat-123"));
    }

    @Test
    public void entryWithoutUserUsesTheTokenAlone() throws Exception {
        GitCredentials credentials = new SecretBackedGitCredentialsProvider(
                ref -> new SecretMaterial(ref, null, "pat-123".toCharArray()), REF).credentials();
        assertEquals("git", credentials.username());
    }

    @Test
    public void entryWithoutTokenIsRefused() {
        try {
            new SecretBackedGitCredentialsProvider(ref -> new SecretMaterial(ref, "reader", new char[0]), REF)
                    .credentials();
            fail("an entry without token gives no access");
        } catch (SecretUnavailableException expected) {
            assertEquals(SecretUnavailableException.Reason.NOT_FOUND, expected.reason());
        }
    }
}
