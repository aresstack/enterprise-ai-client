package com.aresstack.enterpriseai.proasteion.application.resources;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Schreibt ein Offline-Paket ({@link OfflineResourceBundle}): einzelne Ressourcen unter {@code <resourceDirectory>/},
 * ganze Modellordner unter {@code <modelDirectory>/<id>/}, ohne Kompression (Modelle lassen sich kaum packen, so
 * geht es schnell). Größe und SHA-256 jeder Datei entstehen beim Schreiben; das Manifest ist der letzte Eintrag.
 * Geschrieben wird nach {@code <Ziel>.part}, das erst vollständig auf den Zielnamen umbenannt wird; ein Abbruch oder
 * Fehler löscht die Teildatei, es bleibt nie ein gültig aussehendes ZIP zurück.
 */
public final class OfflineResourceBundleExporter {

    /** Fortschritt je Datei; wird vom schreibenden Thread gerufen. */
    public interface Progress {
        /**
         * @param name   Name der Datei, die gerade geschrieben wird
         * @param number laufende Nummer dieser Datei, ab 1
         * @param count  Anzahl aller Dateien
         * @param done   bisher geschriebene Bytes dieser Datei
         * @param total  Größe dieser Datei
         */
        void file(String name, int number, int count, long done, long total);
    }

    /** Der Benutzer hat das Erstellen abgebrochen; die Teildatei ist gelöscht. */
    public static final class Cancelled extends IOException {
        private static final long serialVersionUID = 1L;

        Cancelled() {
            super("Erstellen abgebrochen. Es wurde kein Offline-Paket geschrieben.");
        }
    }

    /** Was im fertigen Paket steht. */
    public static final class Summary {
        private final int resources;
        private final int models;
        private final long bytes;

        Summary(int resources, int models, long bytes) {
            this.resources = resources;
            this.models = models;
            this.bytes = bytes;
        }

        /** Anzahl der einzelnen Ressourcen (OpenNLP-Modelle). */
        public int resources() {
            return resources;
        }

        /** Anzahl der lokalen Modellordner. */
        public int models() {
            return models;
        }

        /** Größe des Pakets in Bytes. */
        public long bytes() {
            return bytes;
        }
    }

    private final String manifestName;
    private final String resourceDirectory;
    private final String modelDirectory;
    private final List<Resource> resources = new ArrayList<Resource>();
    private final List<Model> models = new ArrayList<Model>();

    OfflineResourceBundleExporter(String manifestName, String resourceDirectory, String modelDirectory) {
        if (manifestName == null || manifestName.trim().isEmpty()) {
            throw new IllegalArgumentException("manifestName must not be empty");
        }
        this.manifestName = manifestName.trim();
        this.resourceDirectory = resourceDirectory;
        this.modelDirectory = modelDirectory;
    }

    /** Nimmt eine installierte Ressource auf; im Manifest steht sie nur, wenn sie genau ihrer Beschreibung entspricht. */
    public void addResource(ResourceDescriptor descriptor, Path file) {
        if (descriptor == null || file == null) {
            throw new IllegalArgumentException("descriptor and file must not be null");
        }
        resources.add(new Resource(descriptor, file));
    }

