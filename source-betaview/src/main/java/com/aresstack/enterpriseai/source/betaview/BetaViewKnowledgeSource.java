package com.aresstack.enterpriseai.source.betaview;

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
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dokumente (Listen, Reports) eines BetaView-Servers als {@link KnowledgeSourcePort}.
 *
 * <ul>
 *   <li>Suche wie in MainframeMate ({@code LoadResultsHtmlUseCase}): Favorit, Zeitraum, Formular, Endung und Report
 *       aus der Konfiguration; die Startpunkte sind Jobnamen-Filter ({@code *}, {@code PAY*}).</li>
 *   <li>IDs: {@code betaview:<Quell-ID>/<Zeilenschlüssel>}; der Schlüssel ist ein Hash der Zellen der Trefferzeile,
 *       weil BetaView Dokumente in der Trefferliste nur über ihre Position öffnet. Revision = Schlüssel.</li>
 *   <li>Laden: Suche erneut ausführen, Zeile über den Schlüssel finden, öffnen, wie MainframeMate
 *       ({@code DownloadDocumentUseCase}) alle Seiten im Originalformat herunterladen, den Reiter am Server wieder
 *       schließen und über {@code document-api} in Text überführen.</li>
 *   <li>Eine angemeldete Sitzung je Quelle; Zugangsdaten nur beim (Wieder-)Anmelden aus KeePass.</li>
 * </ul>
 */
final class BetaViewKnowledgeSource implements KnowledgeSourcePort {

    static final String SCHEME = "betaview";

    private static final int PREFIX_BYTES = 8192;
    private static final int KEY_LENGTH = 24;

    /** Vorlage der Suche; Jobname kommt je Startpunkt hinzu. */
    static final class Search {
        final String favoriteId;
        final String locale;
        final int daysBack;
        final String form;
        final String extension;
        final String report;

        Search(String favoriteId, String locale, int daysBack, String form, String extension, String report) {
            this.favoriteId = favoriteId;
            this.locale = locale;
            this.daysBack = daysBack;
            this.form = form;
            this.extension = extension;
            this.report = report;
        }

        ResultFilter filter(String jobName) {
            return new ResultFilter.Builder()
                    .favoriteId(favoriteId)
                    .locale(locale)
                    .lastsel("individual")
                    .timeunit("days")
                    .daysBack(daysBack)
                    .form(form)
                    .extensionPattern(extension)
                    .report(report)
                    .jobName(jobName)
                    .build();
        }
    }

    private interface Operation<T> {
        T run(BetaViewSession session) throws IOException, KnowledgeSourceException;
    }

    private final KnowledgeSourceId sourceId;
    private final BetaViewClient client;
    private final SecretRef credentialRef;
    private final SecretProvider secrets;
    private final Search search;
    private final ContentDetector detector;
    private final ExtractionRegistry extractors;
    private final long maxFileBytes;
    /** Jobnamen-Filter, unter dem ein Schlüssel zuletzt gefunden wurde (für das erneute Suchen beim Laden). */
    private final Map<String, String> filterOfKey = new HashMap<String, String>();
    private BetaViewSession session;

