package com.aresstack.enterpriseai.proasteion.exedra.security;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;

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
