package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.app.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.confluence.ClientCertificates;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Baut die TLS-Socket-Factory für ein Client-Zertifikat (mTLS, Confluence) aus der Konfiguration: Windows-MY per
 * Alias (MainframeMate) oder eine PKCS12-Datei, deren Passwort nur für das Laden über den Security-Port geholt und
 * danach gelöscht wird. Der private Schlüssel bleibt im KeyStore.
 */
public final class ClientCertificateFactory {

    private ClientCertificateFactory() {
    }

    /**
     * @throws GeneralSecurityException wenn Zertifikat oder KeyStore nicht nutzbar sind
     * @throws IOException              wenn die PKCS12-Datei nicht lesbar ist
     * @throws SecretUnavailableException wenn das KeyStore-Passwort nicht bereitgestellt werden kann
     */
    public static SSLSocketFactory create(ClientCertificateConfig config, SecretProvider secrets)
            throws GeneralSecurityException, IOException, SecretUnavailableException {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (config.usesWindowsStore()) {
            return ClientCertificates.windowsMy(config.alias());
        }
        if (config.keyStorePasswordRef() == null) {
            return fromFile(config, null);
        }
        if (secrets == null) {
            throw new IllegalArgumentException("secrets must not be null when a keyStorePasswordRef is configured");
        }
        return secrets.<SSLSocketFactory, GeneralSecurityException>withSecret(config.keyStorePasswordRef(),
                new SecretFunction<SSLSocketFactory, GeneralSecurityException>() {
                    @Override
                    public SSLSocketFactory apply(SecretMaterial material) throws GeneralSecurityException {
                        char[] password = material.copySecret();
                        try {
                            return fromFile(config, password);
                        } catch (IOException e) {
                            throw new GeneralSecurityException("PKCS12-Datei nicht lesbar: " + config.keyStoreFile(), e);
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }
                });
    }

    private static SSLSocketFactory fromFile(ClientCertificateConfig config, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(config.keyStoreFile())) {
            store.load(in, password);
        }
        return ClientCertificates.fromKeyStore(store, password, config.alias());
    }
}
