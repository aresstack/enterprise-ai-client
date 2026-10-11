package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;

/** Ohne Oberfläche (Tests, Werkzeuge): keine Eingabe; Zugang „Manuell“ scheitert, Pairing gilt als abgebrochen. */
public final class RefusingPrompts implements UserPrompts {

    @Override
    public AskedSecretProvider.CredentialPrompt credentials() {
        return new AskedSecretProvider.CredentialPrompt() {
            @Override
            public AskedSecretProvider.Answer ask(SecretRef ref, String label)
                    throws AskedSecretProvider.Unavailable {
                throw new AskedSecretProvider.Unavailable(
                        "Manuelle Zugangsdaten brauchen ein Fenster zur Eingabe");
            }
        };
    }

    @Override
    public PairingPrompt pairing(String keePassAddress) {
        return new PairingPrompt() {
            @Override
            public char[] requestPairingPassword(String clientDisplayName) {
                return null;
            }
        };
    }
}
