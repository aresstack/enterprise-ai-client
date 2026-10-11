package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

/**
 * Die Confluence-Angaben des Einrichtungsassistenten ohne Secret: der KeePass-Eintrag (Benutzer, Passwort bzw.
 * Token und URL stehen dort), die Basis-URL und das Client-Zertifikat ({@code null}/leer: ausdrücklich ohne).
 */
public final class ConfluenceSetup {

    private final String entryTitle;
    private final String baseUrl;
    private final String userName;
    private final String certificateAlias;

    public ConfluenceSetup(String entryTitle, String baseUrl, String userName, String certificateAlias) {
        this.entryTitle = text(entryTitle);
        this.baseUrl = text(baseUrl);
        this.userName = text(userName);
        this.certificateAlias = text(certificateAlias);
        if (this.entryTitle.isEmpty() || this.baseUrl.isEmpty()) {
            throw new IllegalArgumentException("entryTitle and baseUrl must not be empty");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public String entryTitle() {
        return entryTitle;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Leer: Bearer/Personal Access Token, sonst Basic. */
    public String userName() {
        return userName;
    }

    /** Leer: ohne Client-Zertifikat. */
    public String certificateAlias() {
        return certificateAlias;
    }

    @Override
    public String toString() {
        return "ConfluenceSetup{entry=" + entryTitle + ", certificate=" + !certificateAlias.isEmpty() + "}";
    }
}
