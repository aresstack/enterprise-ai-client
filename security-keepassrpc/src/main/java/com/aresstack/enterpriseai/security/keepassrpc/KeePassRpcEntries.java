package com.aresstack.enterpriseai.security.keepassrpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Liest Benutzername und Passwort aus KeePassRPC-Login-DTOs (Version 1). Feldreihenfolge wie MainframeMate
 * {@code KeePassRpcClient.getUserName/getPassword}: direkte Felder, sonst {@code formFieldList} mit
 * {@code FFTusername}/{@code FFTpassword}.
 */
final class KeePassRpcEntries {

    private KeePassRpcEntries() {
    }

    /** Erster Eintrag, dessen Titel (getrimmt, ohne Groß-/Kleinschreibung) dem gesuchten entspricht. */
    static KeePassEntry exactTitleMatch(JsonElement result, String title) {
        if (result == null || !result.isJsonArray()) {
            return null;
        }
        String wanted = title.trim();
        for (JsonElement element : result.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String entryTitle = string(entry, "title");
            if (entryTitle != null && wanted.equalsIgnoreCase(entryTitle.trim())) {
                String password = firstNonNull(string(entry, "password"), formField(entry, "FFTpassword"));
                char[] passwordChars = password == null ? new char[0] : password.toCharArray();
                try {
                    return new KeePassEntry(entryTitle,
                            firstNonNull(string(entry, "usernameValue"), string(entry, "username"),
                                    formField(entry, "FFTusername")),
                            passwordChars, location(entry), attributes(entry));
                } finally {
                    java.util.Arrays.fill(passwordChars, '\0');
                }
            }
        }
        return null;
    }

    /** Alle Einträge mit Titel und Adresse, ohne Benutzer und Passwort (Auswahlliste). */
    static java.util.List<KeePassEntry> withoutSecrets(JsonElement result) {
        java.util.List<KeePassEntry> entries = new java.util.ArrayList<KeePassEntry>();
        if (result == null || !result.isJsonArray()) {
            return entries;
        }
        for (JsonElement element : result.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            String title = string(element.getAsJsonObject(), "title");
            if (title != null && !title.trim().isEmpty()) {
                entries.add(new KeePassEntry(title.trim(), "", new char[0], location(element.getAsJsonObject())));
            }
        }
        return entries;
    }

    /**
     * Erste http(s)-Adresse aus {@code uRLs} (bzw. {@code url}), wie MainframeMate sie liest. MainframeMate legt
     * Einträge ohne URL mit dem Titel als Platzhalter an; solche Werte sind keine Adresse.
     */
    static String location(JsonObject entry) {
        java.util.List<String> candidates = new java.util.ArrayList<String>();
        for (String name : new String[] {"uRLs", "url", "URLs"}) {
            JsonElement value = entry.get(name);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonPrimitive()) {
                candidates.add(value.getAsString());
            } else if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        candidates.add(item.getAsString());
                    }
                }
            }
        }
        for (String candidate : candidates) {
            String trimmed = candidate == null ? "" : candidate.trim();
            String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return trimmed;
            }
        }
        for (String candidate : candidates) {
            String trimmed = candidate == null ? "" : candidate.trim();
            if (trimmed.contains("://")) {
                return trimmed; // z. B. ftp://host für Mainframe-Einträge
            }
        }
        return null;
    }

    /**
     * Die benannten Felder aus {@code formFieldList} ohne Benutzer- und Passwortfeld, wie MainframeMate
     * {@code KeePassRpcClient.formFieldByName} sie für {@code MM_CertAlias}, {@code MM_RequiresLogin} usw. liest.
     */
    static java.util.Map<String, String> attributes(JsonObject entry) {
        java.util.Map<String, String> attributes = new java.util.LinkedHashMap<String, String>();
        JsonElement fields = entry.get("formFieldList");
        if (fields == null || !fields.isJsonArray()) {
            return attributes;
        }
        for (JsonElement element : fields.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject field = element.getAsJsonObject();
            String type = string(field, "type");
            String name = string(field, "name");
            String value = string(field, "value");
            if (name == null || name.trim().isEmpty() || value == null
                    || "FFTpassword".equals(type) || "FFTusername".equals(type)) {
                continue;
            }
            if (!attributes.containsKey(name.trim())) {
                attributes.put(name.trim(), value);
            }
        }
        return attributes;
    }

    private static String formField(JsonObject entry, String type) {
        JsonElement fields = entry.get("formFieldList");
        if (fields == null || !fields.isJsonArray()) {
            return null;
        }
        for (JsonElement element : fields.getAsJsonArray()) {
            if (element.isJsonObject() && type.equals(string(element.getAsJsonObject(), "type"))) {
                return string(element.getAsJsonObject(), "value");
            }
        }
        return null;
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
