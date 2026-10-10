package com.aresstack.enterpriseai.source.betaview;

import com.aresstack.enterpriseai.document.api.ContentDetector;
import com.aresstack.enterpriseai.document.api.ExtractionRegistry;
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
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

/**
 * Quelltyp „BetaView“ ({@code source.<id>.type=betaview}, Ressourcenschema {@code betaview}): Listen und Reports
 * aus BetaView. Schlüssel: {@code baseUrl} (Adresse der BetaView-Webanwendung), {@code credentialRef} (Pflicht),
 * {@code favoriteId} (Pflicht), {@code startPoints} (Jobnamen, Standard {@code *}), {@code daysBack} (Standard 7),
 * {@code form}, {@code extension}, {@code report}, {@code locale}, {@code maxResources} (Standard 200),
 * {@code maxFileMegabytes} (Standard 50); nur in der Datei: {@code timeoutMillis}.
 */
public final class BetaViewSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "betaview";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID,
                    BetaViewKnowledgeSource.SCHEME, "BetaView")
            .description("Listen und Reports aus BetaView über einen gespeicherten Favoriten; PDF und Text über "
                    + "Apache Tika")
            .idPrefix("betaview")
            .summaryKey("baseUrl")
            .field(SourceSettingField.text("baseUrl", "Adresse",
                    "Adresse der BetaView-Webanwendung, wie im Browser vor login.action").required())
            .field(SourceSettingField.of("credentialRef", SourceSettingField.Kind.SECRET_REF,
                    "KeePass-Eintrag", "Titel des KeePass-Eintrags mit Benutzer und Passwort").required())
            .field(SourceSettingField.text("favoriteId", "Favorit", "ID des BetaView-Favoriten für die Suche")
                    .required())
            .field(SourceSettingField.text("startPoints", "Jobnamen", "Kommagetrennte Filter, z. B. * oder PAY*")
                    .withDefault("*"))
            .field(SourceSettingField.of("daysBack", SourceSettingField.Kind.NUMBER, "Zeitraum in Tagen",
                    "Dokumente der letzten N Tage").withDefault("7"))
            .field(SourceSettingField.text("form", "Formular (optional)", "Leer = APZC"))
            .field(SourceSettingField.text("extension", "Endung (optional)", "Leer = *"))
            .field(SourceSettingField.text("report", "Report (optional)", "Leer = *"))
            .field(SourceSettingField.text("locale", "Sprache", "Sprache der Oberfläche").withDefault("de"))
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Dokumente (optional)", "Obergrenze je Lauf; leer = 200"))
            .field(SourceSettingField.of("maxFileMegabytes", SourceSettingField.Kind.NUMBER,
                    "Größtes Dokument in MB (optional)", "Größere Dokumente werden übersprungen; leer = 50"))
            .build();

    private static final int DEFAULT_MAX_RESOURCES = 200;

    private final SecretProvider secrets;
    private final HttpRoutePort routes;
    private final SSLSocketFactory sslSocketFactory;
    private final String userAgent;
    private final ContentDetector detector;
    private final ExtractionRegistry extraction;

    /**
     * @param routes           Proxy-Route je Ziel; {@code null} = JVM-Standard
     * @param sslSocketFactory Vertrauensquellen; {@code null} = JVM-Standard
     * @param userAgent        User-Agent; {@code null} = Standard
     */
    public BetaViewSourceProvider(SecretProvider secrets, HttpRoutePort routes, SSLSocketFactory sslSocketFactory,
                                  String userAgent, ContentDetector detector, ExtractionRegistry extraction) {
        if (secrets == null || detector == null || extraction == null) {
            throw new IllegalArgumentException("secrets, detector and extraction must not be null");
        }
        this.secrets = secrets;
        this.routes = routes;
        this.sslSocketFactory = sslSocketFactory;
        this.userAgent = userAgent;
        this.detector = detector;
        this.extraction = extraction;
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
        baseUrl(reader);
        credentialRef(reader);
        search(reader);
        reader.integer("maxFileMegabytes", 50, 1, 2000);
        reader.integer("timeoutMillis", 60000, 1000, 600000);
        scope(reader);
        return reader.problems();
    }

    @Override
    public SourceScope scope(SourceSettings settings) {
        return scope(new SourceSettingsReader(TYPE, settings));
    }

    @Override
    public KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        URI baseUrl = baseUrl(reader);
        SecretRef credentialRef = credentialRef(reader);
        BetaViewKnowledgeSource.Search search = search(reader);
        long maxFileBytes = reader.integer("maxFileMegabytes", 50, 1, 2000) * 1024L * 1024L;
        int timeoutMillis = reader.integer("timeoutMillis", 60000, 1000, 600000);
        if (reader.hasProblems() || baseUrl == null || credentialRef == null || search == null) {
            throw new IllegalArgumentException("BetaView-Quelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        BetaViewBaseUrl base;
        try {
            base = new BetaViewBaseUrl(baseUrl.toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("BetaView-Quelle " + sourceId + ": ungültige Adresse", e);
        }
        BetaViewClient client = new BetaViewHttpClient(base, routes, sslSocketFactory, userAgent, timeoutMillis);
        return new BetaViewKnowledgeSource(sourceId, client, credentialRef, secrets, search, detector, extraction,
                maxFileBytes);
    }

    /** Basisadresse mit abschließendem {@code /}, damit {@code login.action} darunter aufgelöst wird. */
    private static URI baseUrl(SourceSettingsReader r) {
        URI uri = r.httpUri("baseUrl", true);
        if (uri == null) {
            return null;
        }
        String text = uri.toString();
        if (text.endsWith("login.action")) {
            text = text.substring(0, text.length() - "login.action".length());
        }
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static SecretRef credentialRef(SourceSettingsReader r) {
        SecretRef ref = r.secretRef("credentialRef");
        if (ref == null && r.text("credentialRef", null) == null) {
            r.problem("credentialRef", "fehlt (Pflichtangabe: BetaView braucht Benutzer und Passwort)");
        }
        return ref;
    }

    private static BetaViewKnowledgeSource.Search search(SourceSettingsReader r) {
        String favoriteId = r.required("favoriteId");
        int daysBack = r.integer("daysBack", 7, 1, 3650);
        if (favoriteId == null) {
            return null;
        }
        return new BetaViewKnowledgeSource.Search(favoriteId, r.text("locale", "de"), daysBack,
                r.text("form", "APZC"), r.text("extension", "*"), r.text("report", "*"));
    }

    private static SourceScope scope(SourceSettingsReader r) {
        return r.scope("*", 0, DEFAULT_MAX_RESOURCES);
    }
}
