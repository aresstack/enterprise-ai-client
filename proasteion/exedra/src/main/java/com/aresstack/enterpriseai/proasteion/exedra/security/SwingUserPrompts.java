package com.aresstack.enterpriseai.proasteion.exedra.security;

import com.aresstack.enterpriseai.proasteion.application.composition.UserPrompts;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.ManualCredentials;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;

/** Die Eingaben der Desktop-Anwendung für proasteion:application: Zugangsdaten- und Pairing-Dialog. */
public final class SwingUserPrompts implements UserPrompts {

    private final ManualCredentials session;

    /** Mit eigener, leerer Sitzung der manuellen Zugangsdaten. */
    public SwingUserPrompts() {
        this(new ManualCredentials());
    }

    /** Mit der Sitzung der Anwendung, die alle Dialoge und Proben teilen. */
    public SwingUserPrompts(ManualCredentials session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        this.session = session;
    }

    @Override
    public AskedSecretProvider.CredentialPrompt credentials() {
        return new DialogCredentialPrompt(session);
    }

    @Override
    public PairingPrompt pairing(String keePassAddress) {
        return new SwingPairingCallback(keePassAddress);
    }
}
