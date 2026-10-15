package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.adyton.SecretEntry;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretVault;
import com.aresstack.enterpriseai.adyton.VaultEntry;
import com.aresstack.enterpriseai.adyton.VaultEntryDraft;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Der Tresorzugang für die Schale (corenth {@code EXEDRA_MUST_STAY_THIN_UI_SHELL}: exedra besitzt keinen
 * Credential-Fluss und kennt adyton nicht): Probe, Eintragstitel, URL, Bearer-Token, Passwortverwaltung und
 * {@code MM_CertAlias} über dem Security-Port, ohne adyton-Typen nach außen. Fehler kommen als
 * {@link Failure} mit der Meldung für den Benutzer; keine Meldung nennt Material. Jeder Aufruf darf blockieren
 * (Pairing-Dialog) und gehört nie auf den UI-Thread.
 */
public final class KeePassAccess {

    private static final Logger LOG = Logger.getLogger(KeePassAccess.class.getName());

    /** Der Name des Zertifikat-Felds im KeePass-Eintrag, wie MainframeMate ihn schreibt. */
    public static final String MM_CERT_ALIAS = "MM_CertAlias";

    private final SecretProvider secrets;
    private final String address;

    /**
     * @param secrets der Security-Port; {@code null} = KeePass aus (jeder Zugriff scheitert mit NOT_AVAILABLE)
     * @param address KeePass-Adresse {@code host:port} für Meldungen; {@code null} = keine
     */
    public KeePassAccess(SecretProvider secrets, String address) {
        this.secrets = secrets;
        this.address = address;
    }

    /** Ohne KeePass: jeder Zugriff scheitert mit „nicht erreichbar“. */
    public static KeePassAccess unavailable() {
        return new KeePassAccess(null, null);
    }

    /** Fehler eines Tresorzugriffs mit der Meldung für den Benutzer; nie Material. */
    public static final class Failure extends Exception {

        private static final long serialVersionUID = 1L;

        private final boolean temporary;

        Failure(String message, boolean temporary) {
            super(message);
            this.temporary = temporary;
        }

        /**
         * Ob KeePass nur vorübergehend nicht erreichbar war (Timeout, abgebrochene Verbindung): dann lohnt eine spätere
         * Wiederholung. Fehlender Eintrag, abgelehnter Zugriff und abgebrochenes Pairing sind nie vorübergehend.
         */
        public boolean temporary() {
            return temporary;
        }
    }

    /** Ergebnis der Probe: gefunden mit gefülltem Passwortfeld oder Grund; nie Material. */
    public static final class Check {
        private final boolean ok;
        private final String message;

        Check(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public boolean ok() {
            return ok;
        }

        public String message() {
            return message;
        }
    }

    /** Ein Eintrag der Passwortverwaltung; das Passwort nur nach {@link #read}, sonst {@code null}. */
    public static final class Entry {
        private final String title;
        private final String userName;
        private final String location;
        private final Map<String, String> fields;
        private final char[] password;

        public Entry(String title, String userName, String location, Map<String, String> fields, char[] password) {
            this.title = title;
            this.userName = userName;
            this.location = location;
            this.fields = fields;
            this.password = password;
        }

        public String title() {
            return title;
        }

        public String userName() {
            return userName;
        }

        public String location() {
            return location;
        }

        public Map<String, String> fields() {
            return fields;
        }

        /** Das Passwort; der Empfänger löscht es nach Gebrauch. */
        public char[] password() {
            return password;
        }
    }

    /** Löst die Referenz einmal auf und meldet, ob das Passwortfeld gefüllt ist (Einstellungen „Prüfen“). */
    public Check check(SecretRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        try {
            if (SecretProbe.isFilled(provider(ref), ref)) {
                return new Check(true, "Eintrag „" + title(ref) + "“ gefunden; das Passwortfeld ist gefüllt.");
            }
            return new Check(false, "Eintrag „" + title(ref) + "“ gefunden, aber das Passwortfeld ist leer."
                    + " Bitte den API-Key dort eintragen.");
        } catch (SecretUnavailableException e) {
            return new Check(false, describe(e.reason(), ref, address));
        }
    }