    BetaViewKnowledgeSource(KnowledgeSourceId sourceId, BetaViewClient client, SecretRef credentialRef,
                            SecretProvider secrets, Search search, ContentDetector detector,
                            ExtractionRegistry extractors, long maxFileBytes) {
        if (sourceId == null || client == null || credentialRef == null || secrets == null || search == null
                || detector == null || extractors == null) {
            throw new IllegalArgumentException("all arguments are required");
        }
        this.sourceId = sourceId;
        this.client = client;
        this.credentialRef = credentialRef;
        this.secrets = secrets;
        this.search = search;
        this.detector = detector;
        this.extractors = extractors;
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public KnowledgeSourceId sourceId() {
        return sourceId;
    }

    @Override
    public List<KnowledgeResource> discover(final SourceScope scope) throws KnowledgeSourceException {
        return run(s -> {
            Map<String, KnowledgeResource> found = new LinkedHashMap<String, KnowledgeResource>();
            for (String startPoint : scope.startPoints()) {
                String jobName = jobName(startPoint);
                for (ResultTable.Row row : results(s, jobName)) {
                    if (found.size() >= scope.maxResources()) {
                        return new ArrayList<KnowledgeResource>(found.values());
                    }
                    String key = keyOf(row);
                    if (!found.containsKey(key)) {
                        found.put(key, resource(key, row, null));
                        filterOfKey.put(key, jobName);
                    }
                }
            }
            return new ArrayList<KnowledgeResource>(found.values());
        });
    }

    @Override
    public KnowledgeDocument load(final KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        final String key = keyOf(resourceId);
        return run(s -> {
            String jobName = filterOfKey.containsKey(key) ? filterOfKey.get(key) : "*";
            ResultTable.Row row = null;
            for (ResultTable.Row candidate : results(s, jobName)) {
                if (keyOf(candidate).equals(key)) {
                    row = candidate;
                    break;
                }
            }
            if (row == null) {
                throw new KnowledgeSourceException(Kind.NOT_FOUND, resourceId + " nicht mehr in der Trefferliste");
            }
            String opened = client.getText(s, row.action);
            DownloadResult download;
            try {
                download = new DownloadDocumentUseCase(client).execute(s, DownloadDocumentUseCase.PageSelection.ALL_PAGES);
            } finally {
                closeTabs(s, opened);
            }
            byte[] bytes = download.data();
            if (bytes.length > maxFileBytes) {
                throw new KnowledgeSourceException(Kind.UNSUPPORTED, resourceId + " ist größer als " + maxFileBytes + " Bytes");
            }
            String name = key + (download.isPdf() ? ".pdf" : ".txt");
            String contentType = download.contentType().isEmpty() ? null : download.contentType();
            DetectedContentType type = detector.detect(name, contentType,
                    Arrays.copyOf(bytes, Math.min(bytes.length, PREFIX_BYTES)));
            ResourceExtractor extractor = extractors.findExtractor(type);
            if (extractor == null) {
                throw new KnowledgeSourceException(Kind.UNSUPPORTED, resourceId + ": kein Extraktor für " + type.mimeType());
            }
            ExtractionResult result = extractor.extract(new ExtractionRequest(resourceId.value(), bytes, name,
                    contentType, type));
            if (!result.isSuccess()) {
                throw new KnowledgeSourceException(Kind.INVALID_RESPONSE, resourceId + ": " + result.errorMessage());
            }
            KnowledgeResource resource = resource(key, row, type.mimeType());
            return KnowledgeDocument.of(resource, text(resource.title(), result.document()));
        });
    }

    @Override
    public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        keyOf(resourceId);
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "BetaViewKnowledgeSource{" + sourceId + "}";
    }

    // ── Sitzung ───────────────────────────────────────────────────────────────────────────────────────────────

    private synchronized <T> T run(Operation<T> operation) throws KnowledgeSourceException {
        for (int attempt = 0; ; attempt++) {
            if (session == null) {
                session = login();
            }
            try {
                return operation.run(session);
            } catch (IOException e) {
                // Sitzung abgelaufen oder Verbindung weg: einmal neu anmelden.
                session = null;
                if (attempt > 0) {
                    throw new KnowledgeSourceException(Kind.UNAVAILABLE,
                            "BetaView-Quelle " + sourceId + ": " + e.getMessage(), e);
                }
            }
        }
    }

