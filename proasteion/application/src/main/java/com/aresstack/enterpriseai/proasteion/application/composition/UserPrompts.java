package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;
import com.aresstack.enterpriseai.source.api.OneTimeCodePrompt;

/**
 * Was der Kompositionspunkt vom Benutzer erfragen muss (Zugang „Manuell“, KeePass-Pairing). proasteion:application
 * kennt keine Oberfläche; die Schale (exedra) liefert die Dialoge, ohne Oberfläche gilt {@link RefusingPrompts}.
 */
public interface UserPrompts {

    AskedSecretProvider.CredentialPrompt credentials();

    PairingPrompt pairing(String keePassAddress);

    /** Einmalcode einer Quelle (Zwei-Faktor-Plugin vor Confluence-Dateien); ohne Oberfläche: abgebrochen. */
    default OneTimeCodePrompt oneTimeCodes() {
        return OneTimeCodePrompt.refusing();
    }
}