    /** Die URL im KeePass-Eintrag oder {@code null}. */
    public String locationOf(SecretRef ref) throws Failure {
        try {
            return provider(ref).locationOf(ref);
        } catch (SecretUnavailableException e) {
            throw failure(e.reason(), ref, address);
        }
    }

    /** Das Bearer-Token eines Eintrags, wie der Chat-Adapter es liest. */
    public String token(SecretRef ref) throws Failure {
        try {
            return SecretProbe.token(provider(ref), ref);
        } catch (SecretAccessException e) {
            throw failure(e.reason(), e.ref(), address);
        } catch (SecretUnavailableException e) {
            throw failure(e.reason(), ref, address);
        }
    }

    /** Titel aller KeePass-Einträge, sortiert. */
    public List<String> titles() throws Failure {
        List<String> titles = new ArrayList<String>();
        try {
            for (SecretEntry entry : provider(null).entries()) {
                titles.add(entry.title());
            }
        } catch (SecretUnavailableException e) {
            throw failure(e.reason(), null, address);
        }
        Collections.sort(titles, String.CASE_INSENSITIVE_ORDER);
        return titles;
    }

    /** {@code MM_CertAlias} des Eintrags (wie MainframeMate ihn ablegt); leer, wenn keiner oder KeePass zu. */
    public String certificateAlias(String title) {
        if (secrets == null || title == null || title.trim().isEmpty()) {
            return "";
        }
        try {
            Map<String, String> attributes = secrets.attributesOf(SecretRef.of(title.trim()));
            String alias = attributes == null ? null : attributes.get(MM_CERT_ALIAS);
            return alias == null ? "" : alias.trim();
        } catch (SecretUnavailableException | IllegalArgumentException e) {
            return "";
        }
    }

    /** Schreibt {@code MM_CertAlias} in den Eintrag wie MainframeMate; ein Fehler kommt ins Protokoll. */
    public void storeCertificateAlias(String title, String alias) {
        if (secrets == null || title == null || title.trim().isEmpty()) {
            return;
        }
        try {
            secrets.setAttribute(SecretRef.of(title.trim()), MM_CERT_ALIAS, alias == null ? "" : alias.trim());
            LOG.info("KeePass: " + MM_CERT_ALIAS + " im Eintrag „" + title.trim() + "“ gesetzt: "
                    + (alias == null || alias.trim().isEmpty() ? "(ohne Zertifikat)" : alias.trim()));
        } catch (SecretUnavailableException | IllegalArgumentException e) {
            LOG.warning("KeePass: " + MM_CERT_ALIAS + " im Eintrag „" + title.trim() + "“ nicht geschrieben: "
                    + e.getMessage());
        }
    }

    /** Alle Einträge der Passwortverwaltung, ohne Passwörter, nach Titel sortiert. */
    public List<Entry> list() throws Failure {
        try {
            List<Entry> entries = new ArrayList<Entry>();
            for (VaultEntry entry : vault().list()) {
                entries.add(new Entry(entry.title(), entry.userName(), entry.location(), entry.fields(), null));
            }
            Collections.sort(entries, (a, b) -> a.title().compareToIgnoreCase(b.title()));
            return entries;
        } catch (SecretUnavailableException e) {
            throw vaultFailure("Auflisten", e);
        }
    }

    /** Ein Eintrag mit Passwort (Kopie; der Empfänger löscht sie). */
    public Entry read(String title) throws Failure {
        try (VaultEntryDraft draft = vault().read(title)) {
            return new Entry(draft.title(), draft.userName(), draft.location(), draft.fields(),
                    draft.copyPassword());
        } catch (SecretUnavailableException e) {
            throw vaultFailure("Lesen von " + title, e);
        }
    }

