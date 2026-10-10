package com.aresstack.enterpriseai.source.confluence;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Quelltyp „Confluence“ ({@code source.<id>.type=confluence}, Ressourcenschema {@code confluence}): Felder,
 * Prüfung und Aufbau einer {@link ConfluenceKnowledgeSource} samt Transport. Schlüssel wie bisher: {@code baseUrl},
 * {@code credentialRef}, {@code startPoints}, {@code maxDepth}, {@code maxResources}, {@code searchSpaceKeys},
 * {@code includeAttachments}; nur in der Datei: {@code pageSize}, {@code maxAttachmentBytes},
 * {@code maxResponseBytes}, {@code allowInsecureHttp}, {@code connectTimeoutMillis}, {@code readTimeoutMillis} und
 * {@code clientCertificate.alias}/{@code .keyStoreFile}/{@code .keyStorePasswordRef}.
 */
public final class ConfluenceSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "confluence";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID, "confluence", "Confluence")
            .description("Seiten eines Confluence-Data-Center-Bereichs über die REST-API, wahlweise mit Anhängen")
            .idPrefix("confluence")
            .summaryKey("baseUrl")
            .field(SourceSettingField.text("baseUrl", "Basis-URL", "Basis-URL von Confluence, ohne /rest").required())
            .field(SourceSettingField.of("credentialRef", SourceSettingField.Kind.SECRET_REF,
                    "KeePass-Eintrag (optional)", "Titel des KeePass-Eintrags mit Benutzername und Passwort; leer = anonym"))
            .field(SourceSettingField.text("startPoints", "Startpunkte",
                    "Kommagetrennt: space:KEY oder page:ID").required())
            .field(SourceSettingField.of("maxDepth", SourceSettingField.Kind.NUMBER, "Tiefe",
                    "Wie viele Ebenen Kindseiten ab den Startpunkten verfolgt werden (0 = nur Startpunkte)")
                    .withDefault("1"))
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Seiten (optional)", "Obergrenze je Lauf; leer = Standard"))
            .field(SourceSettingField.text("searchSpaceKeys", "Space-Schlüssel (optional)",
                    "Kommagetrennte Spaces, auf die Suche und Crawl beschränkt werden"))
            .field(SourceSettingField.of("includeAttachments", SourceSettingField.Kind.FLAG, "Anhänge mitlesen",
                    "Anhänge als Dokumente indexieren"))
            .build();

    private static final int MAX_TIMEOUT = 600000;

    /** Baut den TLS-Kontext für ein Client-Zertifikat (mTLS); die Composition Root kennt Tresor und Vertrauen. */
    public interface ClientCertificateSockets {
        /**
         * @param alias        Alias des Zertifikats
         * @param keyStoreFile PKCS12-Datei oder {@code null} für den Windows-Zertifikatspeicher
         * @param passwordRef  KeePass-Verweis auf das Passwort der Datei oder {@code null}
         */
        SSLSocketFactory socketFactory(String alias, Path keyStoreFile, SecretRef passwordRef);
    }

    private final SecretProvider secrets;
    private final HttpRoutePort routes;
    private final SSLSocketFactory sslSocketFactory;
    private final String userAgent;
    private final ClientCertificateSockets certificates;

    /**
     * @param secrets          Security-Port für Benutzer/Passwort je Anfrage
     * @param routes           Proxy-Route je Ziel; {@code null} = JVM-Standard
     * @param sslSocketFactory Vertrauensquellen ohne Client-Zertifikat; {@code null} = JVM-Standard
     * @param userAgent        User-Agent; {@code null} = Standard des Transports
     * @param certificates     TLS-Kontext mit Client-Zertifikat
     */
    public ConfluenceSourceProvider(SecretProvider secrets, HttpRoutePort routes, SSLSocketFactory sslSocketFactory,
                                    String userAgent, ClientCertificateSockets certificates) {
        if (secrets == null || certificates == null) {
            throw new IllegalArgumentException("secrets and certificates must not be null");
        }
        this.secrets = secrets;
        this.routes = routes;
        this.sslSocketFactory = sslSocketFactory;
        this.userAgent = userAgent;
        this.certificates = certificates;
    }

    /** Der Typ ohne Laufzeitumgebung (Beschreibung, Felder). */
    public static KnowledgeSourceType sourceType() {
        return TYPE;
    }

    @Override
    public KnowledgeSourceType type() {
        return TYPE;
    }

    @Override
    public List<String> validate(SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        config(reader);
        timeouts(reader);
        certificate(reader);
        reader.scope(null, 1, SourceScope.DEFAULT_MAX_RESOURCES);
        return reader.problems();
    }

    @Override
    public SourceScope scope(SourceSettings settings) {
        return new SourceSettingsReader(TYPE, settings).scope(null, 1, SourceScope.DEFAULT_MAX_RESOURCES);
    }

    @Override
    public KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        ConfluenceConfig config = config(reader);
        int[] timeouts = timeouts(reader);
        Certificate certificate = certificate(reader);
        if (reader.hasProblems() || config == null) {
            throw new IllegalArgumentException("Confluence-Quelle " + sourceId + " ist fehlerhaft: "
                    + reader.problems());
        }
        UrlConnectionConfluenceTransport.Builder transport = UrlConnectionConfluenceTransport.builder()
                .routes(routes)
                .connectTimeoutMillis(timeouts[0])
                .readTimeoutMillis(timeouts[1]);
        if (userAgent != null) {
            transport.userAgent(userAgent);
        }
        if (certificate != null) {
            // Erst beim ersten Verbindungsaufbau geladen; ohne erreichbaren Tresor meldet die Quelle je Anfrage
            // UNAVAILABLE, bis das Zertifikat ladbar ist.
            transport.sslSocketFactory(certificates.socketFactory(certificate.alias, certificate.keyStoreFile,
                    certificate.passwordRef));
        } else if (sslSocketFactory != null) {
            transport.sslSocketFactory(sslSocketFactory);
        }
        return new ConfluenceKnowledgeSource(sourceId, config, transport.build(), secrets);
    }

    private static ConfluenceConfig config(SourceSettingsReader r) {
        URI baseUrl = r.httpUri("baseUrl", true);
        SecretRef credentialRef = r.secretRef("credentialRef");
        int pageSize = r.integer("pageSize", 50, 1, 500);
        boolean attachments = r.bool("includeAttachments", false);
        int maxAttachmentBytes = r.integer("maxAttachmentBytes", 5 * 1024 * 1024, 1, Integer.MAX_VALUE);
        int maxResponseBytes = r.integer("maxResponseBytes", 20 * 1024 * 1024, 1, Integer.MAX_VALUE);
        boolean allowInsecureHttp = r.bool("allowInsecureHttp", false);
        List<String> spaceKeys = r.list("searchSpaceKeys");
        if (baseUrl == null) {
            return null;
        }
        try {
            ConfluenceConfig.Builder builder = ConfluenceConfig.builder(baseUrl)
                    .credentialRef(credentialRef)
                    .pageSize(pageSize)
                    .includeAttachments(attachments)
                    .maxAttachmentBytes(maxAttachmentBytes)
                    .maxResponseBytes(maxResponseBytes)
                    .allowInsecureHttp(allowInsecureHttp);
            for (String spaceKey : spaceKeys) {
                builder.searchSpaceKey(spaceKey);
            }
            return builder.build();
        } catch (IllegalArgumentException e) {
            r.problem("baseUrl", "Confluence-Einstellung ungültig (mit KeePass-Eintrag nur https, Space-Schlüssel "
                    + "prüfen)");
            return null;
        }
    }

    private static int[] timeouts(SourceSettingsReader r) {
        return new int[] {r.integer("connectTimeoutMillis", 15000, 1, MAX_TIMEOUT),
                r.integer("readTimeoutMillis", 60000, 1, MAX_TIMEOUT)};
    }

    private static Certificate certificate(SourceSettingsReader r) {
        String alias = r.text("clientCertificate.alias", null);
        Path keyStore = r.path("clientCertificate.keyStoreFile", null);
        SecretRef passwordRef = r.secretRef("clientCertificate.keyStorePasswordRef");
        if (alias == null) {
            if (keyStore != null || passwordRef != null) {
                r.problem("clientCertificate.alias", "fehlt, obwohl ein Client-Zertifikat konfiguriert ist");
            }
            return null;
        }
        if (keyStore == null && passwordRef != null) {
            r.problem("clientCertificate.keyStorePasswordRef",
                    "nur zusammen mit keyStoreFile sinnvoll (Windows-MY hat kein Passwort)");
        }
        return new Certificate(alias, keyStore, passwordRef);
    }

    private static final class Certificate {
        final String alias;
        final Path keyStoreFile;
        final SecretRef passwordRef;

        Certificate(String alias, Path keyStoreFile, SecretRef passwordRef) {
            this.alias = alias;
            this.keyStoreFile = keyStoreFile;
            this.passwordRef = passwordRef;
        }
    }
}
