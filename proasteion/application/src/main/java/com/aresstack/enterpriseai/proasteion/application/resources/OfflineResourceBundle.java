package com.aresstack.enterpriseai.proasteion.application.resources;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallCancelledException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.api.OfflineModelPackage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Das Offline-Paket neben dem Jar ({@code <prefix>*.zip}), Nachfolger des Ressourcen-Archivs: ein ZIP mit Manifest,
 * aus dem ohne Download installiert wird. Es führt einzelne Ressourcen (OpenNLP-Modelle, Manifest
 * {@code model.<id>.path/sha256/size}, wie das Ressourcen-Archiv des Release-Workflows) und, seit dem Format
 * {@value #FORMAT}, ganze lokale Modellordner ({@code local-models}, {@code local-model.<id>.path/category} und je
 * Datei {@code local-model.<id>.file.<n>.path/size/sha256}). Eine Ressource gilt nur dann als vorhanden, wenn das
 * Manifest sie mit genau der Größe und dem SHA-256 ihrer Beschreibung führt; beim Lesen und Entpacken wird beides am
 * Inhalt noch einmal geprüft.
 */
public final class OfflineResourceBundle implements OfflineModelPackage {

    /** Kennung des Formats mit lokalen Modellordnern ({@code bundle.format}). */
    public static final String FORMAT = "enterprise-ai-client-offline";
    /** Version dieses Formats ({@code bundle.version}). */
    public static final int VERSION = 1;
    static final String DEFAULT_RESOURCE_DIRECTORY = "opennlp";
    static final String DEFAULT_MODEL_DIRECTORY = "local-models";
    /** Größte Datei, die {@link #readFile} in den Speicher liest (Herkunfts- und Konfigurationsdateien). */
    private static final long SMALL_FILE_LIMIT = 1024L * 1024L;
    private static final String TIMESTAMP = "yyyyMMdd-HHmmss";

    private final List<Path> directories;
    private final String prefix;
    private final String manifestName;
    private final String resourceDirectory;
    private final String modelDirectory;

    /** Bundle-Hash zuletzt gelesen für (Pfad, Größe, Änderungszeit). */
    private String hashedKey;
    private String hashedValue;

    /**
     * @param directories  die Verzeichnisse, in denen der Reihe nach gesucht wird (meist Jar-, dann
     *                     Anwendungsverzeichnis)
     * @param prefix       Namensanfang des ZIP, leer = kein Bundle
     * @param manifestName Name des Manifests im ZIP
     */
    public OfflineResourceBundle(List<Path> directories, String prefix, String manifestName) {
        this(directories, prefix, manifestName, DEFAULT_RESOURCE_DIRECTORY, DEFAULT_MODEL_DIRECTORY);
    }

    /**
     * @param resourceDirectory Ordner der einzelnen Ressourcen im ZIP (beim Erstellen)
     * @param modelDirectory    Ordner der lokalen Modellordner im ZIP (beim Erstellen)
     */
    public OfflineResourceBundle(List<Path> directories, String prefix, String manifestName,
                                 String resourceDirectory, String modelDirectory) {
        this.directories = Collections.unmodifiableList(new ArrayList<Path>(directories));
        this.prefix = prefix == null ? "" : prefix.trim();
        this.manifestName = manifestName == null ? "" : manifestName.trim();
        this.resourceDirectory = blankOr(resourceDirectory, DEFAULT_RESOURCE_DIRECTORY);
        this.modelDirectory = blankOr(modelDirectory, DEFAULT_MODEL_DIRECTORY);
    }

