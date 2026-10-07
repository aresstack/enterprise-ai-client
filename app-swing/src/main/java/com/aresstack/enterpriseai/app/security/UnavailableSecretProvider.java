package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

/**
 * Security-Port ohne Backend: Wird verdrahtet, wenn KeePassRPC in der Konfiguration deaktiviert ist. Die Anwendung
 * startet damit normal; jede Auflösung scheitert mit {@link SecretUnavailableException.Reason#NOT_AVAILABLE} und
 * der konfigurierten Begründung, die die Oberfläche anzeigen kann.
 */
public final class UnavailableSecretProvider implements SecretProvider {

    private final String reason;

    public UnavailableSecretProvider(String reason) {
        this.reason = reason == null || reason.trim().isEmpty() ? "kein Security-Backend konfiguriert" : reason.trim();
    }

    @Override
    public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_AVAILABLE, ref, reason);
    }

    @Override
    public String toString() {
        return "UnavailableSecretProvider[" + reason + "]";
    }
}
