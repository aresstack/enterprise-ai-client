package com.aresstack.enterpriseai.proasteion.exedra.config;

import com.aresstack.enterpriseai.astu.security.SecretRef;

import java.util.Locale;
import java.util.Properties;

/**
 * Basis-URL des KI-Dienstes aus KeePass: Ist {@code chat.baseUrl} leer und {@code chat.apiKeyRef} ein KeePass-Eintrag,
 * gilt die URL dieses Eintrags (wie MainframeMate). Eine eingetragene {@code chat.baseUrl} überschreibt sie. Der
 * Loader verlangt die URL in diesem Fall nicht; ergänzt wird sie hier, bevor Adapter entstehen.
 */
public final class VaultLocations {

    /** Präfix manuell abgefragter Referenzen (Zugang „Manuell“), wie {@code app.security.AskedSecretProvider}. */
    public static final String ASK_SCHEME = "ask:";
    private static final String KEEPASS_SCHEME = "keepass:";
    private static final String CHAT_BASE_URL = "chat.baseUrl";
    private static final String CHAT_API_KEY_REF = "chat.apiKeyRef";

    /** Liest die Adresse eines Eintrags; {@code null}, wenn er keine hat. */
    public interface Lookup {
        String locationOf(SecretRef ref) throws Exception;
    }

    private VaultLocations() {
    }

    /** {@code true}, wenn die Referenz ein KeePass-Eintrag ist (nicht „Manuell“). */
    public static boolean isVault(SecretRef ref) {
        return ref != null && !ref.id().regionMatches(true, 0, ASK_SCHEME, 0, ASK_SCHEME.length());
    }

    /** Der Titel des KeePass-Eintrags, ohne das (alte) Präfix {@code keepass:}. */
    public static String title(SecretRef ref) {
        if (ref == null) {
            return "";
        }
        String id = ref.id();
        return id.toLowerCase(Locale.ROOT).startsWith(KEEPASS_SCHEME) ? id.substring(KEEPASS_SCHEME.length()).trim()
                : id;
    }

    /** Der KeePass-Eintrag, aus dem {@code chat.baseUrl} kommen muss, oder {@code null}. */
    public static SecretRef pendingChatRef(Properties properties) {
        String url = properties.getProperty(CHAT_BASE_URL, "").trim();
        String ref = properties.getProperty(CHAT_API_KEY_REF, "").trim();
        if (!url.isEmpty() || ref.isEmpty()) {
            return null;
        }
        try {
            SecretRef secretRef = SecretRef.of(ref);
            return isVault(secretRef) ? secretRef : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Kopie der Properties mit {@code chat.baseUrl} aus KeePass, wenn sie leer ist; sonst unverändert.
     *
     * @throws AppConfigException wenn KeePass die Adresse nicht liefert
     */
    public static Properties complete(Properties properties, Lookup lookup) {
        SecretRef ref = pendingChatRef(properties);
        if (ref == null) {
            return properties;
        }
        String location;
        try {
            location = lookup.locationOf(ref);
        } catch (Exception e) {
            throw new AppConfigException("Basis-URL des KI-Dienstes: aus dem KeePass-Eintrag „" + title(ref)
                    + "“ nicht lesbar (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + "). Läuft KeePass mit KeePassRPC? Sonst die URL unter Einstellungen → KI-Dienst → Erweitert "
                    + "eintragen.");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new AppConfigException("Basis-URL des KI-Dienstes: Der KeePass-Eintrag „" + title(ref)
                    + "“ hat keine URL. Bitte dort eintragen oder unter Einstellungen → KI-Dienst → Erweitert angeben.");
        }
        Properties completed = new Properties();
        completed.putAll(properties);
        completed.setProperty(CHAT_BASE_URL, location.trim());
        return completed;
    }
}
