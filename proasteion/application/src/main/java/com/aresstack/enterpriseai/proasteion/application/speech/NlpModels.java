package com.aresstack.enterpriseai.proasteion.application.speech;

import com.aresstack.enterpriseai.proasteion.application.net.HttpConnections;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.platform.network.HttpRoute;
import com.aresstack.enterpriseai.speech.api.TextAnalysisEngine;
import com.aresstack.enterpriseai.speech.api.TextAnalysisEngineLoader;
import com.aresstack.enterpriseai.speech.opennlp.OpenNlpTextAnalysisEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die OpenNLP-Modelle des Vorlesens wie in askai-java8 arch ({@code ApacheOpenNlpModelCatalogProvider},
 * {@code LocalNlpModelStore}, {@code NlpModelInstaller}): die kuratierte Liste aus {@code nlp-models.properties},
 * Installation nur auf Klick mit genauer Größe und SHA-256 (sonst wird nichts geschrieben), Ablage je Modell unter
 * {@code <wurzel>/<id>/} mit einem kleinen Manifest, das als Letztes geschrieben wird. {@link #loader()} lädt beim
 * Vorlesen, was installiert ist.
 */
public final class NlpModels {

    static final String RESOURCE = "nlp-models.properties";
    static final String MANIFEST = "nlp-model.properties";
    static final String SENTENCE = "sentence";
    static final String LANGUAGE = "language";
    private static final int MAX_REDIRECTS = 5;

    private static final Logger LOG = Logger.getLogger(NlpModels.class.getName());

    /**
     * Die OpenNLP-Modelle der Anwendung unter {@code <Anwendungsverzeichnis>/models/nlp}; das Ressourcen-Archiv wird
     * neben dem Jar und danach im Anwendungsverzeichnis gesucht.
     */
    public static NlpModels inApplicationDirectory() {
        Path appDirectory = com.aresstack.enterpriseai.proasteion.application.config.AppPaths.appDirectory();
        List<Path> archiveDirectories = new ArrayList<Path>();
        Path jarDirectory = jarDirectory();
        if (jarDirectory != null) {
            archiveDirectories.add(jarDirectory);
        }
        archiveDirectories.add(appDirectory);
        return standard(appDirectory.resolve("models").resolve("nlp"), archiveDirectories);
    }

    /** Das Verzeichnis des laufenden Jars oder {@code null} beim Start aus Klassenverzeichnissen. */
    private static Path jarDirectory() {
        try {
            java.security.CodeSource source = NlpModels.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path location = java.nio.file.Paths.get(source.getLocation().toURI());
                if (Files.isRegularFile(location) && location.getFileName().toString().endsWith(".jar")) {
                    return location.toAbsolutePath().getParent();
                }
            }
        } catch (Exception unknown) {
            LOG.log(Level.FINE, "Jar-Verzeichnis unbekannt", unknown);
        }
        return null;
    }

    /**
     * Eine Textanalyse wie askai arch über {@link #inApplicationDirectory()}: geladen bei der ersten Anfrage,
     * entladen eine Minute nach der letzten.
     */
    public static com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService textAnalysis() {
        return new com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService(
                inApplicationDirectory().loader(),
                com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService.DEFAULT_STOP_GRACE_MILLIS);
    }

    /** Ein Modell der Liste. */
    public static final class Entry {
        private final String id;
        private final String name;
        private final String capability;
        private final String language;
        private final URI url;
        private final String file;
        private final String sha256;
        private final long size;

        Entry(String id, String name, String capability, String language, URI url, String file, String sha256,
              long size) {
            this.id = id;
            this.name = name;
            this.capability = capability;
            this.language = language;
            this.url = url;
            this.file = file;
            this.sha256 = sha256;
            this.size = size;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        /** {@code sentence}, {@code language} oder {@code pos}. */
        public String capability() {
            return capability;
        }

        public String language() {
            return language;
        }

        public String file() {
            return file;
        }
    }

    /** Woher ein Modell beim Installieren käme. */
    public enum Source {
        /** Schon installiert. */
        INSTALLED,
        /** Aus dem Ressourcen-Archiv neben dem Jar. */
        ARCHIVE,
        /** Per Download. */
        DOWNLOAD
    }

    private final Path root;
    private final List<Entry> entries;
    private final NlpResourceArchive archive;

    NlpModels(Path root, List<Entry> entries) {
        this(root, entries, new NlpResourceArchive(Collections.<Path>emptyList(), null, null));
    }

    NlpModels(Path root, List<Entry> entries, NlpResourceArchive archive) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root;
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        this.archive = archive;
    }

    /** Die Liste aus {@code nlp-models.properties}, abgelegt unter {@code root}, ohne Ressourcen-Archiv. */
    public static NlpModels standard(Path root) {
        return standard(root, Collections.<Path>emptyList());
    }

    /**
     * Die Liste aus {@code nlp-models.properties}, abgelegt unter {@code root}; das Ressourcen-Archiv wird in
     * {@code archiveDirectories} der Reihe nach gesucht.
     */
    public static NlpModels standard(Path root, List<Path> archiveDirectories) {
        Properties p = new Properties();
        try (InputStream in = NlpModels.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " missing");
            }
            try (Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " unreadable", e);
        }
        List<Entry> entries = new ArrayList<Entry>();
        for (String id : p.getProperty("models", "").split(",")) {
            String key = "model." + id.trim() + ".";
            if (id.trim().isEmpty()) {
                continue;
            }
            entries.add(new Entry(id.trim(), p.getProperty(key + "name", id.trim()),
                    p.getProperty(key + "capability", ""), p.getProperty(key + "language", ""),
                    URI.create(p.getProperty(key + "url", "").trim()), p.getProperty(key + "file", "").trim(),
                    p.getProperty(key + "sha256", "").trim().toLowerCase(java.util.Locale.ROOT),
                    Long.parseLong(p.getProperty(key + "size", "-1").trim())));
        }
        return new NlpModels(root, entries, new NlpResourceArchive(archiveDirectories,
                p.getProperty("archive.prefix"), p.getProperty("archive.manifest")));
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Ob das Satzmodell der Sprache ({@code de}, {@code en}) installiert ist. */
    public boolean hasSentenceModel(String language) {
        for (Entry entry : entries) {
            if (SENTENCE.equals(entry.capability) && entry.language.equals(language) && isInstalled(entry)) {
                return true;
            }
        }
        return false;
    }

    /** Installiert = Manifest und Datei liegen da. */
    public boolean isInstalled(Entry entry) {
        return Files.isRegularFile(directory(entry).resolve(MANIFEST))
                && Files.isRegularFile(directory(entry).resolve(entry.file));
    }

    /** Das Modell mit dieser Id oder {@code null}. */
    public Entry entry(String id) {
        for (Entry entry : entries) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Woher das Modell beim Installieren käme: installiert, aus dem Ressourcen-Archiv oder per Download. */
    public Source source(Entry entry) {
        if (isInstalled(entry)) {
            return Source.INSTALLED;
        }
        return archive.offers(entry.id, entry.size, entry.sha256) ? Source.ARCHIVE : Source.DOWNLOAD;
    }

    /**
     * Installiert das Modell aus dem Ressourcen-Archiv, wenn es dort mit genau Größe und SHA-256 liegt, sonst per
     * Download (Weiterleitungen werden je Ziel neu geroutet); erst nach der Prüfung werden Datei und zuletzt das
     * Manifest geschrieben. Blockiert.
     *
     * @return woher das Modell kam ({@link Source#INSTALLED}, wenn es schon da war)
     */
    public Source install(Entry entry, NetworkServices network) throws IOException {
        if (isInstalled(entry)) {
            return Source.INSTALLED;
        }
        byte[] bytes = null;
        try {
            bytes = archive.read(entry.id, entry.size, entry.sha256);
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(Level.WARNING, "Ressourcen-Archiv nicht lesbar, lade " + entry.file, unreadable);
        }
        Source source = bytes == null ? Source.DOWNLOAD : Source.ARCHIVE;
        if (bytes == null) {
            if (network == null) {
                throw new IOException(entry.file + ": nicht im Ressourcen-Archiv und kein Netzwerk angegeben");
            }
            bytes = download(entry.url, network);
        }
        if (entry.size >= 0 && bytes.length != entry.size) {
            throw new IOException(entry.file + ": " + bytes.length + " Bytes statt " + entry.size);
        }
        String actual = sha256(bytes);
        if (!actual.equals(entry.sha256)) {
            throw new IOException(entry.file + ": SHA-256 stimmt nicht (" + actual + ")");
        }
        Path dir = directory(entry);
        Files.createDirectories(dir);
        atomicWrite(dir.resolve(entry.file), bytes);
        Properties manifest = new Properties();
        manifest.setProperty("id", entry.id);
        manifest.setProperty("capability", entry.capability);
        manifest.setProperty("language", entry.language);
        manifest.setProperty("file", entry.file);
        manifest.setProperty("sha256", entry.sha256);
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        try (Writer writer = new java.io.OutputStreamWriter(text, StandardCharsets.UTF_8)) {
            manifest.store(writer, null);
        }
        atomicWrite(dir.resolve(MANIFEST), text.toByteArray());
        return source;
    }

    /** Lädt beim Vorlesen, was gerade installiert ist (wie arch bei jedem Start der Engine). */
    public TextAnalysisEngineLoader loader() {
        return new TextAnalysisEngineLoader() {
            @Override
            public TextAnalysisEngine load() {
                Path languageModel = null;
                Map<String, Path> sentenceModels = new LinkedHashMap<String, Path>();
                for (Entry entry : entries) {
                    if (!isInstalled(entry)) {
                        continue;
                    }
                    Path file = directory(entry).resolve(entry.file);
                    if (LANGUAGE.equals(entry.capability) && languageModel == null) {
                        languageModel = file;
                    } else if (SENTENCE.equals(entry.capability)) {
                        sentenceModels.put(entry.language, file);
                    }
                }
                OpenNlpTextAnalysisEngine engine = OpenNlpTextAnalysisEngine.load(languageModel, sentenceModels);
                for (String problem : engine.problems()) {
                    LOG.log(Level.WARNING, "OpenNLP-Modell nicht ladbar: {0}", problem);
                }
                LOG.info("Textanalyse beim Vorlesen: " + engine);
                return engine;
            }
        };
    }

    private Path directory(Entry entry) {
        return root.resolve(entry.id.replaceAll("[^a-zA-Z0-9._-]", "_"));
    }

    private static byte[] download(URI url, NetworkServices network) throws IOException {
        URI target = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpRoute route = network.routes().routeFor(target);
            HttpURLConnection connection = HttpConnections.open(target, route, network.tls(), network.userAgent());
            try {
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(false);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("HTTP " + code + " ohne Ziel: " + target.getHost());
                    }
                    target = target.resolve(location);
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + code + " von " + target.getHost());
                }
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                    }
                    return out.toByteArray();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("zu viele Weiterleitungen: " + url.getHost());
    }

    static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 nicht verfügbar", e);
        }
    }

    private static void atomicWrite(Path target, byte[] content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            out.write(content);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
