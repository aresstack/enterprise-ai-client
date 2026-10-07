package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentials;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiCredentialsProvider;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSiteConfig;

import java.util.Arrays;

/**
 * {@link MediaWikiCredentialsProvider} über den Security-Port: Benutzername und Passwort des KeePass-Eintrags
 * werden erst beim Login geholt; die {@link MediaWikiCredentials} löscht der Adapter nach dem Login selbst.
 */
public final class SecretBackedMediaWikiCredentialsProvider implements MediaWikiCredentialsProvider {

    private final SecretProvider secrets;
    private final SecretRef ref;

    public SecretBackedMediaWikiCredentialsProvider(SecretProvider secrets, SecretRef ref) {
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
    public MediaWikiCredentials credentialsFor(MediaWikiSiteConfig site) throws SecretUnavailableException {
        return secrets.<MediaWikiCredentials, SecretUnavailableException>withSecret(ref,
                new SecretFunction<MediaWikiCredentials, SecretUnavailableException>() {
                    @Override
                    public MediaWikiCredentials apply(SecretMaterial material) throws SecretUnavailableException {
                        if (!material.hasPrincipal()) {
                            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND, ref,
                                    "KeePass-Eintrag hat keinen Benutzernamen");
                        }
                        char[] password = material.copySecret();
                        try {
                            return new MediaWikiCredentials(material.principal(), password);
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }
                });
    }

    @Override
    public String toString() {
        return "SecretBackedMediaWikiCredentialsProvider[" + ref + "]";
    }
}
