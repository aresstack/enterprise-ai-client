package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import java.util.Collections;
import java.util.List;

/**
 * Prüft, ob ein KeePass-Eintrag unter den gegebenen KeePass-Einstellungen erreichbar ist. Die produktive
 * Umsetzung baut den KeePassRPC-Adapter und liegt deshalb in {@code exedra.assembly} über {@code AdapterAssembly.keePassAccess}; sie darf blockieren
 * (Pairing-Dialog) und wird nie auf dem EDT gerufen. Das Ergebnis enthält nie Secret-Material.
 */
public interface SecretChecker {

    SecretCheckResult check(KeePassConfig keePass, SecretRef ref);

    /** Die URL im KeePass-Eintrag oder {@code null}; darf blockieren (Pairing), nie auf dem EDT. */
    default String locationOf(KeePassConfig keePass, SecretRef ref) throws Exception {
        return null;
    }

    /** Die Eintragsverwaltung (Passwortverwaltung) mit diesen KeePass-Einstellungen; nie auf dem EDT benutzen. */
    default KeePassAccess vault(KeePassConfig keePass) {
        return KeePassAccess.unavailable();
    }

    /**
     * „🔗 Verbindung testen“ für einen Confluence-Eintrag mit den eingegebenen Werten; Meldung bei Erfolg, sonst
     * eine Ausnahme mit der Ursache. Nie auf dem EDT.
     */
    default String testConfluence(com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig network, String url,
                                  String user, char[] password, String certAlias) throws Exception {
        throw new UnsupportedOperationException("Verbindungstest hier nicht verfügbar");
    }

    /** Die Titel aller KeePass-Einträge zur Auswahl; darf blockieren (Pairing), nie auf dem EDT. */
    default List<String> entryTitles(KeePassConfig keePass) throws Exception {
        return Collections.emptyList();
    }
}
