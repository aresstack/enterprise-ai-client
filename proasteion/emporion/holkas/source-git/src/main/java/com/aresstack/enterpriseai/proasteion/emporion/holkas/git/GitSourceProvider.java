package com.aresstack.enterpriseai.proasteion.emporion.holkas.git;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceSettingField;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.ContentDetector;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;
import com.aresstack.enterpriseai.source.api.files.FilePatterns;

import java.nio.file.Path;
import java.util.List;

/**
 * Quelltyp „Git-Repository“ ({@code source.<n>.type=git}, Ressourcenschema {@code git}, #137): der aktuelle Stand
 * genau eines Refs, nur lesend. Schlüssel: {@code url} (Pflicht; https, http oder lokales Repository), {@code title},
 * {@code ref} (Branch, Tag oder Commit-ID; leer = Standard-Branch des Remotes), {@code startPoints} (Pfadpräfixe,
 * Standard {@code .}), {@code maxDepth} (Standard 30), {@code maxResources} (Standard 5000),
 * {@code maxFileMegabytes} (Standard 2), {@code include}, {@code exclude} (Glob-Muster; Vorgabe
 * {@link GitFiles#DEFAULT_EXCLUDES}), {@code timeoutSeconds} (Standard 120), {@code refreshMinutes} (Standard 5).
 *
 * <p>JGit liegt nur hinter diesem Adapter; der Cache der Repositorys gehört der Anwendung ({@code cacheDirectory}).
 */
public final class GitSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "git";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID, GitKnowledgeSource.SCHEME,
                    "Git-Repository")
            .description("Ein Branch eines Git-Repositorys (Azure Repos, GitHub, GitLab, lokal), nur lesend: "
                    + "Quelltext, Konfiguration, Dokumentation und Design-Assets")
            .idPrefix("git")
            .fullIndexing(true)
            .summaryKey("url")
            .field(SourceSettingField.text("url", "Repository-URL",
                    "HTTPS-Adresse ohne Zugangsdaten (z. B. dev.azure.com/org/projekt/_git/repo) oder lokaler Pfad").required())
            .field(SourceSettingField.text("title", "Anzeigename (optional)", "Leer = Name aus der Adresse"))
            .field(SourceSettingField.text("ref", "Branch/Tag (optional)",
                    "Branch, Tag oder Commit-ID; leer = Standard-Branch des Repositorys"))
            .field(SourceSettingField.text("startPoints", "Pfade",
                    "Kommagetrennte Ordner oder Dateien im Repository (. = alles)").withDefault(".").advanced())
            .field(SourceSettingField.text("include", "Nur diese Dateien (optional)",
                    "Glob-Muster, kommagetrennt, z. B. src/**, *.md; leer = alle bekannten Dateitypen").advanced())
            .field(SourceSettingField.text("exclude", "Ausschließen",
                    "Glob-Muster, kommagetrennt; Abhängigkeiten, Build-Ausgaben und Lockfiles sind vorbelegt")
                    .withDefault(GitFiles.DEFAULT_EXCLUDES).advanced())
            .field(SourceSettingField.of("maxFileMegabytes", SourceSettingField.Kind.NUMBER,
                    "Größte Datei in MB", "Größere Dateien werden übersprungen; Standard 2").withDefault("2")
                    .advanced())
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Dateien (optional)", "Obergrenze je Lauf; leer = 5000").advanced())
            .field(SourceSettingField.of("maxDepth", SourceSettingField.Kind.NUMBER, "Tiefe",
                    "Wie viele Verzeichnisebenen gelesen werden").withDefault("30").advanced())
            .build();

    private static final int DEFAULT_MAX_DEPTH = 30;
    private static final int DEFAULT_MAX_RESOURCES = 5000;

    private final GitRepositoryCache cache;
    private final ContentDetector detector;
    private final ExtractionRegistry extraction;

    /**
     * @param cacheDirectory folder owned by the application for the bare copies of the repositories
     * @param detector       content detection for PDF and Office files in repositories
     * @param extraction     extractors for PDF and Office files in repositories
     */
    public GitSourceProvider(Path cacheDirectory, ContentDetector detector, ExtractionRegistry extraction) {
        if (cacheDirectory == null || detector == null || extraction == null) {
            throw new IllegalArgumentException("cacheDirectory, detector and extraction must not be null");
        }
        this.cache = new GitRepositoryCache(cacheDirectory);
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
        remote(reader);
        ref(reader);
        maxFileBytes(reader);
        scope(reader);
        return reader.problems();
    }

    @Override
    public SourceScope scope(SourceSettings settings) {
        return scope(new SourceSettingsReader(TYPE, settings));
    }

    @Override
    public KnowledgeSourcePort open(SourceAddress sourceId, SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        GitRemote remote = remote(reader);
        String ref = ref(reader);
        long maxFileBytes = maxFileBytes(reader);
        int timeout = reader.integer("timeoutSeconds", 120, 5, 3600);
        long refreshMillis = reader.integer("refreshMinutes", 5, 0, 24 * 60) * 60000L;
        if (reader.hasProblems() || remote == null) {
            throw new IllegalArgumentException("Git-Quelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        FilePatterns patterns = new FilePatterns(FilePatterns.split(reader.text("include", null)),
                FilePatterns.split(reader.text("exclude", GitFiles.DEFAULT_EXCLUDES)));
        return new GitKnowledgeSource(sourceId, remote, ref, locatorOf(remote, ref), reader.text("title", null),
                cache, null, timeout, patterns, maxFileBytes, detector, extraction, refreshMillis,
                System::currentTimeMillis);
    }

    /**
     * Repository ohne Schema und {@code .git}, mit {@code @<ref>}, wenn ein Ref gewählt ist
     * ({@code git:dev.azure.com/org/p/_git/repo@release/1.0/src/A.java}); zwei Quellen auf verschiedene Branches
     * desselben Repositorys bekommen so verschiedene IDs.
     */
    @Override
    public String locator(SourceSettings settings) {
        SourceSettingsReader reader = new SourceSettingsReader(TYPE, settings);
        GitRemote remote = remote(reader);
        return remote == null ? "" : locatorOf(remote, ref(reader));
    }

    static String locatorOf(GitRemote remote, String ref) {
        return ref.isEmpty() ? remote.locator() : remote.locator() + "@" + ref;
    }

    private static GitRemote remote(SourceSettingsReader r) {
        String url = r.text("url", null);
        try {
            return GitRemote.parse(url);
        } catch (IllegalArgumentException e) {
            r.problem("url", e.getMessage());
            return null;
        }
    }

    private static String ref(SourceSettingsReader r) {
        String ref = r.text("ref", "").trim();
        if (ref.contains("..") || ref.contains(" ") || ref.contains("\\") || ref.startsWith("-")
                || ref.endsWith("/") || ref.contains("@{") || ref.contains(":")) {
            r.problem("ref", "kein gültiger Branch-, Tag- oder Commit-Name");
            return "";
        }
        return ref;
    }

    private static long maxFileBytes(SourceSettingsReader r) {
        return r.integer("maxFileMegabytes", 2, 1, 200) * 1024L * 1024L;
    }

    private static SourceScope scope(SourceSettingsReader r) {
        return r.scope(".", DEFAULT_MAX_DEPTH, DEFAULT_MAX_RESOURCES);
    }
}
