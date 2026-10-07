package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.security.SecretRef;

import java.nio.file.Path;

/**
 * Client-Zertifikat (mTLS) für den Confluence-Transport, nach MainframeMate: Alias im Windows-Zertifikatsspeicher
 * ({@code Windows-MY}, kein Passwort) oder eine PKCS12-Datei, deren Passwort nur als {@link SecretRef} vorliegt.
 */
public final class ClientCertificateConfig {

    private final String alias;
    private final Path keyStoreFile;
    private final SecretRef keyStorePasswordRef;

    ClientCertificateConfig(String alias, Path keyStoreFile, SecretRef keyStorePasswordRef) {
        this.alias = alias;
        this.keyStoreFile = keyStoreFile;
        this.keyStorePasswordRef = keyStorePasswordRef;
    }

    /** Alias des Zertifikats; kein Secret. */
    public String alias() {
        return alias;
    }

    /** PKCS12-Datei oder {@code null} für den Windows-Zertifikatsspeicher des Benutzers. */
    public Path keyStoreFile() {
        return keyStoreFile;
    }

    /** Verweis auf das Passwort der PKCS12-Datei oder {@code null} (Windows-MY, passwortlose Datei). */
    public SecretRef keyStorePasswordRef() {
        return keyStorePasswordRef;
    }

    public boolean usesWindowsStore() {
        return keyStoreFile == null;
    }

    @Override
    public String toString() {
        return "ClientCertificateConfig[alias=" + alias + ", keyStore="
                + (keyStoreFile == null ? "Windows-MY" : keyStoreFile) + ", passwordRef="
                + (keyStorePasswordRef == null ? "keine" : keyStorePasswordRef) + "]";
    }
}
