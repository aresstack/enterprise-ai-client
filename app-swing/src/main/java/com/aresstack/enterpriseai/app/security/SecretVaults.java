package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.security.api.SecretVault;
import com.aresstack.enterpriseai.security.api.VaultEntry;
import com.aresstack.enterpriseai.security.api.VaultEntryDraft;

import java.util.List;

/** Die Eintragsverwaltung hinter einem {@link SecretProvider}, sofern sein Backend eine hat (KeePassRPC). */
public final class SecretVaults {

    private SecretVaults() {
    }

    /** Der Provider selbst, wenn er Einträge verwalten kann, sonst eine Verwaltung, die jeden Aufruf ablehnt. */
    public static SecretVault of(SecretProvider provider) {
        if (provider instanceof SecretVault) {
            return (SecretVault) provider;
        }
        return new Unavailable();
    }

    private static final class Unavailable implements SecretVault {

        private static SecretUnavailableException unavailable() {
            return new SecretUnavailableException(SecretUnavailableException.Reason.NOT_AVAILABLE, null,
                    "KeePassRPC ist ausgeschaltet");
        }

        @Override
        public List<VaultEntry> list() throws SecretUnavailableException {
            throw unavailable();
        }

        @Override
        public VaultEntryDraft read(String title) throws SecretUnavailableException {
            throw unavailable();
        }

        @Override
        public void save(VaultEntryDraft entry) throws SecretUnavailableException {
            throw unavailable();
        }

        @Override
        public void remove(String title) throws SecretUnavailableException {
            throw unavailable();
        }
    }
}
