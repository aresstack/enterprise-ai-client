package com.aresstack.enterpriseai.proasteion.exedra.security;

import com.aresstack.enterpriseai.proasteion.application.composition.UserPrompts;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;

/** Die Eingaben der Desktop-Anwendung für proasteion:application: Zugangsdaten- und Pairing-Dialog. */
public final class SwingUserPrompts implements UserPrompts {

    @Override
    public AskedSecretProvider.CredentialPrompt credentials() {
        return new DialogCredentialPrompt();
    }

    @Override
    public PairingPrompt pairing(String keePassAddress) {
        return new SwingPairingCallback(keePassAddress);
    }
}
