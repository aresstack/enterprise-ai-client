package com.aresstack.enterpriseai.source.mediawiki;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Quelltyp „MediaWiki“ ({@code source.<id>.type=mediawiki}, Ressourcenschema {@code wiki}): Felder, Prüfung und
 * Aufbau einer {@link MediaWikiKnowledgeSource}. Schlüssel wie bisher: {@code apiUrl}, {@code credentialRef},
 * {@code startPoints}, {@code maxDepth}, {@code maxResources}, {@code requiresLogin}, {@code siteKey},
 * {@code displayName}; nur in der Datei: {@code connectTimeoutMillis}, {@code readTimeoutMillis},
 * {@code userAgent}, {@code linkNamespaces}.
 */
public final class MediaWikiSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "mediawiki";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID, "wiki", "MediaWiki")
            .description("Seiten einer MediaWiki-Site über die api.php, ab Startseiten entlang der Wiki-Links")
            .idPrefix("wiki")
            .summaryKey("apiUrl")
            .field(SourceSettingField.text("apiUrl", "API-URL",
                    "Die api.php der MediaWiki-Installation, z. B. …/w/api.php").required())
            .field(SourceSettingField.of("credentialRef", SourceSettingField.Kind.SECRET_REF,
                    "KeePass-Eintrag (optional)", "Titel des KeePass-Eintrags mit Benutzername und Passwort; leer = anonym"))
            .field(SourceSettingField.text("startPoints", "Startpunkte", "Kommagetrennte Seitentitel").required())
            .field(SourceSettingField.of("maxDepth", SourceSettingField.Kind.NUMBER, "Tiefe",
                    "Wie viele Linkebenen ab den Startpunkten verfolgt werden (0 = nur Startpunkte)").withDefault("1"))
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Seiten (optional)", "Obergrenze je Lauf; leer = Standard"))
            .field(SourceSettingField.of("requiresLogin", SourceSettingField.Kind.FLAG, "Anmeldung erforderlich",
                    "Vor dem Lesen anmelden (Standard: ja, wenn ein KeePass-Eintrag gesetzt ist)"))
            .field(SourceSettingField.text("siteKey", "Site-Schlüssel (optional)",
                    "Stabiler Schlüssel in den Ressourcen-IDs; nach dem ersten Indexieren nicht ändern"))
            .field(SourceSettingField.text("displayName", "Anzeigename (optional)", "Name der Quelle in Quellenangaben"))
            .build();

    private static final int MAX_TIMEOUT = 600000;

    private final Function<SecretRef, MediaWikiCredentialsProvider> credentials;
    private final HttpRoutePort routes;
    private final SSLSocketFactory sslSocketFactory;
    private final String defaultUserAgent;

    /**
     * @param credentials      Zugangsdaten zum KeePass-Verweis (Brücke der Composition Root)
     * @param routes           Proxy-Route je Ziel; {@code null} = JVM-Standard
     * @param sslSocketFactory Vertrauensquellen für HTTPS; {@code null} = JVM-Standard
     * @param defaultUserAgent User-Agent, wenn die Quelle keinen eigenen hat; {@code null} = Standard des Adapters
     */
    public MediaWikiSourceProvider(Function<SecretRef, MediaWikiCredentialsProvider> credentials,
                                   HttpRoutePort routes, SSLSocketFactory sslSocketFactory, String defaultUserAgent) {
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must not be null");
        }
        this.credentials = credentials;
        this.routes = routes;
        this.sslSocketFactory = sslSocketFactory;
        this.defaultUserAgent = defaultUserAgent;
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
        site(reader, "wiki"); // ohne ID: Site-Schlüssel nur prüfen, wenn er gesetzt ist
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
        MediaWikiSiteConfig site = site(reader, sourceId.value());
        SecretRef credentialRef = reader.secretRef("credentialRef");
        if (reader.hasProblems() || site == null) {
            throw new IllegalArgumentException("MediaWiki-Quelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        MediaWikiCredentialsProvider provider = credentialRef == null ? MediaWikiCredentialsProvider.anonymous()
                : credentials.apply(credentialRef);
        return new MediaWikiKnowledgeSource(sourceId, site, provider, routes, sslSocketFactory);
    }

    /** Die Site-Konfiguration oder {@code null} (dann sind Probleme vermerkt). */
    private MediaWikiSiteConfig site(SourceSettingsReader r, String sourceId) {
        URI apiUrl = r.httpUri("apiUrl", true);
        SecretRef credentialRef = r.secretRef("credentialRef");
        String siteKey = r.text("siteKey", sourceId.toLowerCase(Locale.ROOT));
        String displayName = r.text("displayName", null);
        boolean requiresLogin = r.bool("requiresLogin", credentialRef != null);
        int connect = r.integer("connectTimeoutMillis", 15000, 1, MAX_TIMEOUT);
        int read = r.integer("readTimeoutMillis", 30000, 1, MAX_TIMEOUT);
        String userAgent = r.text("userAgent", defaultUserAgent);
        List<String> namespaces = r.list("linkNamespaces");
        if (apiUrl == null) {
            return null;
        }
        try {
            MediaWikiSiteConfig.Builder site = MediaWikiSiteConfig.builder(siteKey, apiUrl.toString())
                    .requiresLogin(requiresLogin)
                    .connectTimeoutMillis(connect)
                    .readTimeoutMillis(read);
            if (displayName != null) {
                site.displayName(displayName);
            }
            if (userAgent != null) {
                site.userAgent(userAgent);
            }
            if (!namespaces.isEmpty()) {
                int[] ids = new int[namespaces.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = Integer.parseInt(namespaces.get(i));
                }
                site.linkNamespaces(ids);
            }
            return site.build();
        } catch (NumberFormatException e) {
            r.problem("linkNamespaces", "Namensräume müssen ganze Zahlen sein");
            return null;
        } catch (IllegalArgumentException e) {
            r.problem("siteKey", "MediaWiki-Einstellung ungültig (Site-Schlüssel oder Adresse)");
            return null;
        }
    }
}
