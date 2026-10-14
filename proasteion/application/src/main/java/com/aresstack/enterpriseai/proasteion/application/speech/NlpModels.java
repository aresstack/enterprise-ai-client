package com.aresstack.enterpriseai.proasteion.application.speech;

import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.application.resources.DownloadRetry;
import com.aresstack.enterpriseai.proasteion.application.resources.OfflineResourceBundle;
import com.aresstack.enterpriseai.proasteion.application.resources.OfflineResourceBundleExporter;
import com.aresstack.enterpriseai.proasteion.application.resources.ResourceDescriptor;
import com.aresstack.enterpriseai.proasteion.application.resources.ResourceInstaller;
import com.aresstack.enterpriseai.speech.api.TextAnalysisEngine;
import com.aresstack.enterpriseai.speech.api.TextAnalysisEngineLoader;
import com.aresstack.enterpriseai.speech.opennlp.OpenNlpTextAnalysisEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    /** Tokenizer model of a language, for the linguistic analysis of chat messages. */
    public static final String TOKENIZE = "tokenize";
    /** Part-of-speech model of a language, for the linguistic analysis of chat messages. */
    public static final String POS = "pos";

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
        private final ResourceDescriptor descriptor;

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
            this.descriptor = new ResourceDescriptor(id, url, file, sha256, size);
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

    private final Path root;
    private final List<Entry> entries;
    private final ResourceInstaller installer;

    NlpModels(Path root, List<Entry> entries) {
        this(root, entries, OfflineResourceBundle.none());
    }

    NlpModels(Path root, List<Entry> entries, OfflineResourceBundle local) {
        this(root, entries, local, DownloadRetry.once());
    }

    NlpModels(Path root, List<Entry> entries, OfflineResourceBundle local, DownloadRetry retry) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root;
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        this.installer = new ResourceInstaller(local, retry);
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
        return new NlpModels(root, entries, new OfflineResourceBundle(archiveDirectories,
                p.getProperty("archive.prefix"), p.getProperty("archive.manifest"), p.getProperty("archive.directory"),
                p.getProperty("archive.localModelsDirectory")),
                DownloadRetry.parse(p.getProperty("download.attempts"), p.getProperty("download.retryPausesMillis")));
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

    /** Whether a model of {@code capability} ({@link #TOKENIZE}, {@link #POS} …) is installed for {@code language}. */
    public boolean hasModel(String capability, String language) {
        for (Entry entry : entries) {
            if (entry.capability.equals(capability) && entry.language.equals(language) && isInstalled(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Installiert = Manifest und Datei liegen da und das Manifest nennt den SHA-256 des Katalogs (ein neues Modell
     * im Katalog gilt so als nicht installiert).
     */
    public boolean isInstalled(Entry entry) {
        Path manifest = directory(entry).resolve(MANIFEST);
        if (!Files.isRegularFile(manifest) || !Files.isRegularFile(directory(entry).resolve(entry.file))) {
            return false;
        }
        Properties recorded = new Properties();
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            recorded.load(reader);
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
        String sha = recorded.getProperty("sha256", "").trim();
        return sha.isEmpty() || sha.equalsIgnoreCase(entry.sha256);
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
    public ResourceInstaller.Source source(Entry entry) {
        if (isInstalled(entry)) {
            return ResourceInstaller.Source.INSTALLED;
        }
        return installer.offersLocally(entry.descriptor) ? ResourceInstaller.Source.ARCHIVE
                : ResourceInstaller.Source.DOWNLOAD;
    }

    /** Wie das Ressourcen-Archiv neben dem Jar zum zuletzt daraus installierten steht. */
    public ResourceInstaller.BundleState bundleState() {
        return installer.bundleState(root);
    }

    /**
     * Installiert das Modell aus dem Ressourcen-Archiv, wenn es dort mit genau Größe und SHA-256 liegt, sonst per
     * Download; erst nach der Prüfung werden Datei und zuletzt das Manifest geschrieben. Blockiert.
     *
     * @param network die Netzwerkdienste für den Download oder {@code null} (dann nur aus dem Archiv)
     * @return woher das Modell kam ({@link ResourceInstaller.Source#INSTALLED}, wenn es schon da war)
     */
    public ResourceInstaller.Source install(Entry entry, NetworkServices network) throws IOException {
        if (isInstalled(entry)) {
            return ResourceInstaller.Source.INSTALLED;
        }
        ResourceInstaller.Fetched fetched = installer.fetch(entry.descriptor, network);
        Path dir = directory(entry);
        Files.createDirectories(dir);
        ResourceInstaller.atomicWrite(dir.resolve(entry.file), fetched.bytes());
        Properties manifest = new Properties();
        manifest.setProperty("id", entry.id);
        manifest.setProperty("capability", entry.capability);
        manifest.setProperty("language", entry.language);
        manifest.setProperty("file", entry.file);
        manifest.setProperty("sha256", entry.sha256);
        manifest.setProperty("source", fetched.source().name());
        ResourceInstaller.writeProperties(dir.resolve(MANIFEST), manifest);
        if (fetched.source() == ResourceInstaller.Source.ARCHIVE) {
            installer.recordBundle(root);
        }
        return fetched.source();
    }

    /** Das Offline-Paket neben dem Jar, aus dem vor dem Download installiert wird (auch für lokale Modelle). */
    public OfflineResourceBundle offlineBundle() {
        return installer.bundle();
    }

    /** Nimmt die installierten Modelle, deren Datei genau dem Katalog entspricht, in ein Offline-Paket auf. */
    public void addInstalledTo(OfflineResourceBundleExporter exporter) {
        for (Entry entry : entries) {
            if (isInstalled(entry)) {
                exporter.addResource(entry.descriptor, directory(entry).resolve(entry.file));
            }
        }
    }

    /** Lädt beim Vorlesen, was gerade installiert ist (wie arch bei jedem Start der Engine). */
    public TextAnalysisEngineLoader loader() {
        return new TextAnalysisEngineLoader() {
            @Override
            public TextAnalysisEngine load() {
                Path languageModel = null;
                Map<String, Path> sentenceModels = new LinkedHashMap<String, Path>();
                Map<String, Path> tokenizerModels = new LinkedHashMap<String, Path>();
                Map<String, Path> posModels = new LinkedHashMap<String, Path>();
                for (Entry entry : entries) {
                    if (!isInstalled(entry)) {
                        continue;
                    }
                    Path file = directory(entry).resolve(entry.file);
                    if (LANGUAGE.equals(entry.capability) && languageModel == null) {
                        languageModel = file;
                    } else if (SENTENCE.equals(entry.capability)) {
                        sentenceModels.put(entry.language, file);
                    } else if (TOKENIZE.equals(entry.capability)) {
                        tokenizerModels.put(entry.language, file);
                    } else if (POS.equals(entry.capability)) {
                        posModels.put(entry.language, file);
                    }
                }
                OpenNlpTextAnalysisEngine engine = OpenNlpTextAnalysisEngine.load(languageModel, sentenceModels,
                        tokenizerModels, posModels);
                for (String problem : engine.problems()) {
                    LOG.log(Level.WARNING, "OpenNLP-Modell nicht ladbar: {0}", problem);
                }
                LOG.info("Textanalyse: " + engine);
                return engine;
            }
        };
    }

    private Path directory(Entry entry) {
        return root.resolve(entry.id.replaceAll("[^a-zA-Z0-9._-]", "_"));
    }
}
