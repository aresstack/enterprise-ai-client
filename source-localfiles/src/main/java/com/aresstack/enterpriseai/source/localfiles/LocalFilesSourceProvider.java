package com.aresstack.enterpriseai.source.localfiles;

import com.aresstack.enterpriseai.document.api.ContentDetector;
import com.aresstack.enterpriseai.document.api.ExtractionRegistry;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSettingsReader;

import java.nio.file.Path;
import java.util.List;

/**
 * Quelltyp „Lokale Dateien“ ({@code source.<id>.type=files}, Ressourcenschema {@code file}): ein Verzeichnis,
 * rekursiv. Schlüssel wie bisher: {@code directory}, {@code startPoints} (Standard {@code .}), {@code maxDepth}
 * (Standard 20), {@code maxResources} (Standard 5000), {@code maxFileMegabytes} (Standard 50).
 */
public final class LocalFilesSourceProvider implements KnowledgeSourceProvider {

    public static final String TYPE_ID = "files";

    private static final KnowledgeSourceType TYPE = KnowledgeSourceType.builder(TYPE_ID, "file", "Lokale Dateien")
            .description("Ein Verzeichnis, rekursiv; PDF, Office, Text, Markdown und HTML über Apache Tika")
            .idPrefix("dateien")
            .summaryKey("directory")
            .field(SourceSettingField.of("directory", SourceSettingField.Kind.DIRECTORY, "Verzeichnis",
                    "Ordner mit den Dokumenten, z. B. C:\\Daten\\Handbuch").required())
            .field(SourceSettingField.text("startPoints", "Startpunkte",
                    "Kommagetrennte Unterordner (. = alles)").withDefault("."))
            .field(SourceSettingField.of("maxDepth", SourceSettingField.Kind.NUMBER, "Tiefe",
                    "Wie viele Verzeichnisebenen durchsucht werden").withDefault("20"))
            .field(SourceSettingField.of("maxResources", SourceSettingField.Kind.NUMBER,
                    "Höchstzahl Dateien (optional)", "Obergrenze je Lauf; leer = 5000"))
            .field(SourceSettingField.of("maxFileMegabytes", SourceSettingField.Kind.NUMBER,
                    "Größte Datei in MB (optional)", "Größere Dateien werden übersprungen; leer = 50"))
            .build();

    private final ContentDetector detector;
    private final ExtractionRegistry extraction;

    public LocalFilesSourceProvider(ContentDetector detector, ExtractionRegistry extraction) {
        if (detector == null || extraction == null) {
            throw new IllegalArgumentException("detector and extraction must not be null");
        }
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
        directory(reader);
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
        Path directory = directory(reader);
        long maxFileBytes = maxFileBytes(reader);
        if (reader.hasProblems() || directory == null) {
            throw new IllegalArgumentException("Dateiquelle " + sourceId + " ist fehlerhaft: " + reader.problems());
        }
        return new LocalFilesKnowledgeSource(sourceId, directory, detector, extraction, maxFileBytes);
    }

    private static Path directory(SourceSettingsReader r) {
        Path directory = r.path("directory", null);
        if (directory == null && r.text("directory", null) == null) {
            r.problem("directory", "fehlt (Pflichtangabe: Verzeichnis mit den Dokumenten)");
        }
        return directory;
    }

    private static long maxFileBytes(SourceSettingsReader r) {
        return r.integer("maxFileMegabytes", 50, 1, 2000) * 1024L * 1024L;
    }

    private static SourceScope scope(SourceSettingsReader r) {
        return r.scope(".", 20, 5000);
    }
}
