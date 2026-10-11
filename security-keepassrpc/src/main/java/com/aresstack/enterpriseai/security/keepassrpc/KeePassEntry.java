package com.aresstack.enterpriseai.security.keepassrpc;

import java.util.Arrays;

/** Ein gelesener KeePass-Eintrag, adapterintern und kurzlebig. */
final class KeePassEntry implements AutoCloseable {

    private final String title;
    private final String userName;
    private final char[] password;
    private final String location;
    private final java.util.Map<String, String> attributes;

    KeePassEntry(String title, String userName, char[] password) {
        this(title, userName, password, null);
    }

    KeePassEntry(String title, String userName, char[] password, String location) {
        this(title, userName, password, location, java.util.Collections.<String, String>emptyMap());
    }

    KeePassEntry(String title, String userName, char[] password, String location,
                 java.util.Map<String, String> attributes) {
        this.location = location;
        this.attributes = attributes == null ? java.util.Collections.<String, String>emptyMap()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>(attributes));
        this.title = title == null ? "" : title;
        this.userName = userName == null ? "" : userName;
        this.password = password == null ? new char[0] : Arrays.copyOf(password, password.length);
    }

    String title() {
        return title;
    }

    String userName() {
        return userName;
    }

    char[] password() {
        return password;
    }

    /** Die erste http(s)-Adresse des Eintrags oder {@code null}. */
    String location() {
        return location;
    }

    /** Benannte Textfelder des Eintrags (z. B. {@code MM_CertAlias}), ohne Benutzer- und Passwortfeld. */
    java.util.Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
        return "KeePassEntry[" + title + ", ***]";
    }
}
