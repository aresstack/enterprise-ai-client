package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.ConfluenceEntryCheck;

import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.exedra.security.SwingPairingCallback;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SecretChecker;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.exedra.security.DialogCredentialPrompt;

import java.util.ArrayList;
import java.util.List;


/**
 * Die KeePass-Probe des Einstellungen-Dialogs: lässt sich vom Kompositionspunkt mit den KeePass-Einstellungen des
 * Entwurfs denselben Tresorzugang wie die Anwendung geben ({@link AdapterAssembly#keePassAccess}: Pairing-Dialog,
 * Pairing-Schlüssel in der konfigurierten Datei, damit ein hier erledigtes Pairing beim Start gilt) und liefert
 * nur die Dialoge dazu. Das Material sieht nur {@code proasteion.application.security}; die Meldung nennt nie
 * seinen Inhalt oder seine Länge (corenth {@code EXEDRA_MUST_STAY_THIN_UI_SHELL}: exedra kennt adyton nicht).
 */
public final class KeePassSecretChecker implements SecretChecker {

    private final PairingCallbackFactory pairing;

    /** Baut den Pairing-Dialog je Probe aus der Adresse des Entwurfs. */
    public interface PairingCallbackFactory {
        PairingPrompt forAddress(String keePassAddress);
    }

    /** Produktiv: der Swing-Pairing-Dialog. */
    public KeePassSecretChecker() {
        this(new PairingCallbackFactory() {
            @Override
            public PairingPrompt forAddress(String keePassAddress) {
                return new SwingPairingCallback(keePassAddress);
            }
        });
    }

    public KeePassSecretChecker(PairingCallbackFactory pairing) {
        if (pairing == null) {
            throw new IllegalArgumentException("pairing must not be null");
        }
        this.pairing = pairing;
    }

    @Override
    public SecretCheckResult check(KeePassConfig keePass, SecretRef ref) {
        if (keePass == null || ref == null) {
            throw new IllegalArgumentException("keePass and ref must not be null");
        }
        if (!keePass.enabled()) {
            return SecretCheckResult.failed("KeePassRPC ist ausgeschaltet; ohne KeePass gibt es keinen API-Key.");
        }
        KeePassAccess.Check result = access(keePass).check(ref);
        return result.ok() ? SecretCheckResult.ok(result.message()) : SecretCheckResult.failed(result.message());
    }

    @Override
    public String locationOf(KeePassConfig keePass, SecretRef ref) throws Exception {
        return access(keePass).locationOf(ref);
    }

    @Override
    public List<String> entryTitles(KeePassConfig keePass) throws Exception {
        if (keePass == null || !keePass.enabled()) {
            return new ArrayList<String>();
        }
        return access(keePass).titles();
    }

    @Override
    public KeePassAccess vault(KeePassConfig keePass) {
        return access(keePass);
    }

    @Override
    public String testConfluence(com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig network, String url, String user,
                                 char[] password, String certAlias) throws Exception {
        return ConfluenceEntryCheck.check(network, url, user, password, certAlias);
    }

    /** Der Tresorzugang des Entwurfs mit Pairing- und Zugangsdialog der Schale. */
    private KeePassAccess access(KeePassConfig keePass) {
        if (keePass == null) {
            return KeePassAccess.unavailable();
        }
        return AdapterAssembly.keePassAccess(keePass, pairing.forAddress(keePass.address()),
                new DialogCredentialPrompt());
    }
}