    /** Legt einen Eintrag an oder ändert ihn; löscht das übergebene Passwort danach. */
    public void save(String title, String userName, char[] password, String url, Map<String, String> fields)
            throws Failure {
        try (VaultEntryDraft draft = new VaultEntryDraft(title, userName, password, url, fields)) {
            vault().save(draft);
        } catch (SecretUnavailableException e) {
            throw vaultFailure("Speichern von " + title, e);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * Legt einen neuen Eintrag an und ändert nie einen bestehenden (Titel getrimmt, ohne Groß-/Kleinschreibung);
     * bei Kollision scheitert der Aufruf mit der Bitte um einen anderen Titel. Löscht das Passwort danach.
     */
    public void create(String title, String userName, char[] password, String url, Map<String, String> fields)
            throws Failure {
        try (VaultEntryDraft draft = new VaultEntryDraft(title, userName, password, url, fields)) {
            vault().create(draft);
        } catch (SecretUnavailableException e) {
            throw vaultFailure("Anlegen von " + title, e);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    public void remove(String title) throws Failure {
        try {
            vault().remove(title);
        } catch (SecretUnavailableException e) {
            throw vaultFailure("Löschen von " + title, e);
        }
    }

    private SecretProvider provider(SecretRef ref) throws SecretUnavailableException {
        if (secrets == null) {
            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_AVAILABLE, ref,
                    "KeePassRPC ist ausgeschaltet");
        }
        return secrets;
    }

    private SecretVault vault() {
        return SecretVaults.of(secrets);
    }

    private static Failure vaultFailure(String action, SecretUnavailableException e) {
        LOG.info("KeePass " + action + " fehlgeschlagen: " + e.getMessage());
        return new Failure(describeVault(e), e.reason() == SecretUnavailableException.Reason.NOT_AVAILABLE);
    }

    private static Failure failure(SecretUnavailableException.Reason reason, SecretRef ref, String address) {
        return new Failure(describe(reason, ref, address), reason == SecretUnavailableException.Reason.NOT_AVAILABLE);
    }

    /** Der Titel, wie KeePass ihn kennt: die Referenz ohne das optionale Präfix {@code keepass:}. */
    static String title(SecretRef ref) {
        String id = ref.id();
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.startsWith("keepass:") ? id.substring("keepass:".length()).trim() : id;
    }

    /** Meldung der Proben je Grund; nennt Titel und Adresse, nie Material. */
    static String describe(SecretUnavailableException.Reason reason, SecretRef ref, String address) {
        if (reason == null) {
            return "KeePass-Zugriff fehlgeschlagen.";
        }
        switch (reason) {
            case NOT_FOUND:
                return ref == null ? "Kein KeePass-Eintrag mit diesem Titel."
                        : "Kein KeePass-Eintrag mit dem Titel „" + title(ref) + "“. Der Titel muss genau "
                        + "übereinstimmen.";
            case NOT_AVAILABLE:
                return "KeePass ist unter " + address + " nicht erreichbar. Läuft KeePass mit dem Plugin KeePassRPC, "
                        + "und ist die Datenbank entsperrt?";
            case ACCESS_DENIED:
                return "KeePass lehnt den Zugriff ab. Das Pairing ist ungültig oder wurde in KeePass widerrufen; "
                        + "bitte die Verbindung in KeePassRPC entfernen und erneut prüfen.";
            case CANCELLED:
                return "Das Pairing wurde abgebrochen.";
            default:
                return "KeePass-Zugriff fehlgeschlagen (" + reason + ").";
        }
    }

    /** Meldung der Passwortverwaltung je Grund, wie MainframeMate sie zeigt; nennt nie Material. */
    static String describeVault(SecretUnavailableException e) {
        switch (e.reason()) {
            case NOT_AVAILABLE:
                return "KeePass ist nicht erreichbar. Ist KeePass mit dem Plugin KeePassRPC gestartet und die "
                        + "Datenbank geöffnet?";
            case NOT_FOUND:
                return "Kein KeePass-Eintrag mit diesem Titel.";
            case ACCESS_DENIED:
                return "KeePass lehnt den Zugriff ab" + (e.getCause() == null ? "" : " (" + e.getCause().getMessage()
                        + ")") + ". Möglicherweise muss das Pairing erneut durchgeführt werden.";
            case CANCELLED:
                return "Das KeePassRPC-Pairing wurde abgebrochen.";
            case ALREADY_EXISTS:
                return "In KeePass gibt es bereits einen Eintrag mit diesem Titel; er bleibt unverändert. Bitte einen "
                        + "anderen Titel wählen oder den Eintrag direkt in KeePass bearbeiten.";
            default:
                return "KeePass-Zugriff fehlgeschlagen (" + e.reason() + ").";
        }
    }
}
