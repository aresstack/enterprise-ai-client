package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AskedSecretProviderTest {

    private static final SecretRef REF = SecretRef.of("ask:Confluence");

    @Test
    public void anEmptyAnswerIsAskedOnlyOncePerSession() throws Exception {
        final int[] asked = {0};
        AskedSecretProvider.CredentialPrompt prompt = new AskedSecretProvider.CredentialPrompt() {
            @Override
            public AskedSecretProvider.Answer ask(SecretRef ref, String label) {
                asked[0]++;
                return new AskedSecretProvider.Answer("", new char[0]);
            }
        };
        AskedSecretProvider secrets = new AskedSecretProvider(
                new UnavailableSecretProvider("no vault"), prompt, new ManualCredentials());

        for (int i = 0; i < 3; i++) {
            SecretMaterial material = secrets.resolve(REF);
            assertEquals(0, material.copySecret().length);
            material.close();
        }
        assertEquals(1, asked[0]);
    }
}