    /**
     * Nimmt einen vollständig installierten Modellordner mit allen Dateien und Unterordnern auf (versteckte Einträge
     * und Teildateien ausgenommen).
     *
     * @param category die Kategorie des Modells (Name von {@code ModelCategory})
     */
    public void addModel(String id, String category, Path directory) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9._-]+") || id.startsWith(".") || directory == null) {
            throw new IllegalArgumentException("invalid model id or directory: " + id);
        }
        final Path root = directory;
        final List<Path> files = new ArrayList<Path>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                return !dir.equals(root) && hidden(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String name = file.getFileName().toString();
                if (attributes.isRegularFile() && !hidden(file) && !name.endsWith(".part")
                        && !name.endsWith(".tmp")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files);
        if (!files.isEmpty()) {
            models.add(new Model(id, category == null ? "" : category, root, files));
        }
    }

    /** Ob nichts aufzunehmen ist. */
    public boolean isEmpty() {
        return resources.isEmpty() && models.isEmpty();
    }

    /**
     * Schreibt das Paket nach {@code target}. Blockiert.
     *
     * @throws Cancelled wenn {@code cancellation} den Abbruch verlangt; dann bleibt keine Datei zurück
     */
    public Summary export(Path target, Progress progress, InstallCancellation cancellation) throws IOException {
        int count = resources.size();
        for (Model model : models) {
            count += model.files.size();
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(part);
        boolean finished = false;
        int writtenResources = 0;
        try {
            Properties manifest = new Properties();
            manifest.setProperty("bundle.format", OfflineResourceBundle.FORMAT);
            manifest.setProperty("bundle.version", String.valueOf(OfflineResourceBundle.VERSION));
            byte[] buffer = new byte[64 * 1024];
            int number = 0;
            try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(part));
                 ZipOutputStream zip = new ZipOutputStream(file)) {
                zip.setLevel(Deflater.NO_COMPRESSION);
                for (Resource resource : resources) {
                    number++;
                    String path = resourceDirectory + "/" + resource.descriptor.file();
                    Written written = write(zip, path, resource.file, number, count, buffer, progress, cancellation);
                    ResourceDescriptor expected = resource.descriptor;
                    if (written.sha256.equals(expected.sha256())
                            && (expected.size() < 0 || written.size == expected.size())) {
                        String key = "model." + expected.id() + ".";
                        manifest.setProperty(key + "path", path);
                        manifest.setProperty(key + "sha256", written.sha256);
                        manifest.setProperty(key + "size", String.valueOf(written.size));
                        writtenResources++;
                    }
                }
                StringBuilder ids = new StringBuilder();
                for (Model model : models) {
                    String base = modelDirectory + "/" + model.id + "/";
                    String key = "local-model." + model.id + ".";
                    manifest.setProperty(key + "path", base);
                    manifest.setProperty(key + "category", model.category);
                    manifest.setProperty(key + "files", String.valueOf(model.files.size()));
                    for (int n = 0; n < model.files.size(); n++) {
                        number++;
                        String relative = model.root.relativize(model.files.get(n)).toString()
                                .replace(model.root.getFileSystem().getSeparator(), "/");
                        Written written = write(zip, base + relative, model.files.get(n), number, count, buffer,
                                progress, cancellation);
                        String fileKey = key + "file." + (n + 1) + ".";
                        manifest.setProperty(fileKey + "path", relative);
                        manifest.setProperty(fileKey + "size", String.valueOf(written.size));
                        manifest.setProperty(fileKey + "sha256", written.sha256);
                    }
                    ids.append(ids.length() == 0 ? "" : ",").append(model.id);
                }
                manifest.setProperty("local-models", ids.toString());
                stopIfCancelled(cancellation);
                zip.putNextEntry(new ZipEntry(manifestName));
                ByteArrayOutputStream text = new ByteArrayOutputStream();
                try (Writer writer = new java.io.OutputStreamWriter(text, StandardCharsets.UTF_8)) {
                    manifest.store(writer, null);
                }
                zip.write(text.toByteArray());
                zip.closeEntry();
                zip.finish();
            }
            stopIfCancelled(cancellation);
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
            finished = true;
            return new Summary(writtenResources, models.size(), Files.size(target));
        } finally {
            if (!finished) {
                Files.deleteIfExists(part);
            }
        }
    }

    private static Written write(ZipOutputStream zip, String path, Path source, int number, int count,
                                 byte[] buffer, Progress progress, InstallCancellation cancellation)
            throws IOException {
        stopIfCancelled(cancellation);
        long total = Files.size(source);
        String name = source.getFileName().toString();
        MessageDigest digest = ResourceInstaller.sha256Digest();
        long done = 0;
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream in = Files.newInputStream(source)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                stopIfCancelled(cancellation);
                zip.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                if (progress != null) {
                    progress.file(name, number, count, done, total);
                }
            }
        }
        zip.closeEntry();
        return new Written(done, ResourceInstaller.hex(digest.digest()));
    }

    private static void stopIfCancelled(InstallCancellation cancellation) throws Cancelled {
        if (cancellation != null && cancellation.isCancelled()) {
            throw new Cancelled();
        }
    }

    private static boolean hidden(Path path) {
        return path.getFileName() != null && path.getFileName().toString().startsWith(".");
    }

    private static final class Written {
        final long size;
        final String sha256;

        Written(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }

    private static final class Resource {
        final ResourceDescriptor descriptor;
        final Path file;

        Resource(ResourceDescriptor descriptor, Path file) {
            this.descriptor = descriptor;
            this.file = file;
        }
    }

    private static final class Model {
        final String id;
        final String category;
        final Path root;
        final List<Path> files;

        Model(String id, String category, Path root, List<Path> files) {
            this.id = id;
            this.category = category;
            this.root = root;
            this.files = files;
        }
    }
}
