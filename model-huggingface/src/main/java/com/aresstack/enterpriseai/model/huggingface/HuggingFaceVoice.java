package com.aresstack.enterpriseai.model.huggingface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eine kuratierte Stimme aus der Konfiguration: Kennung (Ordnername und Modellname im Sidecar), Anzeigename,
 * Sprache, Hub-Repository, Revision und die Dateien im Repository (für Piper {@code <name>.onnx} und
 * {@code <name>.onnx.json}). Die Dateien landen unter ihrem Dateinamen ohne Pfad im Stimmenordner.
 */
public final class HuggingFaceVoice {

    private final String id;
    private final String displayName;
    private final String language;
    private final String repository;
    private final String revision;
    private final List<String> files;

    public HuggingFaceVoice(String id, String displayName, String language, String repository, String revision,
                            List<String> files) {
        if (id == null || !id.matches("[A-Za-z0-9._-]+") || id.startsWith(".")) {
            throw new IllegalArgumentException("id must be a plain directory name: " + id);
        }
        if (repository == null || repository.trim().isEmpty()) {
            throw new IllegalArgumentException("repository must not be blank");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        List<String> copy = new ArrayList<String>();
        for (String file : files) {
            if (file == null || file.trim().isEmpty() || file.contains("..")) {
                throw new IllegalArgumentException("invalid file: " + file);
            }
            copy.add(file.trim());
        }
        this.id = id;
        this.displayName = displayName == null || displayName.trim().isEmpty() ? id : displayName.trim();
        this.language = language == null ? "" : language.trim();
        this.repository = repository.trim();
        this.revision = revision == null || revision.trim().isEmpty() ? "main" : revision.trim();
        this.files = Collections.unmodifiableList(copy);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String language() {
        return language;
    }

    public String repository() {
        return repository;
    }

    public String revision() {
        return revision;
    }

    public List<String> files() {
        return files;
    }

    /** Dateiname ohne Pfad im Repository: so heißt die Datei im Stimmenordner. */
    static String fileName(String repositoryPath) {
        int slash = repositoryPath.lastIndexOf('/');
        return slash < 0 ? repositoryPath : repositoryPath.substring(slash + 1);
    }
}
