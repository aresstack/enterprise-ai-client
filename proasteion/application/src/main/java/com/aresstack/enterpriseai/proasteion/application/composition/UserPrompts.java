package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;

/**
 * Was der Kompositionspunkt vom Benutzer erfragen muss (Zugang „Manuell“, KeePass-Pairing). proasteion:application
 * kennt keine Oberfläche; die Schale (exedra) liefert die Dialoge, ohne Oberfläche gilt {@link RefusingPrompts}.
 */
public interface UserPrompts {

    AskedSecretProvider.CredentialPrompt credentials();

    PairingPrompt pairing(String keePassAddress);
}
