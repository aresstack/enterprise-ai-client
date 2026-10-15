package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import java.net.URI;
import java.util.Locale;

/**
 * Die Confluence-Angaben des Einrichtungsassistenten ohne Secret: Zugang „KeePass“ (der Eintrag trägt Benutzer,
 * Passwort bzw. Token und URL) oder „Manuell“ (Passwort bzw. Token gilt nur für die Sitzung, der Benutzername darf
 * vorgemerkt werden), dazu Basis-URL und Client-Zertifikat ({@code null}/leer: ausdrücklich ohne).
 */
public final class ConfluenceSetup {

    /** Präfix der manuellen Referenzen, wie {@code AskedSecretProvider.SCHEME}. */
    private static final String ASK = "ask:";

    private final String entryTitle;
    private final String baseUrl;
    private final String userName;
    private final String certificateAlias;
    private final boolean manual;
    private final boolean rememberUser;

    /** Zugang „KeePass“. */
    public ConfluenceSetup(String entryTitle, String baseUrl, String userName, String certificateAlias) {
        this(entryTitle, baseUrl, userName, certificateAlias, false, false);
    }

    private ConfluenceSetup(String entryTitle, String baseUrl, String userName, String certificateAlias,
                            boolean manual, boolean rememberUser) {
        this.entryTitle = text(entryTitle);
        this.baseUrl = text(baseUrl);
        this.userName = text(userName);
        this.certificateAlias = text(certificateAlias);
        this.manual = manual;
        this.rememberUser = rememberUser;
        if ((!manual && this.entryTitle.isEmpty()) || this.baseUrl.isEmpty()) {
            throw new IllegalArgumentException(manual ? "baseUrl must not be empty"
                    : "entryTitle and baseUrl must not be empty");
        }
    }

    /**
     * Zugang „Manuell“: kein KeePass-Eintrag; Passwort bzw. Token fragt die Anwendung je Sitzung ab.
     *
     * @param rememberUser ob der (nicht geheime) Benutzername als Vorbelegung des Dialogs gespeichert wird
     */
    public static ConfluenceSetup manual(String baseUrl, String userName, String certificateAlias,
                                         boolean rememberUser) {
        return new ConfluenceSetup("", baseUrl, userName, certificateAlias, true, rememberUser);
    }

    /** Die manuelle Referenz einer Basis-URL, je Rechner eine eigene: {@code ask:Confluence (<host>)}. */
    public static String manualRef(String baseUrl) {
        String host = "";
        try {
            String parsed = URI.create(text(baseUrl)).getHost();
            host = parsed == null ? "" : parsed.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            host = "";
        }
        return ASK + "Confluence" + (host.isEmpty() ? "" : " (" + host + ")");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** Der KeePass-Eintrag; leer bei „Manuell“. */
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

    public boolean isManual() {
        return manual;
    }

    /** Was die Quelle als {@code credentialRef} speichert: der Eintragstitel oder die manuelle Referenz. */
    public String credentialRef() {
        return manual ? manualRef(baseUrl) : entryTitle;
    }

    /** Der vorzumerkende Benutzername der Quelle ({@code ""}: keiner); nur bei „Manuell“ und auf Wunsch. */
    public String rememberedUser() {
        return manual && rememberUser ? userName : "";
    }

    @Override
    public String toString() {
        return "ConfluenceSetup{" + (manual ? "manual" : "entry=" + entryTitle) + ", certificate="
                + !certificateAlias.isEmpty() + "}";
    }
}
