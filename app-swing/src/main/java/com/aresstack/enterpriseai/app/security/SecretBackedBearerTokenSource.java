package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

/**
 * {@link BearerTokenSource} des Embedding-Adapters über den Security-Port: je Request aufgelöst, als Kopie
 * übergeben (der Adapter nullt sie nach Gebrauch), das Material selbst wird im {@code withSecret} gelöscht.
 */
public final class SecretBackedBearerTokenSource implements BearerTokenSource {

    private final SecretProvider secrets;
    private final SecretRef ref;

    public SecretBackedBearerTokenSource(SecretProvider secrets, SecretRef ref) {
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
    public char[] bearerToken() {
        try {
            return secrets.<char[], RuntimeException>withSecret(ref, new SecretFunction<char[], RuntimeException>() {
                @Override
                public char[] apply(SecretMaterial material) {
                    return material.copySecret();
                }
            });
        } catch (SecretUnavailableException e) {
            throw new SecretAccessException(e);
        }
    }

    @Override
    public String toString() {
        return "SecretBackedBearerTokenSource[" + ref + "]";
    }
}
