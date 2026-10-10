package com.aresstack.enterpriseai.source.localfiles;

import com.aresstack.enterpriseai.document.api.ContentCategory;
import com.aresstack.enterpriseai.document.api.ContentDetector;
import com.aresstack.enterpriseai.document.api.DetectedContentType;
import com.aresstack.enterpriseai.document.api.ExtractedBlock;
import com.aresstack.enterpriseai.document.api.ExtractedDocument;
import com.aresstack.enterpriseai.document.api.ExtractionRegistry;
import com.aresstack.enterpriseai.document.api.ExtractionRequest;
import com.aresstack.enterpriseai.document.api.ExtractionResult;
import com.aresstack.enterpriseai.document.api.ResourceExtractor;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ein lokales Verzeichnis als {@link KnowledgeSourcePort}.
 *
 * <ul>
 *   <li>Startpunkte im {@link SourceScope} sind Pfade relativ zum Wurzelverzeichnis ({@code .} = die Wurzel) oder
 *       einzelne Dateien; Verzeichnisse werden bis {@code maxDepth} Ebenen tief gelesen ({@code 0} = nur die Dateien
 *       direkt darin). Versteckte Dateien, Office-Sperrdateien ({@code ~$…}) und Dateien über der Größengrenze
 *       werden übersprungen, ebenso Dateien, deren Endung keinen Dokumenttyp ergibt.</li>
 *   <li>Resource-IDs: {@code file:<Quell-ID>/<relativer Pfad mit />}; Revision: Änderungszeit und Größe.</li>
 *   <li>Inhalt: Erkennung per {@link ContentDetector}, Extraktion über das erste passende
 *       {@link ResourceExtractor} der {@link ExtractionRegistry}; Überschriften und Code werden als Markdown
 *       ausgegeben.</li>
 * </ul>
 */
public final class LocalFilesKnowledgeSource implements KnowledgeSourcePort {

    static final String SCHEME = "file";
    /** Standardobergrenze je Datei. */
    public static final long DEFAULT_MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final int PREFIX_BYTES = 8192;
    private static final Set<ContentCategory> INDEXABLE = Collections.unmodifiableSet(EnumSet.of(
            ContentCategory.PLAIN_TEXT, ContentCategory.MARKDOWN, ContentCategory.HTML, ContentCategory.PDF,
            ContentCategory.OFFICE_DOCUMENT, ContentCategory.SOURCE_CODE, ContentCategory.STRUCTURED_DATA));

    private final KnowledgeSourceId sourceId;
    private final Path root;
    private final ContentDetector detector;
    private final ExtractionRegistry extractors;
    private final long maxFileBytes;

