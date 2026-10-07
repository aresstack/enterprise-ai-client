package com.aresstack.enterpriseai.security.keepassrpc;

import java.util.Arrays;

/** Ein gelesener KeePass-Eintrag, adapterintern und kurzlebig. */
final class KeePassEntry implements AutoCloseable {

    private final String title;
    private final String userName;
    private final char[] password;

    KeePassEntry(String title, String userName, char[] password) {
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

    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
        return "KeePassEntry[" + title + ", ***]";
    }
}
