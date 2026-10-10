package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.nio.file.Path;

/**
 * Ein lokales Verzeichnis als Wissensquelle ({@code source.<id>.type=files}): {@code directory} ist die Wurzel,
 * Startpunkte sind Pfade darunter ({@code .} = alles), {@code maxDepth} die Verzeichnistiefe.
 */
public final class LocalFilesSourceConfig extends SourceConfig {

    private final Path directory;
    private final long maxFileBytes;

    LocalFilesSourceConfig(KnowledgeSourceId sourceId, SourceScope scope, Path directory, long maxFileBytes,
                           boolean enabled) {
        super(sourceId, scope, null, enabled);
        this.directory = directory;
        this.maxFileBytes = maxFileBytes;
    }

    public Path directory() {
        return directory;
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    @Override
    public String type() {
        return "files";
    }
}
