package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretFunction;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.git.GitCredentials;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.git.GitCredentialsProvider;

import java.util.Arrays;

/**
 * {@link GitCredentialsProvider} über den Security-Port (#137): Benutzer und Lese-Token (PAT) des KeePass-Eintrags
 * werden erst beim Fetch geholt; die {@link GitCredentials} löscht der Adapter nach dem Fetch selbst. Ein Eintrag
 * ohne Benutzernamen ist erlaubt (Azure Repos und GitHub werten bei Tokens nur das Token aus).
 */
public final class SecretBackedGitCredentialsProvider implements GitCredentialsProvider {

    private final SecretProvider secrets;
    private final SecretRef ref;

    public SecretBackedGitCredentialsProvider(SecretProvider secrets, SecretRef ref) {
        if (secrets == null || ref == null) {
            throw new IllegalArgumentException("secrets and ref must not be null");
        }
        this.secrets = secrets;
        this.ref = ref;
    }

    public SecretRef ref() {
        return ref;
    }

    @Override
    public GitCredentials credentials() throws SecretUnavailableException {
        return secrets.<GitCredentials, SecretUnavailableException>withSecret(ref,
                new SecretFunction<GitCredentials, SecretUnavailableException>() {
                    @Override
                    public GitCredentials apply(SecretMaterial material) throws SecretUnavailableException {
                        char[] token = material.copySecret();
                        try {
                            if (token.length == 0) {
                                throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND,
                                        ref, "KeePass-Eintrag hat kein Token");
                            }
                            return new GitCredentials(material.hasPrincipal() ? material.principal() : null, token);
                        } finally {
                            Arrays.fill(token, '\0');
                        }
                    }
                });
    }

    @Override
    public String toString() {
        return "SecretBackedGitCredentialsProvider[" + ref + "]";
    }
}
