package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretFunction;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.sharepoint.SharePointCredentials;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.sharepoint.SharePointCredentialsProvider;

import java.util.Arrays;

/**
 * {@link SharePointCredentialsProvider} über den Security-Port: Benutzername und Passwort des KeePass-Eintrags
 * werden erst beim Zugriff geholt; die {@link SharePointCredentials} löscht der Adapter nach Gebrauch selbst.
 */
public final class SecretBackedSharePointCredentialsProvider implements SharePointCredentialsProvider {

    private final SecretProvider secrets;
    private final SecretRef ref;

    public SecretBackedSharePointCredentialsProvider(SecretProvider secrets, SecretRef ref) {
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
    public SharePointCredentials credentials() throws SecretUnavailableException {
        return secrets.<SharePointCredentials, SecretUnavailableException>withSecret(ref,
                new SecretFunction<SharePointCredentials, SecretUnavailableException>() {
                    @Override
                    public SharePointCredentials apply(SecretMaterial material) throws SecretUnavailableException {
                        if (!material.hasPrincipal()) {
                            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND, ref,
                                    "KeePass-Eintrag hat keinen Benutzernamen");
                        }
                        char[] password = material.copySecret();
                        try {
                            return new SharePointCredentials(material.principal(), password);
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }
                });
    }

    @Override
    public String toString() {
        return "SecretBackedSharePointCredentialsProvider[" + ref + "]";
    }
}
