package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.SecretProbe;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.app.settings.SecretChecker;
import com.aresstack.enterpriseai.app.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingCallback;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;

import java.util.Locale;


/**
 * Die KeePass-Probe des Einstellungen-Dialogs: baut mit den KeePass-Einstellungen des Entwurfs denselben
 * {@code KeePassRpcSecretProvider} wie die Anwendung (Pairing-Dialog, Pairing-Schlüssel in der konfigurierten
 * Datei, damit ein hier erledigtes Pairing beim Start gilt) und löst die Referenz einmal auf. Das Material wird
 * nur in {@code app.security.SecretProbe} daraufhin angesehen, ob das Passwortfeld leer ist; die Meldung nennt
 * nie seinen Inhalt oder seine Länge. Liegt in {@code app.composition}, weil nur dort Adapter entstehen (AP23).
 */
public final class KeePassSecretChecker implements SecretChecker {

    private final PairingCallbackFactory pairing;

    /** Baut den Pairing-Dialog je Probe aus der Adresse des Entwurfs. */
    public interface PairingCallbackFactory {
        KeePassPairingCallback forAddress(String keePassAddress);
    }

    /** Produktiv: der Swing-Pairing-Dialog. */
    public KeePassSecretChecker() {
        this(new PairingCallbackFactory() {
            @Override
            public KeePassPairingCallback forAddress(String keePassAddress) {
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
        String address = keePass.rpc().host() + ":" + keePass.rpc().port();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        SecretProvider provider = AdapterAssembly.secrets(keePass, pairing.forAddress(address), keyStore);
        try {
            boolean filled = SecretProbe.isFilled(provider, ref);
            if (filled) {
                return SecretCheckResult.ok("Eintrag „" + title(ref) + "“ gefunden; das Passwortfeld ist gefüllt.");
            }
            return SecretCheckResult.failed("Eintrag „" + title(ref) + "“ gefunden, aber das Passwortfeld ist leer."
                    + " Bitte den API-Key dort eintragen.");
        } catch (SecretUnavailableException e) {
            return SecretCheckResult.failed(describe(e, ref, address));
        }
    }

    /** Der Titel, wie KeePass ihn kennt: die Referenz ohne das optionale Präfix {@code keepass:}. */
    static String title(SecretRef ref) {
        String id = ref.id();
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.startsWith("keepass:") ? id.substring("keepass:".length()).trim() : id;
    }

    static String describe(SecretUnavailableException e, SecretRef ref, String address) {
        switch (e.reason()) {
            case NOT_FOUND:
                return "Kein KeePass-Eintrag mit dem Titel „" + title(ref) + "“. Der Titel muss genau "
                        + "übereinstimmen (ohne das Präfix keepass:).";
            case NOT_AVAILABLE:
                return "KeePass ist unter " + address + " nicht erreichbar. Läuft KeePass mit dem Plugin KeePassRPC, "
                        + "und ist die Datenbank entsperrt?";
            case ACCESS_DENIED:
                return "KeePass lehnt den Zugriff ab. Das Pairing ist ungültig oder wurde in KeePass widerrufen; "
                        + "bitte die Verbindung in KeePassRPC entfernen und erneut prüfen.";
            case CANCELLED:
                return "Das Pairing wurde abgebrochen.";
            default:
                return "KeePass-Zugriff fehlgeschlagen (" + e.reason() + ").";
        }
    }
}
