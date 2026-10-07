package com.aresstack.enterpriseai.app.security;

import java.util.Arrays;

/** Hilfsfunktionen auf {@code char[]}, damit Secrets nie als {@link String} zwischengespeichert werden müssen. */
final class SecretChars {

    private SecretChars() {
    }

    /**
     * Kopie ohne führende und abschließende Leerzeichen (z. B. aus einem KeePass-Feld mit Zeilenumbruch). Das
     * Original bleibt unverändert; der Aufrufer löscht beide Arrays nach Gebrauch.
     */
    static char[] trimmedCopy(char[] chars) {
        int start = 0;
        int end = chars.length;
        while (start < end && Character.isWhitespace(chars[start])) {
            start++;
        }
        while (end > start && Character.isWhitespace(chars[end - 1])) {
            end--;
        }
        return Arrays.copyOfRange(chars, start, end);
    }
}
