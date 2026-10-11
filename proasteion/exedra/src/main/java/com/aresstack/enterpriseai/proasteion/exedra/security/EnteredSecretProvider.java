package com.aresstack.enterpriseai.proasteion.exedra.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;

import java.util.Arrays;

/**
 * Die gerade im Eintragseditor eingegebenen Zugangsdaten als {@link SecretProvider}, nur für den Verbindungstest vor
 * dem Speichern (wie MainframeMate „🔗 Verbindung testen“). Der Besitzer schließt ihn; {@link #close()} überschreibt
 * das Passwort.
 */
public final class EnteredSecretProvider implements SecretProvider, AutoCloseable {

    private final String user;
    private final char[] password;

    public EnteredSecretProvider(String user, char[] password) {
        this.user = user == null ? "" : user;
        this.password = password == null ? new char[0] : Arrays.copyOf(password, password.length);
    }

    @Override
    public SecretMaterial resolve(SecretRef ref) {
        return new SecretMaterial(ref, user, Arrays.copyOf(password, password.length));
    }

    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
        return "EnteredSecretProvider[***]";
    }
}
