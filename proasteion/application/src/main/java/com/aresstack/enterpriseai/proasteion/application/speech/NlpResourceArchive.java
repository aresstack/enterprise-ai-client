package com.aresstack.enterpriseai.proasteion.application.speech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Das Ressourcen-Archiv neben dem Jar ({@code <prefix>*.zip}): ein ZIP mit Manifest, aus dem die OpenNLP-Modelle
 * ohne Download installiert werden. Ein Modell gilt nur dann als im Archiv vorhanden, wenn das Manifest es mit genau
 * der Größe und dem SHA-256 des Katalogs führt; beim Entpacken wird beides am Inhalt noch einmal geprüft.
 */
final class NlpResourceArchive {

    private final List<Path> directories;
    private final String prefix;
    private final String manifestName;

    NlpResourceArchive(List<Path> directories, String prefix, String manifestName) {
        this.directories = Collections.unmodifiableList(new ArrayList<Path>(directories));
        this.prefix = prefix == null ? "" : prefix.trim();
        this.manifestName = manifestName == null ? "" : manifestName.trim();
    }

    /** Ob das Archiv das Modell mit genau dieser Größe und diesem SHA-256 anbietet. */
    boolean offers(String id, long size, String sha256) {
        Path archive = locate();
        if (archive == null) {
            return false;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return pathOf(zip, id, size, sha256) != null;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * Der geprüfte Inhalt des Modells aus dem Archiv oder {@code null}, wenn es dort fehlt oder nicht stimmt (dann
     * wird heruntergeladen).
     */
    byte[] read(String id, long size, String sha256) throws IOException {
        Path archive = locate();
        if (archive == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            String path = pathOf(zip, id, size, sha256);
            ZipEntry entry = path == null ? null : zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            byte[] bytes;
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = readAll(in, size);
            }
            if (bytes == null || (size >= 0 && bytes.length != size) || !NlpModels.sha256(bytes).equals(sha256)) {
                return null;
            }
            return bytes;
        }
    }

    /** Das Archiv mit dem größten Namen im ersten Verzeichnis, das eines hat, oder {@code null}. */
    Path locate() {
        if (prefix.isEmpty()) {
            return null;
        }
        for (Path directory : directories) {
            if (directory == null || !Files.isDirectory(directory)) {
                continue;
            }
            Path best = null;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, prefix + "*.zip")) {
                for (Path file : files) {
                    if (Files.isRegularFile(file) && (best == null
                            || file.getFileName().toString().compareTo(best.getFileName().toString()) > 0)) {
                        best = file;
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                continue;
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private String pathOf(ZipFile zip, String id, long size, String sha256) throws IOException {
        ZipEntry manifestEntry = manifestName.isEmpty() ? null : zip.getEntry(manifestName);
        if (manifestEntry == null) {
            return null;
        }
        Properties manifest = new Properties();
        try (Reader reader = new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        String key = "model." + id + ".";
        String path = manifest.getProperty(key + "path", "").trim();
        String listedSha = manifest.getProperty(key + "sha256", "").trim().toLowerCase(Locale.ROOT);
        String listedSize = manifest.getProperty(key + "size", "").trim();
        if (path.isEmpty() || !listedSha.equals(sha256) || !listedSize.equals(String.valueOf(size))) {
            return null;
        }
        return zip.getEntry(path) == null ? null : path;
    }

    /** Liest höchstens {@code size + 1} Bytes, damit ein falscher Eintrag nicht unbegrenzt Speicher belegt. */
    private static byte[] readAll(InputStream in, long size) throws IOException {
        long limit = size >= 0 ? size + 1 : Long.MAX_VALUE;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
