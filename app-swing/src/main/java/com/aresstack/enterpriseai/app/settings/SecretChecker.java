package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.domain.security.SecretRef;

import java.util.Collections;
import java.util.List;

/**
 * Prüft, ob ein KeePass-Eintrag unter den gegebenen KeePass-Einstellungen erreichbar ist. Die produktive
 * Umsetzung baut den KeePassRPC-Adapter und liegt deshalb in {@code app.composition}; sie darf blockieren
 * (Pairing-Dialog) und wird nie auf dem EDT gerufen. Das Ergebnis enthält nie Secret-Material.
 */
public interface SecretChecker {

    SecretCheckResult check(KeePassConfig keePass, SecretRef ref);

    /** Die URL im KeePass-Eintrag oder {@code null}; darf blockieren (Pairing), nie auf dem EDT. */
    default String locationOf(KeePassConfig keePass, SecretRef ref) throws Exception {
        return null;
    }

    /** Die Titel aller KeePass-Einträge zur Auswahl; darf blockieren (Pairing), nie auf dem EDT. */
    default List<String> entryTitles(KeePassConfig keePass) throws Exception {
        return Collections.emptyList();
    }
}
