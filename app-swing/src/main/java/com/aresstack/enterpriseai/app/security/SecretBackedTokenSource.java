package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.chat.openai.OpenAiCompatibleChatConfig;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.util.Arrays;

/**
 * {@link OpenAiCompatibleChatConfig.TokenSource} über den Security-Port: Der API-Key wird je Anfrage mit
 * {@link SecretProvider#withSecret} aufgelöst und danach gelöscht; kein Feld hält ihn.
 *
 * <p>Bekannte Grenze: Die Schnittstelle des Chat-Adapters verlangt einen {@link String}; dieser lässt sich nicht
 * überschreiben und lebt bis zur Garbage Collection. Das {@code char[]} aus dem Material wird sofort genullt.
 */
public final class SecretBackedTokenSource implements OpenAiCompatibleChatConfig.TokenSource {

    private final SecretProvider secrets;
    private final SecretRef ref;

    public SecretBackedTokenSource(SecretProvider secrets, SecretRef ref) {
        if (secrets == null || ref == null) {
            throw new IllegalArgumentException("secrets and ref must not be null");
        }
        this.secrets = secrets;
        this.ref = ref;
    }

    public SecretRef ref() {
        return ref;
    }

    /** @throws SecretAccessException wenn das Secret nicht bereitgestellt werden kann */
    @Override
    public String token() {
        try {
            return secrets.<String, RuntimeException>withSecret(ref, new SecretFunction<String, RuntimeException>() {
                @Override
                public String apply(SecretMaterial material) {
                    char[] secret = material.copySecret();
                    char[] trimmed = null;
                    try {
                        trimmed = SecretChars.trimmedCopy(secret);
                        return new String(trimmed);
                    } finally {
                        Arrays.fill(secret, '\0');
                        if (trimmed != null) {
                            Arrays.fill(trimmed, '\0');
                        }
                    }
                }
            });
        } catch (SecretUnavailableException e) {
            throw new SecretAccessException(e);
        }
    }

    @Override
    public String toString() {
        return "SecretBackedTokenSource[" + ref + "]";
    }
}
