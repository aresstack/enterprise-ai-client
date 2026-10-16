package com.aresstack.enterpriseai.proasteion.application.memory;

import java.util.regex.Pattern;

/** Recognizes text that looks like it carries a secret (password, token, API key, private key). */
final class SecretGuard {

    private static final Pattern[] SECRETS = {
            Pattern.compile("(?i)\\b(passwor[dt]|kennwort|passwd|pwd|pin|secret|geheimnis|token|api[-_ ]?key"
                    + "|access[-_ ]?key|client[-_ ]?secret|zugangsdaten)\\b\\s*(ist|lautet|is|=|:)\\s*\\S+"),
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{8,}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\b(sk|pk|rk)-[A-Za-z0-9_-]{16,}"),
            Pattern.compile("\\b(ghp|gho|ghs|github_pat|xox[abp])_[A-Za-z0-9_]{16,}"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            // Long key-like strings mixing letters and digits (no spaces), e.g. raw tokens.
            Pattern.compile("(?=[A-Za-z0-9+/_=-]*[0-9])(?=[A-Za-z0-9+/_=-]*[A-Za-z])[A-Za-z0-9+/_=-]{32,}")};

    private SecretGuard() {
    }

    static boolean looksSecret(String text) {
        if (text == null) {
            return false;
        }
        for (Pattern pattern : SECRETS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
