package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.source.SourceLocators;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.ImageSource;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.ConfluenceRestClient.Attachment;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.ConfluenceRestClient.Page;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.ConfluenceRestClient.Session;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Confluence Data Center als {@link SearchableKnowledgeSource}.
 *
 * <ul>
 *   <li>Ohne Startpunkte ({@code space:*}, Standard wie in MainframeMate): alle Seiten aller Spaces außer den
 *       abgehakten ({@link SourceScope#excludedPartitions()}); die Spaces listet {@link #partitions()}.</li>
 *   <li>Startpunkte (nur noch aus der Datei): Seiten-ID ({@code 123456}, {@code page:123456}), Space ({@code DEV}, {@code space:DEV} –
 *       beginnt bei dessen Startseite) oder eine Ressourcen-ID dieser Quelle. {@code discover} folgt per
 *       Breitensuche den Kindseiten bis {@code maxDepth}; textartige Anhänge sind, wenn konfiguriert, Kinder
 *       ihrer Seite.</li>
 *   <li>IDs: {@code confluence:<locator>/page/<id>} und {@code confluence:<locator>/attachment/<attId>} (Locator = kanonische Basis-URL ohne Schema).</li>
 *   <li>Revision: Versionsnummer und Änderungszeitpunkt aus Confluence.</li>
 *   <li>Inhalt: gerenderte Ansicht ({@code body.view}), in Text mit Markdown-Struktur überführt.</li>
 *   <li>Lese-Ansicht ({@link PageViewSource}): dasselbe {@code body.view} als HTML mit den Bildern an ihrer
 *       Stelle; deren Bytes lädt {@link #loadViewImage} angemeldet von dieser Confluence.</li>
 *   <li>Suche: CQL {@code type=page AND text ~ "..."} über {@code /rest/api/content/search} (wie MainframeMate),
 *       optional auf konfigurierte Spaces beschränkt; dieser Endpunkt liefert keine Textausschnitte, Treffer
 *       haben deshalb einen leeren Snippet.</li>
 * </ul>
 *
 * <p>Anmeldedaten löst der Adapter je Port-Aufruf genau einmal über den {@link SecretProvider} auf (nicht je
 * HTTP-Request, weil z. B. KeePassRPC dafür eine Verbindung aufbaut) und vergisst sie mit dem Ende des Aufrufs.
 * Ohne {@link ConfluenceConfig#credentialRef()} fragt er keine an (z. B. reine mTLS-Anmeldung).
 */
public final class ConfluenceKnowledgeSource implements SearchableKnowledgeSource, ImageSource, PageViewSource {

    static final String PAGE_CONTENT_TYPE = "text/html";

    private final SourceAddress sourceId;
    /** Kanonische Basis-URL ohne Schema, Teil jeder Ressourcen-ID. */
    private final String locator;
    private final ConfluenceConfig config;
    private final ConfluenceRestClient client;
    private final SecretProvider secrets;
    private final VaultEntryOptions options;

    /**
     * @param transport z. B. {@link UrlConnectionConfluenceTransport} mit Proxy/mTLS aus der Composition Root
     * @param secrets   Security-Port; wird nur benutzt, wenn {@link ConfluenceConfig#credentialRef()} gesetzt ist
     */
    public ConfluenceKnowledgeSource(SourceAddress sourceId, ConfluenceConfig config,
                                     ConfluenceHttpTransport transport, SecretProvider secrets) {
        this(sourceId, config, transport, secrets, new VaultEntryOptions(secrets, config == null ? null
                : config.credentialRef()));
    }

    ConfluenceKnowledgeSource(SourceAddress sourceId, ConfluenceConfig config,
                              ConfluenceHttpTransport transport, SecretProvider secrets, VaultEntryOptions options) {
        if (sourceId == null || config == null || transport == null) {
            throw new IllegalArgumentException("sourceId, config und transport sind Pflicht");
        }
        if (config.credentialRef() != null && secrets == null) {
            throw new IllegalArgumentException("credentialRef gesetzt, aber kein SecretProvider");
        }
        this.sourceId = sourceId;
        this.locator = locatorOf(config);
        this.config = config;
        this.client = new ConfluenceRestClient(config, transport);
        this.secrets = secrets;
        this.options = options;
    }

    @Override
    public SourceAddress sourceId() {
        return sourceId;
    }

    @Override
    public List<KnowledgeResource> discover(final SourceScope scope) throws KnowledgeSourceException {
        return withSession(new Operation<List<KnowledgeResource>>() {
            @Override
            public List<KnowledgeResource> run(Session session) throws KnowledgeSourceException {
                return crawl(session, scope);
            }
        });
    }

    @Override
    public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        final ConfluenceIds.Parsed parsed = parse(resourceId);
        return withSession(new Operation<KnowledgeDocument>() {
            @Override
            public KnowledgeDocument run(Session session) throws KnowledgeSourceException {
                return parsed.attachment ? loadAttachment(session, parsed.contentId) : loadPage(session, parsed.contentId);
            }
        });
    }

    @Override
    public List<KnowledgeResource> imagesOf(KnowledgeResourceId page, final int max) throws KnowledgeSourceException {
        final ConfluenceIds.Parsed parsed = parse(page);
        if (parsed.attachment || max <= 0) {
            return new ArrayList<KnowledgeResource>();
        }
        return withSession(new Operation<List<KnowledgeResource>>() {
            @Override
            public List<KnowledgeResource> run(Session session) throws KnowledgeSourceException {
                List<KnowledgeResource> images = new ArrayList<KnowledgeResource>();
                for (Attachment attachment : client.attachments(session, parsed.contentId, max,
                        new java.util.function.Predicate<Attachment>() {
                            @Override
                            public boolean test(Attachment candidate) {
                                return isLoadableImage(candidate);
                            }
                        })) {
                    images.add(attachmentResource(attachment));
                }
                return images;
            }
        });
    }

    @Override
    public byte[] loadImage(KnowledgeResourceId image) throws KnowledgeSourceException {
        final ConfluenceIds.Parsed parsed = parse(image);
        if (!parsed.attachment) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, "Kein Anhang: " + image.value());
        }
        return withSession(new Operation<byte[]>() {
            @Override
            public byte[] run(Session session) throws KnowledgeSourceException {
                Attachment attachment = client.attachment(session, parsed.contentId);
                if (!isLoadableImage(attachment)) {
                    throw new KnowledgeSourceException(Kind.UNSUPPORTED, "Anhang " + parsed.contentId
                            + " ist kein ladbares Bild (" + attachment.mediaType + ")");
                }
                return client.download(session, attachment);
            }
        });
    }

    @Override
    public PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException {
        final ConfluenceIds.Parsed parsed = parse(page);
        if (parsed.attachment) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, "Keine Seite: " + page.value());
        }
        return withSession(new Operation<PageView>() {
            @Override
            public PageView run(Session session) throws KnowledgeSourceException {
                Page loaded = client.page(session, parsed.contentId, true);
                String base = config.baseUrl().toString();
                base = base.endsWith("/") ? base : base + "/";
                ConfluencePageView view = ConfluencePageView.of(loaded.html, base);
                List<PageView.Image> images = new ArrayList<PageView.Image>();
                for (ConfluencePageView.InlineImage image : view.images) {
                    images.add(new PageView.Image(image.src, image.title, image.attachmentId == null ? null
                            : inlineImageResource(image, loaded.id)));
                }
                return new PageView(view.html, base, images);
            }
        });
    }

    /** Wie MainframeMates {@code ByteDownloader} im {@code ConfluenceReaderTab}: jede Adresse unter der Basis-URL. */
    @Override
    public byte[] loadViewImage(String src) throws KnowledgeSourceException {
        String base = config.baseUrl().toString();
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (src == null || !src.startsWith(base + "/")) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, "Bild liegt nicht in dieser Confluence: " + src);
        }
        final String path = src.substring(base.length());
        return withSession(new Operation<byte[]>() {
            @Override
            public byte[] run(Session session) throws KnowledgeSourceException {
                return client.viewImage(session, path);
            }
        });
    }

    private KnowledgeResource inlineImageResource(ConfluencePageView.InlineImage image, String pageId) {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("confluence.contentId", image.attachmentId);
        metadata.put("confluence.type", "attachment");
        String container = ConfluenceIds.isContentId(image.containerId) ? image.containerId : pageId;
        return KnowledgeResource.builder(ConfluenceIds.attachment(locator, image.attachmentId), sourceId)
                .title(image.title)
                .contentType(image.mediaType.isEmpty() ? null : image.mediaType)
                .revision(image.version.isEmpty() ? KnowledgeRevision.unknown()
                        : KnowledgeRevision.version(image.version))
                .parentId(ConfluenceIds.page(locator, container))
                .metadata(KnowledgeMetadata.of(metadata))
                .build();
    }

    /** Bilder: {@code image/*} außer SVG (kein Rasterbild für OCR/Bildverständnis), höchstens so groß wie Anhänge. */
    private boolean isLoadableImage(Attachment attachment) {
        String type = baseType(attachment.mediaType);
        return type.startsWith("image/") && !type.contains("svg")
                && (attachment.size < 0 || attachment.size <= config.maxAttachmentBytes());
    }

    @Override
    public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        final ConfluenceIds.Parsed parsed = parse(resourceId);
        return withSession(new Operation<List<SourceLink>>() {
            @Override
            public List<SourceLink> run(Session session) throws KnowledgeSourceException {
                List<SourceLink> links = new ArrayList<SourceLink>();
                if (parsed.attachment) {
                    client.attachment(session, parsed.contentId);
                    return links;
                }
                client.page(session, parsed.contentId, false);
                for (KnowledgeResource child : childrenOf(session, parsed.contentId, Integer.MAX_VALUE)) {
                    links.add(new SourceLink(child.id(), child.title()));
                }
                return links;
            }
        });
    }

    @Override
    public List<SourcePartition> partitions() throws KnowledgeSourceException {
        return withSession(new Operation<List<SourcePartition>>() {
            @Override
            public List<SourcePartition> run(Session session) throws KnowledgeSourceException {
                List<SourcePartition> partitions = new ArrayList<SourcePartition>();
                for (ConfluenceRestClient.Space space : client.spaces(session, Integer.MAX_VALUE)) {
                    KnowledgeResourceId home = ConfluenceIds.isContentId(space.homepageId)
                            ? ConfluenceIds.page(locator, space.homepageId) : null;
                    partitions.add(new SourcePartition(space.key, space.name, home, "space:" + space.key));
                }
                return partitions;
            }
        });
    }

    @Override
    public List<SourceSearchHit> search(final SourceQuery query) throws KnowledgeSourceException {
        final String cql = cql(query.text(), config.searchSpaceKeys(), query.excludedPartitions());
        return withSession(new Operation<List<SourceSearchHit>>() {
            @Override
            public List<SourceSearchHit> run(Session session) throws KnowledgeSourceException {
                List<SourceSearchHit> hits = new ArrayList<SourceSearchHit>();
                for (Page page : client.search(session, cql, query.limit())) {
                    // Version und URL liefert die Suche mit (expand=version): unveränderte Treffer werden nicht geladen.
                    hits.add(new SourceSearchHit(ConfluenceIds.page(locator, page.id), page.title, page.excerpt,
                            KnowledgeRevision.of(page.modifiedAt, page.version), location(page.webUi)));
                }
                return hits;
            }
        });
    }

    @Override
    public String toString() {
        return "ConfluenceKnowledgeSource[" + sourceId + ", " + config + "]";
    }

    // --- discover -----------------------------------------------------------------------------------------

    private List<KnowledgeResource> crawl(Session session, SourceScope scope) throws KnowledgeSourceException {
        Map<KnowledgeResourceId, KnowledgeResource> found = new LinkedHashMap<KnowledgeResourceId, KnowledgeResource>();
        Deque<Node> queue = new ArrayDeque<Node>();
        for (String raw : scope.startPoints()) {
            if (ConfluenceSourceProvider.ALL_SPACES.equals(raw.trim())) {
                for (String key : spaceKeys(session, scope)) {
                    allPagesOf(session, key, scope.maxResources(), found);
                }
                continue;
            }
            ConfluenceIds.StartPoint point = ConfluenceIds.startPoint(locator, raw);
            if (point == null) {
                continue;
            }
            Page start;
            if (point.spaceKey != null) {
                ConfluenceRestClient.Space space = client.space(session, point.spaceKey);
                if (space == null || scope.excludedPartitions().contains(space.key)) {
                    continue;
                }
                if (!ConfluenceIds.isContentId(space.homepageId)) {
                    // Space ohne Startseite: wie MainframeMate (space="KEY" AND type=page) alle Seiten des Space.
                    allPagesOf(session, space.key, scope.maxResources(), found);
                    continue;
                }
                start = existingPage(session, space.homepageId);
            } else {
                start = existingPage(session, point.pageId);
            }
            if (start != null && !scope.excludedPartitions().contains(start.spaceKey)) {
                queue.add(new Node(pageResource(start, start.parentId), start.id, 0));
            }
        }
        while (!queue.isEmpty() && found.size() < scope.maxResources()) {
            Node node = queue.poll();
            if (found.containsKey(node.resource.id())) {
                continue;
            }
            found.put(node.resource.id(), node.resource);
            int remaining = scope.maxResources() - found.size(); // nur so viele Kinder holen, wie noch Platz ist
            if (node.pageId == null || node.depth >= scope.maxDepth() || remaining <= 0) {
                continue;
            }
            List<KnowledgeResource> children;
            try {
                children = childrenOf(session, node.pageId, remaining);
            } catch (KnowledgeSourceException e) {
                if (e.kind() == Kind.NOT_FOUND) {
                    continue; // Seite zwischenzeitlich gelöscht: überspringen, wie im Port beschrieben
                }
                throw e;
            }
            for (KnowledgeResource child : children) {
                ConfluenceIds.Parsed parsed = ConfluenceIds.parse(locator, child.id());
                queue.add(new Node(child, parsed.attachment ? null : parsed.contentId, node.depth + 1));
            }
        }
        return new ArrayList<KnowledgeResource>(found.values());
    }

    /**
     * Die Spaces für {@code space:*}: die Space-Schlüssel der Quelle oder, ohne sie, alle Spaces (wie MainframeMate),
     * jeweils ohne die abgehakten.
     */
    private List<String> spaceKeys(Session session, SourceScope scope) throws KnowledgeSourceException {
        List<String> keys = new ArrayList<String>();
        if (config.searchSpaceKeys().isEmpty()) {
            for (ConfluenceRestClient.Space space : client.spaces(session, Integer.MAX_VALUE)) {
                keys.add(space.key);
            }
        } else {
            keys.addAll(config.searchSpaceKeys());
        }
        keys.removeAll(scope.excludedPartitions());
        return keys;
    }

    /** Alle Seiten eines Space (samt textartiger Anhänge, wenn konfiguriert), bis {@code max} beisammen sind. */
    private void allPagesOf(Session session, String spaceKey, int max,
                            Map<KnowledgeResourceId, KnowledgeResource> found) throws KnowledgeSourceException {
        if (found.size() >= max) {
            return;
        }
        for (Page page : client.spacePages(session, spaceKey, max - found.size())) {
            if (found.size() >= max) {
                return;
            }
            KnowledgeResource resource = pageResource(page, page.parentId);
            found.put(resource.id(), resource);
            if (config.includeAttachments() && found.size() < max) {
                for (Attachment attachment : client.attachments(session, page.id, max - found.size(),
                        this::isIndexable)) {
                    KnowledgeResource attached = attachmentResource(attachment);
                    found.put(attached.id(), attached);
                }
            }
        }
    }

    private Page existingPage(Session session, String pageId) throws KnowledgeSourceException {
        try {
            return client.page(session, pageId, false);
        } catch (KnowledgeSourceException e) {
            if (e.kind() == Kind.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /** Kindseiten und (wenn konfiguriert) textartige Anhänge einer Seite, in Confluence-Reihenfolge. */
    private List<KnowledgeResource> childrenOf(Session session, String pageId, int max)
            throws KnowledgeSourceException {
        Set<KnowledgeResource> children = new LinkedHashSet<KnowledgeResource>();
        for (Page child : client.children(session, pageId, max)) {
            children.add(pageResource(child, pageId));
        }
        if (config.includeAttachments() && children.size() < max) {
            for (Attachment attachment : client.attachments(session, pageId, max - children.size(), this::isIndexable)) {
                children.add(attachmentResource(attachment));
            }
        }
        return new ArrayList<KnowledgeResource>(children);
    }

    // --- load ---------------------------------------------------------------------------------------------

    private KnowledgeDocument loadPage(Session session, String pageId) throws KnowledgeSourceException {
        Page page = client.page(session, pageId, true);
        String body = ConfluenceHtmlText.toText(page.html);
        String text = body.isEmpty() ? "# " + page.title : "# " + page.title + "\n\n" + body;
        return KnowledgeDocument.of(pageResource(page, page.parentId), text,
                ConfluenceHtmlText.toBlocks(page.title, page.html));
    }

    private KnowledgeDocument loadAttachment(Session session, String attachmentId) throws KnowledgeSourceException {
        Attachment attachment = client.attachment(session, attachmentId);
        if (!isTextLike(attachment.mediaType)) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED,
                    "Anhang " + attachmentId + " hat keinen Textinhalt (" + attachment.mediaType + ")");
        }
        byte[] content = client.download(session, attachment);
        String text = new String(content, charsetOf(attachment.mediaType));
        return KnowledgeDocument.of(attachmentResource(attachment), text);
    }

    // --- Abbildung ----------------------------------------------------------------------------------------

    private KnowledgeResource pageResource(Page page, String parentId) {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("confluence.contentId", page.id);
        metadata.put("confluence.type", "page");
        if (page.spaceKey != null) {
            metadata.put("confluence.space", page.spaceKey);
        }
        if (!page.labels.isEmpty()) {
            metadata.put("confluence.labels", join(page.labels));
        }
        return KnowledgeResource.builder(ConfluenceIds.page(locator, page.id), sourceId)
                .title(page.title)
                .contentType(PAGE_CONTENT_TYPE)
                .revision(KnowledgeRevision.of(page.modifiedAt, page.version))
                .parentId(parentId == null ? null : ConfluenceIds.page(locator, parentId))
                .scope(page.spaceKey == null ? "" : page.spaceKey)
                .location(location(page.webUi))
                .metadata(KnowledgeMetadata.of(metadata))
                .build();
    }

    private KnowledgeResource attachmentResource(Attachment attachment) {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("confluence.contentId", attachment.id);
        metadata.put("confluence.type", "attachment");
        if (attachment.size >= 0) {
            metadata.put("confluence.fileSize", String.valueOf(attachment.size));
        }
        return KnowledgeResource.builder(ConfluenceIds.attachment(locator, attachment.id), sourceId)
                .title(attachment.title)
                .contentType(attachment.mediaType)
                .revision(KnowledgeRevision.of(attachment.modifiedAt, attachment.version))
                .parentId(attachment.containerId == null || !ConfluenceIds.isContentId(attachment.containerId)
                        ? null : ConfluenceIds.page(locator, attachment.containerId))
                .location(location(attachment.webUi))
                .metadata(KnowledgeMetadata.of(metadata))
                .build();
    }

    private boolean isIndexable(Attachment attachment) {
        return isTextLike(attachment.mediaType)
                && (attachment.size < 0 || attachment.size <= config.maxAttachmentBytes());
    }

    static boolean isTextLike(String mediaType) {
        String type = baseType(mediaType);
        return type.startsWith("text/") || "application/json".equals(type) || "application/xml".equals(type)
                || type.endsWith("+json") || type.endsWith("+xml");
    }

    private static Charset charsetOf(String mediaType) {
        for (String part : mediaType.split(";")) {
            String p = part.trim();
            if (p.regionMatches(true, 0, "charset=", 0, 8)) {
                try {
                    return Charset.forName(p.substring(8).replace("\"", "").trim());
                } catch (RuntimeException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String baseType(String mediaType) {
        String type = mediaType == null ? "" : mediaType;
        int semicolon = type.indexOf(';');
        return (semicolon < 0 ? type : type.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    /** Aufrufbarer Ort; nur unterhalb der Basis-URL, nie mit Zugangsdaten. */
    private URI location(String webUi) {
        if (webUi == null || !webUi.startsWith("/") || webUi.startsWith("//")) {
            return null;
        }
        try {
            return URI.create(config.baseUrl() + webUi);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Der kanonische Locator der Basis-URL ({@code cf.intern/confluence}), aus dem die Ressourcen-IDs entstehen. */
    static String locatorOf(ConfluenceConfig config) {
        return SourceLocators.url(config.baseUrl().toString());
    }

    private ConfluenceIds.Parsed parse(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        ConfluenceIds.Parsed parsed = resourceId == null ? null : ConfluenceIds.parse(locator, resourceId);
        if (parsed == null) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED,
                    resourceId + " gehört nicht zur Confluence-Quelle " + sourceId);
        }
        return parsed;
    }

    /** CQL für die Volltextsuche; Anführungszeichen und Backslashes im Suchtext werden maskiert. */
    static String cql(String text, List<String> spaceKeys) {
        return cql(text, spaceKeys, Collections.<String>emptySet());
    }

    /** Wie MainframeMate: abgehakte Spaces fallen über {@code space not in (...)} aus der Suche. */
    static String cql(String text, List<String> spaceKeys, Collection<String> excludedSpaces) {
        StringBuilder cql = new StringBuilder("type=page AND text ~ \"").append(escapeCql(text.trim())).append('"');
        appendSpaces(cql, " AND space in (", spaceKeys);
        appendSpaces(cql, " AND space not in (", excludedSpaces);
        return cql.toString();
    }

    private static void appendSpaces(StringBuilder cql, String clause, Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        cql.append(clause);
        int i = 0;
        for (String key : keys) {
            cql.append(i++ == 0 ? "" : ",").append('"').append(escapeCql(key)).append('"');
        }
        cql.append(')');
    }

    private static String escapeCql(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            sb.append(sb.length() == 0 ? "" : ",").append(value);
        }
        return sb.toString();
    }

    // --- Anmeldung je Vorgang -----------------------------------------------------------------------------

    private <T> T withSession(final Operation<T> operation) throws KnowledgeSourceException {
        final SecretRef ref = config.credentialRef();
        if (ref == null) {
            return operation.run(new Session(null));
        }
        try {
            if (!options.sendsLogin()) {
                return operation.run(new Session(null)); // MM_RequiresLogin=false: nur Zertifikat, wie MainframeMate
            }
            return secrets.withSecret(ref, material -> operation.run(new Session(ConfluenceAuthorization.header(material))));
        } catch (SecretUnavailableException e) {
            Kind kind = e.reason() == SecretUnavailableException.Reason.NOT_AVAILABLE ? Kind.UNAVAILABLE : Kind.ACCESS_DENIED;
            throw new KnowledgeSourceException(kind,
                    "Anmeldedaten für Confluence-Quelle " + sourceId + " nicht verfügbar (" + e.reason() + ", " + ref
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
        }
    }

    private interface Operation<T> {
        T run(Session session) throws KnowledgeSourceException;
    }

    private static final class Node {
        final KnowledgeResource resource;
        final String pageId;
        final int depth;

        Node(KnowledgeResource resource, String pageId, int depth) {
            this.resource = resource;
            this.pageId = pageId;
            this.depth = depth;
        }
    }
}
