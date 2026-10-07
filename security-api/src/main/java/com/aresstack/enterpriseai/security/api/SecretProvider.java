package com.aresstack.enterpriseai.security.api;

import com.aresstack.enterpriseai.domain.security.SecretRef;

/**
 * Neutraler Port zum Auflösen eines {@link SecretRef} in kurzlebiges {@link SecretMaterial}.
 *
 * <p>Implementiert von Security-Adaptern (z. B. {@code security-keepassrpc}), benutzt ausschließlich von
 * Adaptern, die sich bei einem externen System authentifizieren (z. B. {@code source-confluence}).
 * Domain, Application und UI reichen nur den {@link SecretRef} weiter.
 *
 * <p>Vorbild: corenth {@code adyton.SecretMaterialProvider}, ohne dessen Broker-/Lease-/Cache-Schicht, die
 * dieses Projekt (noch) nicht braucht. Implementierungen dürfen interaktiv sein (z. B. Pairing) und blockieren;
 * sie werden nicht auf dem Swing-EDT aufgerufen.
 */
public interface SecretProvider {

    /**
     * Löst die Referenz auf. Der Aufrufer besitzt das Ergebnis und muss es schließen.
     *
     * @throws SecretUnavailableException wenn das Backend nicht erreichbar ist, der Eintrag fehlt, der Zugriff
     *                                    abgelehnt oder vom Benutzer abgebrochen wurde
     */
    SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException;

    /**
     * Löst die Referenz auf, übergibt das Material an {@code use} und löscht es danach in jedem Fall.
     * Bevorzugter Weg für Adapter: das Material verlässt den Aufruf nicht.
     */
    default <T, X extends Exception> T withSecret(SecretRef ref, SecretFunction<T, X> use)
            throws SecretUnavailableException, X {
        if (use == null) {
            throw new IllegalArgumentException("Verwendung darf nicht null sein");
        }
        SecretMaterial material = resolve(ref);
        if (material == null) {
            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND, ref,
                    "Secret-Provider lieferte kein Material");
        }
        try {
            return use.apply(material);
        } finally {
            material.close();
        }
    }
}
