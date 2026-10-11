package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ein Eintrag der Passwortverwaltung wie in MainframeMate ({@code ShowPasswordsMenuCommand.KeePassEntry}): ID
 * (= KeePass-Titel), Benutzer, URL und die Zusatzfelder {@code MM_*}; das Passwort nur, wenn es gelesen oder
 * eingegeben wurde. {@link #wipe()} überschreibt es.
 */
public final class PasswordEntry {

    public static final String DISPLAY_NAME = "MM_DisplayName";
    public static final String CATEGORY = "MM_Category";
    public static final String NETWORK_ZONE = "MM_NetworkZone";
    public static final String REQUIRES_LOGIN = "MM_RequiresLogin";
    public static final String USE_PROXY = "MM_UseProxy";
    public static final String AUTO_INDEX = "MM_AutoIndex";
    public static final String SAVE_PASSWORD = "MM_SavePassword";
    public static final String SESSION_CACHE = "MM_SessionCache";
    public static final String CERT_ALIAS = "MM_CertAlias";
    public static final String ZONE_EXTERN = "EXTERN";
    public static final String ZONE_INTERN = "INTERN";

    private final String title;
    private final String userName;
    private final String url;
    private final Map<String, String> fields;
    private final char[] password;

    public PasswordEntry(String title, String userName, String url, Map<String, String> fields, char[] password) {
        this.title = title == null ? "" : title.trim();
        this.userName = userName == null ? "" : userName;
        this.url = url == null ? "" : url.trim();
        this.fields = fields == null ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
        this.password = password == null ? null : Arrays.copyOf(password, password.length);
    }

    /** Die Kategorien aus MainframeMate. */
    public static List<String> categories() {
        return Collections.unmodifiableList(Arrays.asList("Mainframe", "Wiki", "SP", "Confluence"));
    }

    public String title() {
        return title;
    }

    public String userName() {
        return userName;
    }

    public String url() {
        return url;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public String field(String name) {
        String value = fields.get(name);
        return value == null ? "" : value;
    }

    /** Ein Ja/Nein-Feld; fehlt es, gilt {@code fallback}. */
    public boolean flag(String name, boolean fallback) {
        String value = fields.get(name);
        return value == null || value.trim().isEmpty() ? fallback : "true".equalsIgnoreCase(value.trim());
    }

    public String category() {
        return field(CATEGORY);
    }

    public String displayName() {
        return field(DISPLAY_NAME);
    }

    public boolean intern() {
        return ZONE_INTERN.equalsIgnoreCase(field(NETWORK_ZONE).trim());
    }

    /** {@code true}, wenn die Kategorie (ohne Groß-/Kleinschreibung) einen der Namen trägt. */
    public boolean isCategory(String name) {
        return category().trim().toLowerCase(Locale.ROOT).equals(name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    /** Eine Kopie des Passworts oder {@code null}, wenn es nicht gelesen wurde. */
    public char[] copyPassword() {
        return password == null ? null : Arrays.copyOf(password, password.length);
    }

    public boolean hasPassword() {
        return password != null;
    }

    public void wipe() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public String toString() {
        return "PasswordEntry[" + title + "]";
    }
}
