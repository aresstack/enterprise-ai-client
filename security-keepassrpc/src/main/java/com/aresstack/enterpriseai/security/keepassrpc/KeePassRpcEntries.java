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
                            passwordChars);
                } finally {
                    java.util.Arrays.fill(passwordChars, '\0');
                }
            }
        }
        return null;
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
