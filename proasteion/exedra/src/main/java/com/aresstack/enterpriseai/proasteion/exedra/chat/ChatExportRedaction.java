package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schwärzt Zugangsdaten im Chat-Export, bevor er geschrieben wird: API-Keys, Authorization-Header, Tokens,
 * Session-Cookies, Passwörter und Einmalcodes. Zwei Wege:
 *
 * <ul>
 *   <li>ein Feld, dessen Name nach Secret aussieht ({@code password}, {@code proxyPassword}, {@code accessToken},
 *       {@code otpCode}, {@code sessionCookie} …), verliert seinen Wert ganz;</li>
 *   <li>in jedem Text werden bekannte Muster ersetzt ({@code Authorization: …}, {@code Bearer …},
 *       {@code Cookie: …}, {@code JSESSIONID=…}, {@code password=…} in URLs, {@code sk-…}-Schlüssel, JSON-Felder
 *       wie {@code "password": "…"}); der übrige Text bleibt, wie er gespeichert ist.</li>
 * </ul>
 *
 * Zählt die Ersetzungen, damit der Export sagt, dass geschwärzt wurde.
 */
final class ChatExportRedaction {

    static final String REDACTED = "[REDACTED]";

    /** Wortteile eines Feldnamens, die ein Secret kennzeichnen (nach camelCase, {@code _}, {@code -}, {@code .}). */
    private static final Set<String> SECRET_WORDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "password", "passwort", "passwd", "pwd", "secret", "token", "apikey", "authorization", "cookie",
            "cookies", "otp", "otpcode", "jsessionid", "credential", "credentials", "bearer", "passphrase")));

    private static final String KEY_NAMES = "password|passwort|passwd|pwd|token|access_token|refresh_token|"
            + "id_token|api_key|apikey|api-key|key|otp|otpcode|otp_code|secret|client_secret|os_password|"
            + "jsessionid|session|sessionid|auth";

    private final List<Rule> rules = new ArrayList<Rule>();
    private int count;

    ChatExportRedaction() {
        // Header-Zeilen: der Name bleibt, der Wert geht
        rules.add(new Rule("(?i)(\\bauthorization\\s*[:=]\\s*)(?:(?:bearer|basic|negotiate|ntlm|token)\\s+)?"
                + "[^\\s,;\"']+", "$1" + REDACTED));
        rules.add(new Rule("(?i)(\\bproxy-authorization\\s*[:=]\\s*)(?:(?:bearer|basic|negotiate|ntlm)\\s+)?"
                + "[^\\s,;\"']+", "$1" + REDACTED));
        rules.add(new Rule("(?i)\\b(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}", "$1" + REDACTED));
        // Cookie-Header: alle name=wert-Paare, nicht der Rest der Zeile
        rules.add(new Rule("(?i)(\\b(?:set-)?cookie\\s*:\\s*)(?:[A-Za-z0-9._$-]+=[^;\\s\"']*(?:;\\s*)?)+",
                "$1" + REDACTED + " "));
        rules.add(new Rule("(?i)(\\b(?:x-api-key|api-key|x-auth-token|mcp-token)\\s*:\\s*)[^\\s\"]+",
                "$1" + REDACTED));
        // Session-Cookies als name=wert
        rules.add(new Rule("(?i)(\\b(?:jsessionid|seraph\\.[a-z.]+|atlassian\\.xsrf\\.token|crowd\\.token_key|"
                + "cloud\\.session\\.token)=)[^;\\s&\"']+", "$1" + REDACTED));
        // Parameter in URLs, Formularen und Konfigurationszeilen
        rules.add(new Rule("(?i)((?:^|[?&;\\s,(])(?:" + KEY_NAMES + ")=)[^&\\s\"';]+", "$1" + REDACTED));
        // JSON- oder Properties-artige Felder innerhalb von Text
        rules.add(new Rule("(?i)(\"[A-Za-z0-9_.-]*(?:password|passwort|token|api_?key|secret|otp_?code|"
                + "authorization|cookie)\"\\s*:\\s*\")(?:[^\"\\\\]|\\\\.)*(\")", "$1" + REDACTED + "$2"));
        // OpenAI-artige Schlüssel
        rules.add(new Rule("\\bsk-[A-Za-z0-9_-]{16,}", REDACTED));
    }

    /** Wie viele Werte bisher geschwärzt wurden. */
    int count() {
        return count;
    }

    /** Schwärzt den Baum an Ort und Stelle und gibt ihn zurück. */
    JsonElement apply(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : new ArrayList<Map.Entry<String, JsonElement>>(object.entrySet())) {
                JsonElement value = entry.getValue();
                if (isSecretKey(entry.getKey()) && value != null && value.isJsonPrimitive()
                        && !value.getAsString().isEmpty() && !REDACTED.equals(value.getAsString())) {
                    object.add(entry.getKey(), new JsonPrimitive(REDACTED));
                    count++;
                } else {
                    object.add(entry.getKey(), apply(value));
                }
            }
            return object;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                array.set(i, apply(array.get(i)));
            }
            return array;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isString() ? new JsonPrimitive(text(primitive.getAsString())) : primitive;
    }

    /** Schwärzt die bekannten Muster in einem Text. */
    String text(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern.matcher(result);
            StringBuffer out = null;
            while (matcher.find()) {
                if (out == null) {
                    out = new StringBuffer();
                }
                matcher.appendReplacement(out, rule.replacement);
                count++;
            }
            if (out != null) {
                matcher.appendTail(out);
                result = out.toString();
            }
        }
        return result;
    }

    /** Ob ein Feldname ein Secret bezeichnet ({@code proxyPassword}, {@code api_key}, {@code otpCode} …). */
    static boolean isSecretKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String compact = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (compact.equals("apikey") || compact.endsWith("apikey") || compact.equals("otpcode")) {
            return true;
        }
        for (String word : key.split("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")) {
            if (SECRET_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static final class Rule {
        final Pattern pattern;
        final String replacement;

        Rule(String regex, String replacement) {
            this.pattern = Pattern.compile(regex);
            this.replacement = replacement;
        }
    }
}
