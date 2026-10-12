package com.aresstack.enterpriseai.proasteion.application.resources;

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
 * Das lokale Ressourcen-Bundle neben dem Jar ({@code <prefix>*.zip}): ein ZIP mit Manifest, aus dem Ressourcen ohne
 * Download installiert werden. Eine Ressource gilt nur dann als vorhanden, wenn das Manifest sie mit genau der Größe
 * und dem SHA-256 ihrer Beschreibung führt; beim Lesen wird beides am Inhalt noch einmal geprüft.
 *
 * <p>Manifest (Properties): {@code model.<id>.path}, {@code model.<id>.sha256}, {@code model.<id>.size}.
 */
public final class LocalResourceSource {

    private final List<Path> directories;
    private final String prefix;
    private final String manifestName;

    /** Bundle-Hash zuletzt gelesen für (Pfad, Größe, Änderungszeit). */
    private String hashedKey;
    private String hashedValue;

    /**
     * @param directories  die Verzeichnisse, in denen der Reihe nach gesucht wird (meist Jar-, dann
     *                     Anwendungsverzeichnis)
     * @param prefix       Namensanfang des ZIP, leer = kein Bundle
     * @param manifestName Name des Manifests im ZIP
     */
    public LocalResourceSource(List<Path> directories, String prefix, String manifestName) {
        this.directories = Collections.unmodifiableList(new ArrayList<Path>(directories));
        this.prefix = prefix == null ? "" : prefix.trim();
        this.manifestName = manifestName == null ? "" : manifestName.trim();
    }

    /** Eine Quelle ohne Bundle. */
    public static LocalResourceSource none() {
        return new LocalResourceSource(Collections.<Path>emptyList(), null, null);
    }

    /** Ob das Bundle die Ressource mit genau dieser Größe und diesem SHA-256 anbietet. */
    public boolean offers(ResourceDescriptor resource) {
        Path archive = locate();
        if (archive == null) {
            return false;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return pathOf(zip, resource) != null;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * Der geprüfte Inhalt der Ressource aus dem Bundle oder {@code null}, wenn sie dort fehlt oder nicht stimmt (dann
     * wird heruntergeladen).
     */
    public byte[] read(ResourceDescriptor resource) throws IOException {
        Path archive = locate();
        if (archive == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            String path = pathOf(zip, resource);
            ZipEntry entry = path == null ? null : zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            byte[] bytes;
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = readAll(in, resource.size());
            }
            if (bytes == null || (resource.size() >= 0 && bytes.length != resource.size())
                    || !ResourceInstaller.sha256(bytes).equals(resource.sha256())) {
                return null;
            }
            return bytes;
        }
    }

    /** Das Bundle mit dem größten Namen im ersten Verzeichnis, das eines hat, oder {@code null}. */
    public Path locate() {
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

    /**
     * SHA-256 des ganzen Bundles als seine Installationsversion oder {@code null} ohne Bundle; zwischengespeichert,
     * solange Pfad, Größe und Änderungszeit gleich bleiben.
     */
    public synchronized String bundleSha256() throws IOException {
        Path archive = locate();
        if (archive == null) {
            return null;
        }
        String key = archive.toAbsolutePath() + "|" + Files.size(archive) + "|"
                + Files.getLastModifiedTime(archive).toMillis();
        if (!key.equals(hashedKey)) {
            hashedValue = ResourceInstaller.sha256(archive);
            hashedKey = key;
        }
        return hashedValue;
    }

    private String pathOf(ZipFile zip, ResourceDescriptor resource) throws IOException {
        ZipEntry manifestEntry = manifestName.isEmpty() ? null : zip.getEntry(manifestName);
        if (manifestEntry == null) {
            return null;
        }
        Properties manifest = new Properties();
        try (Reader reader = new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        String key = "model." + resource.id() + ".";
        String path = manifest.getProperty(key + "path", "").trim();
        String listedSha = manifest.getProperty(key + "sha256", "").trim().toLowerCase(Locale.ROOT);
        String listedSize = manifest.getProperty(key + "size", "").trim();
        if (path.isEmpty() || !listedSha.equals(resource.sha256())
                || !listedSize.equals(String.valueOf(resource.size()))) {
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
