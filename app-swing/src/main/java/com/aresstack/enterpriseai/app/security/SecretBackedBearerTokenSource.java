package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.openai.BearerTokenSource;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * {@link BearerTokenSource} des Embedding-Adapters über den Security-Port: je Request aufgelöst, als getrimmte Kopie
 * übergeben (der Adapter nullt sie nach Gebrauch), das Material selbst wird im {@code withSecret} gelöscht.
 *
 * <p>Ein nicht beschaffbares Secret wird als {@link EmbeddingException} mit {@link EmbeddingFailureKind#AUTHENTICATION}
 * gemeldet, nicht als fremde Laufzeitausnahme: So bleibt der Fehler im Fehlerpfad des Embedding-Ports, und die
 * Indexierung meldet die Ressource als fehlgeschlagen, statt ohne Abschluss abzubrechen.
 */
public final class SecretBackedBearerTokenSource implements BearerTokenSource {

    private static final Logger LOG = Logger.getLogger(SecretBackedBearerTokenSource.class.getName());

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

    /** @throws EmbeddingException ({@code AUTHENTICATION}) wenn das Secret nicht bereitgestellt werden kann */
    @Override
    public char[] bearerToken() {
        try {
            return secrets.<char[], RuntimeException>withSecret(ref, new SecretFunction<char[], RuntimeException>() {
                @Override
                public char[] apply(SecretMaterial material) {
                    char[] raw = material.copySecret();
                    try {
                        return SecretChars.trimmedCopy(raw);
                    } finally {
                        Arrays.fill(raw, '\0');
                    }
                }
            });
        } catch (SecretUnavailableException e) {
            LOG.warning("API-Key für Embeddings nicht lesbar: " + SecretAccessException.describe(e.reason(), e.ref()));
            throw new EmbeddingException(EmbeddingFailureKind.AUTHENTICATION,
                    SecretAccessException.describe(e.reason(), e.ref()), e);
        }
    }

    @Override
    public String toString() {
        return "SecretBackedBearerTokenSource[" + ref + "]";
    }
}
