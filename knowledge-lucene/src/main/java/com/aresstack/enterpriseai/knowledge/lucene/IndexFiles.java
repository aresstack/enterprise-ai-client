package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dateihilfen der Index-Projektion: atomare Schreibvorgänge, Verzeichnisse auflisten und löschen. */
final class IndexFiles {

    private IndexFiles() {
    }

    /**
     * Schreibt über eine temporäre Datei und benennt sie um, atomar wo das Dateisystem es unterstützt; sonst per
     * ersetzendem Umbenennen. Gelesen wird nur von derselben, synchronisierten Instanz nach dem Schreiben, nie
     * nebenläufig; die temporäre Datei schützt vor halb geschriebenen Zieldateien bei Abbruch.
     */
    static void atomicWrite(Path target, byte[] content) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicUnsupported) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Indexdatei kann nicht geschrieben werden: " + target.getFileName(), ex);
        }
    }

    /** Einträge eines Verzeichnisses, sortiert; leer, wenn es nicht existiert. */
    static List<Path> list(Path directory) {
        List<Path> entries = new ArrayList<Path>();
        if (!Files.isDirectory(directory)) {
            return entries;
        }
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
            try {
                for (Path entry : stream) {
                    entries.add(entry);
                }
            } finally {
                stream.close();
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Indexverzeichnis kann nicht gelesen werden: " + directory, ex);
        }
        Collections.sort(entries);
        return entries;
    }

    static void deleteRecursively(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        // Symbolische Links werden als Link gelöscht, nie verfolgt: nichts außerhalb des Indexverzeichnisses.
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            for (Path child : list(path)) {
                deleteRecursively(child);
            }
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Indexdatei kann nicht gelöscht werden: " + path, ex);
        }
    }
}
