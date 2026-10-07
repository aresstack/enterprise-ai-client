package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

/**
 * Ungeprüfte Form von {@link SecretUnavailableException} für Schnittstellen, die keine geprüfte Ausnahme erlauben
 * ({@code TokenSource}, {@code BearerTokenSource}). Trägt nur Grund und Referenz, nie Secret-Material; die
 * Adapter geben ohnehin nur den Klassennamen weiter.
 */
public final class SecretAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final SecretUnavailableException.Reason reason;
    private final SecretRef ref;

    public SecretAccessException(SecretUnavailableException cause) {
        super(cause.getMessage(), cause);
        this.reason = cause.reason();
        this.ref = cause.ref();
    }

    public SecretUnavailableException.Reason reason() {
        return reason;
    }

    public SecretRef ref() {
        return ref;
    }

    /** Verständliche Meldung für die Oberfläche, nach Grund. */
    public static String describe(SecretUnavailableException.Reason reason, SecretRef ref) {
        String what = ref == null ? "Secret" : "Eintrag \"" + ref.id() + "\"";
        if (reason == null) {
            return what + " konnte nicht gelesen werden.";
        }
        switch (reason) {
            case NOT_AVAILABLE:
                return "KeePass ist nicht erreichbar (KeePass mit KeePassRPC-Plugin starten und Datenbank entsperren); "
                        + what + " konnte nicht gelesen werden.";
            case NOT_FOUND:
                return what + " existiert nicht in KeePass.";
            case ACCESS_DENIED:
                return "KeePass verweigert den Zugriff auf " + what + " (Pairing prüfen).";
            case CANCELLED:
                return "Das KeePass-Pairing wurde abgebrochen; " + what + " konnte nicht gelesen werden.";
            default:
                return what + " konnte nicht gelesen werden.";
        }
    }
}
