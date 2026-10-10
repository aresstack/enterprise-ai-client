package com.aresstack.enterpriseai.source.sharepoint;

import com.aresstack.enterpriseai.document.api.ContentDetector;
import com.aresstack.enterpriseai.document.api.ExtractionRegistry;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Quelltyp „SharePoint“ ({@code source.<id>.type=sharepoint}, Ressourcenschema {@code sharepoint}): eine
 * Dokumentbibliothek über WebDAV. Schlüssel: {@code siteUrl} (http(s)-URL der Bibliothek oder eines Ordners darin),
 * {@code credentialRef} (optional, nur wenn SSO nicht reicht), {@code startPoints} (Standard {@code .}),
 * {@code maxDepth} (Standard 20), {@code maxResources} (Standard 5000), {@code maxFileMegabytes} (Standard 50).
 */
public final class SharePointSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "sharepoint";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID,
                    SharePointKnowledgeSource.SCHEME, "SharePoint")
            .description("Dokumentbibliothek über WebDAV (Windows-WebClient), Anmeldung per SSO; PDF, Office, Text "
                    + "und HTML über Apache Tika")
            .idPrefix("sharepoint")
            .summaryKey("siteUrl")
            .field(SourceSettingField.text("siteUrl", "Adresse der Bibliothek",
                    "Wie im Browser, z. B. …/sites/Team/Freigegebene%20Dokumente").required())
            .field(SourceSettingField.of("credentialRef", SourceSettingField.Kind.SECRET_REF,
                    "KeePass-Eintrag (optional)", "Nur wenn die Windows-Anmeldung (SSO) nicht reicht"))
            .field(SourceSettingField.text("startPoints", "Startpunkte",
                    "Kommagetrennte Unterordner (. = alles)").withDefault("."))
            .field(SourceSettingField.of("maxDepth", SourceSettingField.Kind.NUMBER, "Tiefe",
                    "Wie viele Ordnerebenen durchsucht werden").withDefault("20"))
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Dateien (optional)", "Obergrenze je Lauf; leer = 5000"))
            .field(SourceSettingField.of("maxFileMegabytes", SourceSettingField.Kind.NUMBER,
                    "Größte Datei in MB (optional)", "Größere Dateien werden übersprungen; leer = 50"))
            .build();

    private final SecretProvider secrets;
    private final ContentDetector detector;
    private final ExtractionRegistry extraction;

    public SharePointSourceProvider(SecretProvider secrets, ContentDetector detector, ExtractionRegistry extraction) {
        if (secrets == null || detector == null || extraction == null) {
            throw new IllegalArgumentException("secrets, detector and extraction must not be null");
        }
        this.secrets = secrets;
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
        root(reader, siteUrl(reader));
        reader.secretRef("credentialRef");
        maxFileBytes(reader);
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
        URI siteUrl = siteUrl(reader);
        Path root = root(reader, siteUrl);
        SecretRef credentialRef = reader.secretRef("credentialRef");
        long maxFileBytes = maxFileBytes(reader);
        if (reader.hasProblems() || siteUrl == null || root == null) {
            throw new IllegalArgumentException("SharePoint-Quelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        WebDavAccess access = new WebDavAccess(sourceId, root, SharePointPaths.shareRoot(siteUrl), credentialRef,
                secrets);
        return new SharePointKnowledgeSource(sourceId, siteUrl, root, access, detector, extraction, maxFileBytes);
    }

    private static URI siteUrl(SourceSettingsReader r) {
        return r.httpUri("siteUrl", true);
    }

    private static Path root(SourceSettingsReader r, URI siteUrl) {
        if (siteUrl == null) {
            return null;
        }
        try {
            return Paths.get(SharePointPaths.toUncPath(siteUrl));
        } catch (InvalidPathException e) {
            r.problem("siteUrl", "lässt sich nicht als WebDAV-Pfad abbilden");
            return null;
        }
    }

    private static long maxFileBytes(SourceSettingsReader r) {
        return r.integer("maxFileMegabytes", 50, 1, 2000) * 1024L * 1024L;
    }

    private static SourceScope scope(SourceSettingsReader r) {
        return r.scope(".", 20, 5000);
    }
}
