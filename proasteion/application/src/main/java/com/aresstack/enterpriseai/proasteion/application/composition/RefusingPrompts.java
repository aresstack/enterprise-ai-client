package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException.Reason;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassPairingCallback;

/** Ohne Oberfläche (Tests, Werkzeuge): keine Eingabe; Zugang „Manuell“ scheitert, Pairing gilt als abgebrochen. */
public final class RefusingPrompts implements UserPrompts {

    @Override
    public AskedSecretProvider.CredentialPrompt credentials() {
        return new AskedSecretProvider.CredentialPrompt() {
            @Override
            public AskedSecretProvider.Answer ask(SecretRef ref, String label) throws SecretUnavailableException {
                throw new SecretUnavailableException(Reason.NOT_AVAILABLE, ref,
                        "Manuelle Zugangsdaten brauchen ein Fenster zur Eingabe");
            }
        };
    }

    @Override
    public KeePassPairingCallback pairing(String keePassAddress) {
        return new KeePassPairingCallback() {
            @Override
            public char[] requestPairingPassword(String clientDisplayName) {
                return null;
            }
        };
    }
}
