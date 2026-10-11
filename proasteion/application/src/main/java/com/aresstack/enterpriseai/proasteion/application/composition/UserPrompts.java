package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassPairingCallback;

/**
 * Was der Kompositionspunkt vom Benutzer erfragen muss (Zugang „Manuell“, KeePass-Pairing). proasteion:application
 * kennt keine Oberfläche; die Schale (exedra) liefert die Dialoge, ohne Oberfläche gilt {@link RefusingPrompts}.
 */
public interface UserPrompts {

    AskedSecretProvider.CredentialPrompt credentials();

    KeePassPairingCallback pairing(String keePassAddress);
}
