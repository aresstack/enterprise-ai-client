package com.aresstack.enterpriseai.adyton;

import java.util.List;
import java.util.Locale;

/** Titelvergleich der Tresor-Einträge wie KeePassRPC: getrimmt und ohne Groß-/Kleinschreibung. */
public final class VaultTitles {

    private VaultTitles() {
    }

    /** Der normalisierte Titel; {@code null} wird leer. */
    public static String normalize(String title) {
        return title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean same(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    public static boolean containsTitle(List<VaultEntry> entries, String title) {
        if (entries != null) {
            for (VaultEntry entry : entries) {
                if (entry != null && same(entry.title(), title)) {
                    return true;
                }
            }
        }
        return false;
    }
}
