package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordStore;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretVault;
import com.aresstack.enterpriseai.adyton.VaultEntry;
import com.aresstack.enterpriseai.adyton.VaultEntryDraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Passwortverwaltung der Oberfläche über dem {@link SecretVault} (KeePassRPC): jeder Zugriff läuft auf dem
 * Arbeits-Executor, das Ergebnis kommt über den UI-Executor. Fehler werden zu Meldungen wie in MainframeMate
 * ({@code KeePassNotAvailableException}); das Log nennt nur Titel und Grund, nie Passwörter.
 */
public final class VaultPasswordStore implements PasswordStore {

    private static final Logger LOG = Logger.getLogger(VaultPasswordStore.class.getName());

    private final Supplier<SecretVault> vault;
    private final Executor worker;
    private final Executor ui;
    private final ConnectionTest test;

    /** Verbindungstest eines Eintrags; Meldung bei Erfolg, sonst Ausnahme mit Ursache. */
    public interface ConnectionTest {
        String test(String url, String user, char[] password, String certAlias) throws Exception;
    }

    public VaultPasswordStore(Supplier<SecretVault> vault, Executor worker, Executor ui) {
        this(vault, worker, ui, null);
    }

    /** @param test Verbindungstest für Confluence-Einträge; {@code null} = keiner */
    public VaultPasswordStore(Supplier<SecretVault> vault, Executor worker, Executor ui, ConnectionTest test) {
        this.test = test;
        if (vault == null || worker == null || ui == null) {
            throw new IllegalArgumentException("vault, worker and ui must not be null");
        }
        this.vault = vault;
        this.worker = worker;
        this.ui = ui;
    }

    private interface Work<T> {
        T run(SecretVault vault) throws SecretUnavailableException;
    }

    private <T> void submit(final String action, final Work<T> work, final PasswordCallback<T> done) {
        try {
            worker.execute(() -> {
                T value = null;
                String error = null;
                try {
                    value = work.run(vault.get());
                } catch (SecretUnavailableException e) {
                    LOG.log(Level.INFO, "KeePass " + action + " fehlgeschlagen: " + e.getMessage());
                    error = describe(e);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "KeePass " + action + " fehlgeschlagen", e);
                    error = "KeePass-Zugriff fehlgeschlagen: " + e.getClass().getSimpleName();
                }
                final T delivered = value;
                final String failure = error;
                ui.execute(() -> done.done(delivered, failure));
            });
        } catch (RuntimeException rejected) {
            done.done(null, "KeePass-Zugriff konnte nicht gestartet werden.");
        }
    }

    @Override
    public void list(PasswordCallback<List<PasswordEntry>> done) {
        submit("Auflisten", v -> {
            List<PasswordEntry> entries = new ArrayList<PasswordEntry>();
            for (VaultEntry entry : v.list()) {
                entries.add(new PasswordEntry(entry.title(), entry.userName(), entry.location(), entry.fields(), null));
            }
            Collections.sort(entries, (a, b) -> a.title().compareToIgnoreCase(b.title()));
            return entries;
        }, done);
    }

    @Override
    public void read(final String title, PasswordCallback<PasswordEntry> done) {
        submit("Lesen von " + title, v -> {
            try (VaultEntryDraft draft = v.read(title)) {
                char[] password = draft.copyPassword();
                try {
                    return new PasswordEntry(draft.title(), draft.userName(), draft.location(), draft.fields(),
                            password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        }, done);
    }

    @Override
    public void save(final PasswordEntry entry, PasswordCallback<Void> done) {
        final char[] password = entry.copyPassword();
        submit("Speichern von " + entry.title(), v -> {
            try (VaultEntryDraft draft = new VaultEntryDraft(entry.title(), entry.userName(), password, entry.url(),
                    entry.fields())) {
                v.save(draft);
                return null;
            } finally {
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
            }
        }, done);
    }

    @Override
    public void remove(final String title, PasswordCallback<Void> done) {
        submit("Löschen von " + title, v -> {
            v.remove(title);
            return null;
        }, done);
    }

    @Override
    public void testConnection(final PasswordEntry entry, final PasswordCallback<String> done) {
        if (test == null) {
            done.done(null, "Verbindungstest in dieser Umgebung nicht verfügbar.");
            return;
        }
        final char[] password = entry.copyPassword();
        try {
            worker.execute(() -> {
                String message = null;
                String error = null;
                try {
                    message = test.test(entry.url(), entry.userName(), password, entry.field(PasswordEntry.CERT_ALIAS));
                } catch (Exception e) {
                    LOG.log(Level.INFO, "Verbindungstest für " + entry.title() + " fehlgeschlagen: " + e);
                    error = "Verbindung fehlgeschlagen: " + (e.getMessage() == null ? e.getClass().getSimpleName()
                            : e.getMessage());
                } finally {
                    if (password != null) {
                        Arrays.fill(password, '\0');
                    }
                }
                final String delivered = message;
                final String failure = error;
                ui.execute(() -> done.done(delivered, failure));
            });
        } catch (RuntimeException rejected) {
            done.done(null, "Verbindungstest konnte nicht gestartet werden.");
        }
    }

    /** Meldung je Grund, wie MainframeMate sie zeigt; nennt nie Material. */
    static String describe(SecretUnavailableException e) {
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
            default:
                return "KeePass-Zugriff fehlgeschlagen (" + e.reason() + ").";
        }
    }
}
