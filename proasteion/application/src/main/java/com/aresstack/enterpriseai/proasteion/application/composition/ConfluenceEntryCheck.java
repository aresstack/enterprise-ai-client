package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.security.ManualCredentials;
import com.aresstack.enterpriseai.proasteion.application.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.security.ClientCertificateFactory;
import com.aresstack.enterpriseai.proasteion.application.security.EnteredSecretProvider;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.astu.source.SourceLocators;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.ConfluenceSourceProvider;

import java.util.List;

/**
 * „🔗 Verbindung testen“ im Eintragseditor wie MainframeMate ({@code ConfluenceRestClient.testConnection} mit URL,
 * Benutzer, Passwort und Zertifikat vor dem Speichern): öffnet mit den eingegebenen Werten eine Confluence-Quelle
 * über denselben Adapter und dieselbe Zertifikatsverdrahtung wie die Anwendung und listet die Spaces.
 */
public final class ConfluenceEntryCheck {

    private static final String REF = "eintragseditor";

    private ConfluenceEntryCheck() {
    }

    /**
     * Die kanonische Adresse einer Confluence-Quelle zu einer Basis-URL (wie {@code KnowledgeSourceManagement.addressOf}
     * aus Typ und Locator), für den Einrichtungsassistenten, der vor der Komposition speichert; {@code ""} ohne URL.
     */
    public static String address(String baseUrl) {
        return ConfluenceSourceProvider.sourceType().addressFor(SourceLocators.url(baseUrl));
    }

    /**
     * Die aktivierte Confluence-Quelle des Einrichtungsassistenten: Titel, Basis-URL, KeePass-Eintrag und
     * Zertifikat (Alias, oder {@code clientCertificate.use=false}, wenn der Benutzer ohne Zertifikat fortfährt).
     * Ohne Startpunkte: alle Spaces sind Partitionen, ausgewählt wird später unter Wissensquellen.
     */
    public static SourceDefinition definition(String title, String baseUrl, String credentialRef, String certAlias) {
        return definition(title, baseUrl, credentialRef, certAlias, null);
    }

    /**
     * Wie {@link #definition(String, String, String, String)}; bei Zugang „Manuell“ ({@code ask:…}) mit dem
     * vorgemerkten Benutzernamen ({@link ManualCredentials#SOURCE_USER_SETTING}, nie ein Secret), {@code null} oder
     * leer: keiner.
     */
    public static SourceDefinition definition(String title, String baseUrl, String credentialRef, String certAlias,
                                              String manualUser) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        SourceSettings settings = SourceSettings.empty().with("title", title == null || title.trim().isEmpty()
                ? ConfluenceSourceProvider.sourceType().displayName() : title.trim()).with("baseUrl", url);
        if (credentialRef != null && !credentialRef.trim().isEmpty()) {
            settings = settings.with("credentialRef", credentialRef.trim());
        }
        if (certAlias != null && !certAlias.trim().isEmpty()) {
            settings = settings.with("clientCertificate.alias", certAlias.trim());
        } else {
            settings = settings.with("clientCertificate.use", "false");
        }
        if (ManualCredentials.isManual(credentialRef) && manualUser != null && !manualUser.trim().isEmpty()) {
            settings = settings.with(ManualCredentials.SOURCE_USER_SETTING, manualUser.trim());
        }
        return new SourceDefinition(address(url), ConfluenceSourceProvider.TYPE_ID, true, settings);
    }

    /** Meldung bei Erfolg; wirft mit der Ursache, wenn die Verbindung scheitert. Nie auf dem EDT. */
    public static String check(NetworkConfig network, String url, String user, char[] password, String certAlias)
            throws Exception {
        NetworkServices services = NetworkServices.from(network);
        try (EnteredSecretProvider secrets = new EnteredSecretProvider(user, password)) {
            ConfluenceSourceProvider provider = new ConfluenceSourceProvider(secrets, services.routes(),
                    services.tls(), services.userAgent(),
                    (alias, keyStoreFile, passwordRef) -> ClientCertificateFactory.deferred(
                            new ClientCertificateConfig(alias, keyStoreFile, passwordRef), secrets, services));
            boolean useCertificate = certAlias != null && !certAlias.trim().isEmpty();
            SourceSettings settings = SourceSettings.empty().with("baseUrl", url).with("credentialRef", REF)
                    .with("clientCertificate.use", Boolean.toString(useCertificate));
            if (useCertificate) {
                settings = settings.with("clientCertificate.alias", certAlias.trim());
            }
            KnowledgeSourcePort port = provider.open(SourceAddress.of("passwort-test"), settings);
            List<SourcePartition> spaces = port.partitions();
            return "Verbindung erfolgreich" + (useCertificate ? " (Zertifikat „" + certAlias.trim() + "“)" : "")
                    + ": " + spaces.size() + " Spaces sichtbar.";
        }
    }
}