    public LocalFilesKnowledgeSource(KnowledgeSourceId sourceId, Path root, ContentDetector detector,
                                     ExtractionRegistry extractors, long maxFileBytes) {
        if (sourceId == null || root == null || detector == null || extractors == null) {
            throw new IllegalArgumentException("sourceId, root, detector and extractors are required");
        }
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes must be >= 1");
        }
        this.sourceId = sourceId;
        this.root = root.toAbsolutePath().normalize();
        this.detector = detector;
        this.extractors = extractors;
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    @Override
    public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
        if (!Files.isDirectory(root)) {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, "Verzeichnis nicht gefunden: " + root);
        }
        Set<Path> files = new LinkedHashSet<Path>();
        for (String startPoint : scope.startPoints()) {
            final Path start = inside(startPoint.trim().isEmpty() ? "." : startPoint.trim());
            if (start == null || !Files.exists(start)) {
                continue;
            }
            if (Files.isRegularFile(start)) {
                files.add(start);
                continue;
            }
            final List<Path> found = new ArrayList<Path>();
            try {
                Files.walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), scope.maxDepth() + 1,
                        new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (attrs.isRegularFile()) {
                                    found.add(file);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                                // Startverzeichnis nicht lesbar: Fehler statt leerer Liste (sonst räumt der Index
                                // alle Dokumente der Quelle ab). Einzelne Unterverzeichnisse und Dateien überspringen.
                                if (file.equals(start)) {
                                    throw exc;
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                throw new KnowledgeSourceException(Kind.UNAVAILABLE, "Verzeichnis nicht lesbar: " + start, e);
            }
            Collections.sort(found);
            files.addAll(found);
        }
        List<KnowledgeResource> resources = new ArrayList<KnowledgeResource>();
        for (Path file : files) {
            if (resources.size() >= scope.maxResources()) {
                break;
            }
            if (!candidate(file)) {
                continue;
            }
            try {
                resources.add(resource(file, typeByName(file)));
            } catch (IOException | RuntimeException e) {
                // Datei verschwunden oder nicht lesbar: überspringen.
            }
        }
        return resources;
    }

    @Override
    public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        Path file = fileOf(resourceId);
        byte[] bytes;
        try {
            if (Files.size(file) > maxFileBytes) {
                throw new KnowledgeSourceException(Kind.UNSUPPORTED, resourceId + " ist größer als " + maxFileBytes + " Bytes");
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, resourceId + " nicht lesbar", e);
        }
        String name = file.getFileName().toString();
        DetectedContentType type = detector.detect(name, null, Arrays.copyOf(bytes, Math.min(bytes.length, PREFIX_BYTES)));
        ResourceExtractor extractor = extractors.findExtractor(type);
        if (extractor == null) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, resourceId + ": kein Extraktor für " + type.mimeType());
        }
        ExtractionResult result = extractor.extract(new ExtractionRequest(resourceId.value(), bytes, name, null, type));
        if (!result.isSuccess()) {
            throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, resourceId + ": " + result.errorMessage());
        }
        KnowledgeResource resource;
        try {
            resource = resource(file, type);
        } catch (IOException e) {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, resourceId + " nicht lesbar", e);
        }
        return KnowledgeDocument.of(resource, text(resource.title(), result.document()));
    }

    @Override
    public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        fileOf(resourceId);
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "LocalFilesKnowledgeSource{" + sourceId + ", " + root + "}";
    }

    private boolean candidate(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith(".") || name.startsWith("~$")) {
            return false;
        }
        try {
            if (Files.isHidden(file) || Files.size(file) > maxFileBytes || Files.size(file) == 0) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return INDEXABLE.contains(typeByName(file).category());
    }

    private DetectedContentType typeByName(Path file) {
        return detector.detect(file.getFileName().toString(), null, null);
    }

    private KnowledgeResource resource(Path file, DetectedContentType type) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        String relative = relative(file);
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("file.path", relative);
        metadata.put("file.size", String.valueOf(attributes.size()));
        return KnowledgeResource.builder(idOf(relative), sourceId)
                .title(file.getFileName().toString())
                .contentType(type.mimeType())
                .revision(KnowledgeRevision.of(attributes.lastModifiedTime().toInstant(),
                        String.valueOf(attributes.size())))
                .scope(sourceId.value())
                .location(file.toUri())
                .metadata(KnowledgeMetadata.of(metadata))
                .build();
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private KnowledgeResourceId idOf(String relative) {
        return KnowledgeResourceId.of(SCHEME, sourceId.value() + "/" + relative);
    }

    private Path fileOf(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        String prefix = SCHEME + ":" + sourceId.value() + "/";
        String value = resourceId.value();
        Path file = value.startsWith(prefix) && value.length() > prefix.length()
                ? inside(value.substring(prefix.length())) : null;
        if (file == null) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, resourceId + " gehört nicht zur Quelle " + sourceId);
        }
        if (!Files.isRegularFile(file)) {
            throw new KnowledgeSourceException(Kind.NOT_FOUND, resourceId + " nicht gefunden");
        }
        return file;
    }

    /** Pfad unterhalb der Wurzel oder {@code null}, wenn er hinausführt. */
    private Path inside(String relative) {
        try {
            Path path = root.resolve(relative).normalize();
            return path.startsWith(root) ? path : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String text(String fileName, ExtractedDocument document) {
        StringBuilder sb = new StringBuilder();
        String title = document.title() != null && !document.title().trim().isEmpty() ? document.title().trim() : fileName;
        sb.append("# ").append(title);
        for (ExtractedBlock block : document.blocks()) {
            String text = block.text();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            sb.append("\n\n");
            switch (block.kind()) {
                case HEADING:
                    sb.append(hashes(block.attributes().get("level"))).append(' ').append(text.trim());
                    break;
                case CODE:
                    String language = block.attributes().get("language");
                    sb.append("```").append(language == null ? "" : language).append('\n').append(text)
                            .append("\n```");
                    break;
                default:
                    sb.append(text.trim());
                    break;
            }
        }
        return sb.toString();
    }

    private static String hashes(String level) {
        int n = 2;
        try {
            n = level == null ? 2 : Math.max(1, Math.min(6, Integer.parseInt(level.trim())));
        } catch (NumberFormatException e) {
            n = 2;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append('#');
        }
        return sb.toString();
    }
}
