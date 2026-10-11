package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.security.ClientCertificateFactory;
import com.aresstack.enterpriseai.proasteion.application.security.EnteredSecretProvider;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
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