    private BetaViewSession login() throws KnowledgeSourceException {
        try {
            return secrets.withSecret(credentialRef, (SecretMaterial material) -> {
                char[] password = material.copySecret();
                try {
                    return client.login(material.principal(), password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            });
        } catch (SecretUnavailableException e) {
            Kind kind = e.reason() == SecretUnavailableException.Reason.NOT_AVAILABLE ? Kind.UNAVAILABLE : Kind.ACCESS_DENIED;
            throw new KnowledgeSourceException(kind, "Anmeldedaten für BetaView-Quelle " + sourceId
                    + " nicht verfügbar (" + e.reason() + ", " + credentialRef + ")");
        } catch (BetaViewHttpClient.BetaViewLoginException e) {
            throw new KnowledgeSourceException(Kind.ACCESS_DENIED, "BetaView-Quelle " + sourceId + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE,
                    "BetaView-Quelle " + sourceId + " nicht erreichbar: " + e.getMessage(), e);
        }
    }

    private List<ResultTable.Row> results(BetaViewSession s, String jobName) throws IOException {
        return ResultTable.parse(new LoadResultsHtmlUseCase(client).execute(s, search.filter(jobName)));
    }

    /** Geöffnete Dokument-Reiter am Server schließen (wie {@code BetaViewDocumentTab.onClose}). */
    private void closeTabs(BetaViewSession s, String openedHtml) {
        for (DocumentTab tab : DocumentTabParser.parse(openedHtml)) {
            if (tab.isActive() && tab.linkID() != null && !tab.linkID().isEmpty()) {
                try {
                    client.getText(s, "closeSingleDocument.action?linkID=" + tab.linkID());
                } catch (IOException e) {
                    // Reiter bleibt offen; die Sitzung räumt ihn beim Abmelden ab.
                }
            }
        }
    }

    // ── Ressourcen ────────────────────────────────────────────────────────────────────────────────────────────

    private KnowledgeResource resource(String key, ResultTable.Row row, String contentType) {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        StringBuilder title = new StringBuilder();
        for (Map.Entry<String, String> cell : row.cells.entrySet()) {
            if (cell.getValue().isEmpty()) {
                continue;
            }
            if (!cell.getKey().isEmpty()) {
                metadata.put("betaview." + cell.getKey(), cell.getValue());
            }
            if (title.length() < 80) {
                title.append(title.length() == 0 ? "" : " · ").append(cell.getValue());
            }
        }
        return KnowledgeResource.builder(KnowledgeResourceId.of(SCHEME, sourceId.value() + "/" + key), sourceId)
                .title(title.length() == 0 ? key : title.toString())
                .contentType(contentType == null ? KnowledgeResource.DEFAULT_CONTENT_TYPE : contentType)
                .revision(KnowledgeRevision.version(key))
                .scope(sourceId.value())
                .metadata(KnowledgeMetadata.of(metadata))
                .build();
    }

    private static String jobName(String startPoint) {
        String trimmed = startPoint == null ? "" : startPoint.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? "*" : trimmed;
    }

    static String keyOf(ResultTable.Row row) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> cell : row.cells.entrySet()) {
            sb.append(cell.getKey()).append('\u001f').append(cell.getValue()).append('\u001e');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.substring(0, KEY_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }

    private String keyOf(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
        String prefix = SCHEME + ":" + sourceId.value() + "/";
        String value = resourceId == null ? "" : resourceId.value();
        String key = value.startsWith(prefix) ? value.substring(prefix.length()) : "";
        boolean hex = key.length() == KEY_LENGTH;
        for (int i = 0; hex && i < key.length(); i++) {
            char c = key.charAt(i);
            hex = c >= '0' && c <= '9' || c >= 'a' && c <= 'f';
        }
        if (!hex) {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, resourceId + " gehört nicht zur Quelle " + sourceId);
        }
        return key;
    }

    /** Text wie die lokale Dateiquelle: Überschriften und Code als Markdown. */
    static String text(String fallbackTitle, ExtractedDocument document) {
        StringBuilder sb = new StringBuilder();
        String title = document.title() != null && !document.title().trim().isEmpty() ? document.title().trim()
                : fallbackTitle;
        sb.append("# ").append(title);
        for (ExtractedBlock block : document.blocks()) {
            String text = block.text();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            sb.append("\n\n");
            switch (block.kind()) {
                case HEADING:
                    sb.append("## ").append(text.trim());
                    break;
                case CODE:
                    sb.append("```\n").append(text).append("\n```");
                    break;
                default:
                    sb.append(text.trim());
                    break;
            }
        }
        return sb.toString();
    }
}
