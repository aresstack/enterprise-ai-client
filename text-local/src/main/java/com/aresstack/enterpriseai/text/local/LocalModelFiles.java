package com.aresstack.enterpriseai.text.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dateien eines installierten Text-Modells im Modellverzeichnis ({@code <Modellverzeichnis>/<id>}). */
final class LocalModelFiles {

    private LocalModelFiles() {
    }

    /** Ordner des Modells; nur einfache Ordnernamen. */
    static Path directory(Path modelRoot, String modelId) {
        if (modelRoot == null || modelId == null || !modelId.matches("[A-Za-z0-9._-]+") || modelId.startsWith(".")) {
            throw new IllegalArgumentException("Ungültige Modellkennung: " + modelId);
        }
        return modelRoot.resolve(modelId);
    }

    /** Der Graph: {@code model.onnx}, sonst die einzige bzw. erste {@code .onnx}-Datei im Ordner. */
    static Path graph(Path directory) throws IOException {
        Path preferred = directory.resolve("model.onnx");
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        List<Path> graphs = new ArrayList<Path>();
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.onnx")) {
                for (Path file : files) {
                    if (Files.isRegularFile(file)) {
                        graphs.add(file);
                    }
                }
            }
        }
        if (graphs.isEmpty()) {
            throw new IOException("Kein ONNX-Graph in " + directory);
        }
        Collections.sort(graphs);
        return graphs.get(0);
    }

    static Path require(Path directory, String name) throws IOException {
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new IOException(name + " fehlt in " + directory);
        }
        return file;
    }
}
