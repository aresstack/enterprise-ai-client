package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.util.Arrays;

/**
 * Die Probe des Einstellungen-Dialogs: löst eine Referenz einmal auf und meldet nur, ob das Passwortfeld
 * gefüllt ist. Das Material lebt nur innerhalb von {@link SecretProvider#withSecret}, wird sofort überschrieben
 * und verlässt diese Klasse nie, auch nicht als Länge (einziger Ort außerhalb der Secret-Adapter, der Material
 * sieht, ist {@code app.security}; {@code SecretBoundaryTest}).
 */
public final class SecretProbe {

    private SecretProbe() {
    }

    /** @return {@code true}, wenn der Eintrag existiert und sein Passwortfeld nicht leer (nur Whitespace) ist */
    public static boolean isFilled(SecretProvider provider, SecretRef ref) throws SecretUnavailableException {
        if (provider == null || ref == null) {
            throw new IllegalArgumentException("provider and ref must not be null");
        }
        return provider.withSecret(ref, new SecretFunction<Boolean, RuntimeException>() {
            @Override
            public Boolean apply(SecretMaterial material) {
                char[] secret = material.copySecret();
                try {
                    for (char c : secret) {
                        if (!Character.isWhitespace(c)) {
                            return Boolean.TRUE;
                        }
                    }
                    return Boolean.FALSE;
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
        });
    }
}