    /** Eine Quelle ohne Bundle. */
    public static OfflineResourceBundle none() {
        return new OfflineResourceBundle(Collections.<Path>emptyList(), null, null);
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
     * Die Installationsversion des Bundles oder {@code null} ohne Bundle: bei einem Offline-Paket der SHA-256 seines
     * Manifests (es enthält Größe und SHA-256 jeder Datei, ein großes Paket wird so nicht ganz gelesen), sonst der
     * SHA-256 des ganzen ZIP. Zwischengespeichert, solange Pfad, Größe und Änderungszeit gleich bleiben.
     */
    public synchronized String bundleSha256() throws IOException {
        Path archive = locate();
        if (archive == null) {
            return null;
        }
        String key = archive.toAbsolutePath() + "|" + Files.size(archive) + "|"
                + Files.getLastModifiedTime(archive).toMillis();
        if (!key.equals(hashedKey)) {
            byte[] manifest = manifestBytes(archive);
            hashedValue = manifest != null && FORMAT.equals(load(manifest).getProperty("bundle.format", "").trim())
                    ? ResourceInstaller.sha256(manifest) : ResourceInstaller.sha256(archive);
            hashedKey = key;
        }
        return hashedValue;
    }

    /** Ein Exporter mit dem Aufbau dieses Bundles (Manifestname, Ordner im ZIP). */
    public OfflineResourceBundleExporter exporter() {
        return new OfflineResourceBundleExporter(manifestName, resourceDirectory, modelDirectory);
    }

    /**
     * Der Vorschlag für ein neues Offline-Paket: {@code <prefix>-yyyyMMdd-HHmmss.zip}. So liegt es, neben das Jar
     * kopiert, als neuestes Bundle vor den Ressourcen-Archiven der Releases.
     */
    public String exportFileName(Date now) {
        return prefix + "-" + new SimpleDateFormat(TIMESTAMP, Locale.ROOT).format(now) + ".zip";
    }

    /** Das erste vorhandene Suchverzeichnis (meist das des Jars) oder {@code null}. */
    public Path exportDirectory() {
        for (Path directory : directories) {
            if (directory != null && Files.isDirectory(directory)) {
                return directory;
            }
        }
        return null;
    }

    @Override
    public List<String> modelIds() {
        Properties manifest = manifest();
        List<String> ids = new ArrayList<String>();
        if (manifest == null) {
            return ids;
        }
        for (String id : manifest.getProperty("local-models", "").split(",")) {
            if (!id.trim().isEmpty() && files(manifest, id.trim()) != null) {
                ids.add(id.trim());
            }
        }
        return ids;
    }

    @Override
    public ModelCategory category(String id) {
        Properties manifest = manifest();
        if (manifest == null || files(manifest, id) == null) {
            return null;
        }
        try {
            return ModelCategory.valueOf(manifest.getProperty("local-model." + id + ".category", "").trim());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    @Override
    public byte[] readFile(String id, String relativePath) throws IOException {
        Path archive = locate();
        Properties manifest = manifest();
        List<PackedFile> files = manifest == null ? null : files(manifest, id);
        if (files == null) {
            return null;
        }
        for (PackedFile file : files) {
            if (!file.relative.equals(relativePath) || file.size > SMALL_FILE_LIMIT) {
                continue;
            }
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                ZipEntry entry = zip.getEntry(file.zipPath);
                if (entry == null) {
                    return null;
                }
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = readAll(in, file.size);
                }
                return bytes != null && bytes.length == file.size
                        && ResourceInstaller.sha256(bytes).equals(file.sha256) ? bytes : null;
            }
        }
        return null;
    }

    @Override
    public void extract(String id, Path directory, LocalVoiceInstallListener listener,
                        InstallCancellation cancellation) throws IOException, LocalVoiceInstallCancelledException {
        Path archive = locate();
        Properties manifest = manifest();
        List<PackedFile> files = manifest == null ? null : files(manifest, id);
        if (files == null) {
            throw new IOException(id + ": nicht im Offline-Paket");
        }
        Path root = directory.toAbsolutePath().normalize();
        byte[] buffer = new byte[64 * 1024];
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (int i = 0; i < files.size(); i++) {
                PackedFile file = files.get(i);
                Path target = root.resolve(file.relative).normalize();
                if (!target.startsWith(root) || target.equals(root)) {
                    throw new IOException(id + ": ungültiger Pfad im Offline-Paket: " + file.relative);
                }
                ZipEntry entry = zip.getEntry(file.zipPath);
                if (entry == null) {
                    throw new IOException(id + ": " + file.zipPath + " fehlt im Offline-Paket");
                }
                Files.createDirectories(target.getParent());
                MessageDigest digest = ResourceInstaller.sha256Digest();
                long done = 0;
                String name = target.getFileName().toString();
                try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target)) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (cancellation != null && cancellation.isCancelled()) {
                            throw new LocalVoiceInstallCancelledException(
                                    "Entpacken abgebrochen. Das Modell wurde nicht installiert.");
                        }
                        done += read;
                        if (done > file.size) {
                            break;
                        }
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                        if (listener != null) {
                            listener.progress(name, i + 1, files.size(), done, file.size);
                        }
                    }
                }
                String actual = ResourceInstaller.hex(digest.digest());
                if (done != file.size || !actual.equals(file.sha256)) {
                    Files.deleteIfExists(target);
                    throw new IOException(file.relative + " stimmt nicht mit dem Manifest überein (" + done
                            + " statt " + file.size + " Bytes oder anderer SHA-256)");
                }
            }
        }
    }

    /** Das Manifest des gefundenen Bundles oder {@code null}. */
    private Properties manifest() {
        Path archive = locate();
        if (archive == null) {
            return null;
        }
        try {
            byte[] bytes = manifestBytes(archive);
            return bytes == null ? null : load(bytes);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private byte[] manifestBytes(Path archive) throws IOException {
        if (manifestName.isEmpty()) {
            return null;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(manifestName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return readAll(in, -1);
            }
        }
    }

    private static Properties load(byte[] bytes) throws IOException {
        Properties manifest = new Properties();
        try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        return manifest;
    }

    /** Die Dateien des Modellordners laut Manifest oder {@code null}, wenn das Modell fehlt oder unvollständig ist. */
    private static List<PackedFile> files(Properties manifest, String id) {
        if (id == null || !FORMAT.equals(manifest.getProperty("bundle.format", "").trim())) {
            return null;
        }
        String key = "local-model." + id + ".";
        String base = manifest.getProperty(key + "path", "").trim();
        int count;
        try {
            count = Integer.parseInt(manifest.getProperty(key + "files", "").trim());
        } catch (NumberFormatException missing) {
            return null;
        }
        if (base.isEmpty() || count <= 0) {
            return null;
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        List<PackedFile> files = new ArrayList<PackedFile>();
        for (int n = 1; n <= count; n++) {
            String fileKey = key + "file." + n + ".";
            String relative = manifest.getProperty(fileKey + "path", "").trim();
            String sha = manifest.getProperty(fileKey + "sha256", "").trim().toLowerCase(Locale.ROOT);
            long size;
            try {
                size = Long.parseLong(manifest.getProperty(fileKey + "size", "").trim());
            } catch (NumberFormatException missing) {
                return null;
            }
            if (relative.isEmpty() || sha.isEmpty() || size < 0) {
                return null;
            }
            files.add(new PackedFile(relative, base + relative, size, sha));
        }
        return files;
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

    private static String blankOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    /** Eine Datei eines Modellordners im Paket. */
    private static final class PackedFile {
        final String relative;
        final String zipPath;
        final long size;
        final String sha256;

        PackedFile(String relative, String zipPath, long size, String sha256) {
            this.relative = relative;
            this.zipPath = zipPath;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
