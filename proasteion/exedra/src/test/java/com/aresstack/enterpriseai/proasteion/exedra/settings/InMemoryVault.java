package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.adyton.SecretEntry;
import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretVault;
import com.aresstack.enterpriseai.adyton.VaultEntry;
import com.aresstack.enterpriseai.adyton.VaultEntryDraft;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** KeePass-Ersatz im Speicher: Einträge mit Benutzer, Passwort, URL und Zusatzfeldern, wie KeePassRPC sie liefert. */
final class InMemoryVault implements SecretProvider, SecretVault {

    private static final class Stored {
        final String userName;
        final String password;
        final String url;
        final Map<String, String> fields;

        Stored(String userName, String password, String url, Map<String, String> fields) {
            this.userName = userName;
            this.password = password;
            this.url = url;
            this.fields = new LinkedHashMap<String, String>(fields);
        }
    }

    private final Map<String, Stored> entries = new LinkedHashMap<String, Stored>();

    InMemoryVault put(String title, String userName, String password, String url) {
        entries.put(title, new Stored(userName, password, url, new LinkedHashMap<String, String>()));
        return this;
    }

    boolean contains(String title) {
        return entries.containsKey(title);
    }

    private static String title(SecretRef ref) {
        String id = ref.id();
        return id.toLowerCase(Locale.ROOT).startsWith("keepass:") ? id.substring("keepass:".length()).trim() : id;
    }

    private Stored find(SecretRef ref) throws SecretUnavailableException {
        Stored stored = ref == null ? null : entries.get(title(ref));
        if (stored == null) {
            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND, ref, "kein Eintrag");
        }
        return stored;
    }

    @Override
    public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        Stored stored = find(ref);
        return new SecretMaterial(ref, stored.userName, stored.password == null ? new char[0]
                : stored.password.toCharArray());
    }

    @Override
    public List<SecretEntry> entries() {
        List<SecretEntry> result = new ArrayList<SecretEntry>();
        for (Map.Entry<String, Stored> entry : entries.entrySet()) {
            result.add(new SecretEntry(entry.getKey(), entry.getValue().url));
        }
        return result;
    }

    @Override
    public String locationOf(SecretRef ref) throws SecretUnavailableException {
        return find(ref).url;
    }

    @Override
    public Map<String, String> attributesOf(SecretRef ref) throws SecretUnavailableException {
        return find(ref).fields;
    }

    @Override
    public List<VaultEntry> list() {
        List<VaultEntry> result = new ArrayList<VaultEntry>();
        for (Map.Entry<String, Stored> entry : entries.entrySet()) {
            result.add(new VaultEntry(entry.getKey(), entry.getValue().userName, entry.getValue().url,
                    entry.getValue().fields));
        }
        return result;
    }

    @Override
    public VaultEntryDraft read(String title) throws SecretUnavailableException {
        Stored stored = entries.get(title);
        if (stored == null) {
            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND, null, "kein Eintrag");
        }
        return new VaultEntryDraft(title, stored.userName, stored.password == null ? null
                : stored.password.toCharArray(), stored.url, stored.fields);
    }

    @Override
    public void save(VaultEntryDraft entry) {
        char[] password = entry.copyPassword();
        entries.put(entry.title(), new Stored(entry.userName(), password == null ? null : new String(password),
                entry.location(), entry.fields()));
    }

    @Override
    public void remove(String title) {
        entries.remove(title);
    }
}
