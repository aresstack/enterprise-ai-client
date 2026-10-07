package com.aresstack.enterpriseai.source.confluence;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import com.aresstack.enterpriseai.source.confluence.ConfluenceRestClient.Attachment;
import com.aresstack.enterpriseai.source.confluence.ConfluenceRestClient.Page;
import com.aresstack.enterpriseai.source.confluence.ConfluenceRestClient.Session;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
 *   <li>Startpunkte: Seiten-ID ({@code 123456}, {@code page:123456}), Space ({@code DEV}, {@code space:DEV} –
 *       beginnt bei dessen Startseite) oder eine Ressourcen-ID dieser Quelle. {@code discover} folgt per
 *       Breitensuche den Kindseiten bis {@code maxDepth}; textartige Anhänge sind, wenn konfiguriert, Kinder
 *       ihrer Seite.</li>
 *   <li>IDs: {@code confluence:<sourceId>/page/<id>} und {@code confluence:<sourceId>/attachment/<attId>}.</li>
 *   <li>Revision: Versionsnummer und Änderungszeitpunkt aus Confluence.</li>
 *   <li>Inhalt: gerenderte Ansicht ({@code body.view}), in Text mit Markdown-Struktur überführt.</li>
 *   <li>Suche: CQL {@code type=page AND text ~ "..."} über {@code /rest/api/content/search} (wie MainframeMate),
 *       optional auf konfigurierte Spaces beschränkt; dieser Endpunkt liefert keine Textausschnitte, Treffer
 *       haben deshalb einen leeren Snippet.</li>
 * </ul>
 *
 * <p>Anmeldedaten löst der Adapter je Port-Aufruf genau einmal über den {@link SecretProvider} auf (nicht je
 * HTTP-Request, weil z. B. KeePassRPC dafür eine Verbindung aufbaut) und vergisst sie mit dem Ende des Aufrufs.
 * Ohne {@link ConfluenceConfig#credentialRef()} fragt er keine an (z. B. reine mTLS-Anmeldung).
 */
public final class ConfluenceKnowledgeSource implements SearchableKnowledgeSource {

    static final String PAGE_CONTENT_TYPE = "text/html";

    private final KnowledgeSourceId sourceId;
    private final ConfluenceConfig config;
    private final ConfluenceRestClient client;
    private final SecretProvider secrets;

    /**
     * @param transport z. B. {@link UrlConnectionConfluenceTransport} mit Proxy/mTLS aus der Composition Root
     * @param secrets   Security-Port; wird nur benutzt, wenn {@link ConfluenceConfig#credentialRef()} gesetzt ist
     */
    public ConfluenceKnowledgeSource(KnowledgeSourceId sourceId, ConfluenceConfig config,
                                     ConfluenceHttpTransport transport, SecretProvider secrets) {
        if (sourceId == null || config == null || transport == null) {
            throw new IllegalArgumentException("sourceId, config und transport sind Pflicht");
        }
        if (config.credentialRef() != null && secrets == null) {
            throw new IllegalArgumentException("credentialRef gesetzt, aber kein SecretProvider");
        }
        this.sourceId = sourceId;
        this.config = config;
        this.client = new ConfluenceRestClient(config, transport);
        this.secrets = secrets;
    }

    @Override
    public KnowledgeSourceId sourceId() {
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
    public List<SourceSearchHit> search(final SourceQuery query) throws KnowledgeSourceException {
        final String cql = cql(query.text(), config.searchSpaceKeys());
        return withSession(new Operation<List<SourceSearchHit>>() {
            @Override
            public List<SourceSearchHit> run(Session session) throws KnowledgeSourceException {
                List<SourceSearchHit> hits = new ArrayList<SourceSearchHit>();
                for (Page page : client.search(session, cql, query.limit())) {
                    hits.add(new SourceSearchHit(ConfluenceIds.page(sourceId, page.id), page.title, ""));
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
            Page start = startPage(session, raw);
            if (start != null) {
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
                ConfluenceIds.Parsed parsed = ConfluenceIds.parse(sourceId, child.id());
                queue.add(new Node(child, parsed.attachment ? null : parsed.contentId, node.depth + 1));
            }
        }
        return new ArrayList<KnowledgeResource>(found.values());
    }

    private Page startPage(Session session, String raw) throws KnowledgeSourceException {
        ConfluenceIds.StartPoint start = ConfluenceIds.startPoint(sourceId, raw);
        if (start == null) {
            return null;
        }
        if (start.spaceKey != null) {
            return client.spaceHomepage(session, start.spaceKey);
        }
        try {
            return client.page(session, start.pageId, false);
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
        return KnowledgeDocument.of(pageResource(page, page.parentId), text);
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
        return KnowledgeResource.builder(ConfluenceIds.page(sourceId, page.id), sourceId)
                .title(page.title)
                .contentType(PAGE_CONTENT_TYPE)
                .revision(KnowledgeRevision.of(page.modifiedAt, page.version))
                .parentId(parentId == null ? null : ConfluenceIds.page(sourceId, parentId))
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
        return KnowledgeResource.builder(ConfluenceIds.attachment(sourceId, attachment.id), sourceId)
                .title(attachment.title)
                .contentType(attachment.mediaType)
                .revision(KnowledgeRevision.of(attachment.modifiedAt, attachment.version))
                .parentId(attachment.containerId == null || !ConfluenceIds.isContentId(attachment.containerId)
                        ? null : ConfluenceIds.page(sourceId, attachment.containerId))
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

    private ConfluenceIds.Parsed parse(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        ConfluenceIds.Parsed parsed = resourceId == null ? null : ConfluenceIds.parse(sourceId, resourceId);
        if (parsed == null) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED,
                    resourceId + " gehört nicht zur Confluence-Quelle " + sourceId);
        }
        return parsed;
    }

    /** CQL für die Volltextsuche; Anführungszeichen und Backslashes im Suchtext werden maskiert. */
    static String cql(String text, List<String> spaceKeys) {
        StringBuilder cql = new StringBuilder("type=page AND text ~ \"").append(escapeCql(text.trim())).append('"');
        if (!spaceKeys.isEmpty()) {
            cql.append(" AND space in (");
            for (int i = 0; i < spaceKeys.size(); i++) {
                cql.append(i == 0 ? "" : ",").append('"').append(escapeCql(spaceKeys.get(i))).append('"');
            }
            cql.append(')');
        }
        return cql.toString();
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
            return secrets.withSecret(ref, material -> operation.run(new Session(ConfluenceAuthorization.header(material))));
        } catch (SecretUnavailableException e) {
            Kind kind = e.reason() == SecretUnavailableException.Reason.NOT_AVAILABLE ? Kind.UNAVAILABLE : Kind.ACCESS_DENIED;
            throw new KnowledgeSourceException(kind,
                    "Anmeldedaten für Confluence-Quelle " + sourceId + " nicht verfügbar (" + e.reason() + ", " + ref + ")");
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
