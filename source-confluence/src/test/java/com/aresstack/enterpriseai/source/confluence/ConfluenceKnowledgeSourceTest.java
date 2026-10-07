package com.aresstack.enterpriseai.source.confluence;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException.Reason;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfluenceKnowledgeSourceTest {

    private static final KnowledgeSourceId SOURCE = KnowledgeSourceId.of("confluence-dc");

    private final FakeConfluence confluence = new FakeConfluence();
    private final RecordingSecretProvider secrets = new RecordingSecretProvider();
    private ConfluenceConfig.Builder config;

    @Before
    public void setUp() {
        confluence.page("100", "Start", "DEV", "<p>Willkommen</p>").labels("howto", "java");
        confluence.page("101", "Kapitel 1", "DEV", "<h2>Einleitung</h2><p>Eins</p>");
        confluence.page("102", "Kapitel 2", "DEV", "<p>Zwei</p>");
        confluence.page("103", "Unterkapitel", "DEV", "<p>Drei</p>");
        confluence.child("100", "101");
        confluence.child("100", "102");
        confluence.child("101", "103");
        confluence.spaceHomepages.put("DEV", "100");
        confluence.attachment("att900", "100", "notes.txt", "text/plain; charset=UTF-8", "Notizen äöü");
        confluence.attachment("att901", "100", "bild.png", "image/png", "PNG");
        config = ConfluenceConfig.builder(FakeConfluence.BASE).credentialRef(RecordingSecretProvider.REF).pageSize(1);
    }

    private ConfluenceKnowledgeSource source() {
        return new ConfluenceKnowledgeSource(SOURCE, config.build(), confluence, secrets);
    }

    private static KnowledgeResourceId page(String id) {
        return KnowledgeResourceId.of("confluence", "confluence-dc/page/" + id);
    }

    private static KnowledgeResourceId attachment(String id) {
        return KnowledgeResourceId.of("confluence", "confluence-dc/attachment/" + id);
    }

    // --- discover -----------------------------------------------------------------------------------------

    @Test
    public void spaceStartPointBeginsAtTheHomepageAndCrawlsChildrenBreadthFirst() throws Exception {
        List<KnowledgeResource> resources = source().discover(
                SourceScope.builder().startPoint("DEV").maxDepth(2).build());

        assertEquals(Arrays.asList(page("100"), page("101"), page("102"), page("103")), ids(resources));
        assertEquals(page("100"), resources.get(1).parentId().get());
        assertEquals(page("101"), resources.get(3).parentId().get());
    }

    @Test
    public void pageStartPointsInAllForms() throws Exception {
        for (String start : new String[] {"101", "page:101", "confluence:confluence-dc/page/101"}) {
            List<KnowledgeResource> resources = source().discover(SourceScope.of(start));
            assertEquals(start, Collections.singletonList(page("101")), ids(resources));
        }
    }

    @Test
    public void unusableStartPointsAreSkipped() throws Exception {
        assertTrue(source().discover(SourceScope.of("space:NOPE", "999", "kein gültiger ? Startpunkt",
                "confluence:other-source/page/100")).isEmpty());
    }

    @Test
    public void discoveredResourcesCarryRevisionScopeLocationAndNoContent() throws Exception {
        KnowledgeResource start = source().discover(SourceScope.of("100")).get(0);

        assertEquals("Start", start.title());
        assertEquals("DEV", start.scope());
        assertEquals("3", start.revision().version());
        assertEquals(Instant.parse("2026-10-01T06:15:00Z"), start.revision().modifiedAt().get());
        assertEquals(URI.create("https://confluence.example.org/wiki/pages/viewpage.action?pageId=100"),
                start.location().get());
        assertEquals("100", start.metadata().get("confluence.contentId").get());
        assertEquals(0, confluence.requestCount("body.view"));
    }

    @Test
    public void textAttachmentsAreChildrenOnlyWhenEnabled() throws Exception {
        SourceScope scope = SourceScope.builder().startPoint("100").maxDepth(1).build();
        assertFalse(ids(source().discover(scope)).contains(attachment("att900")));

        config.includeAttachments(true);
        List<KnowledgeResource> resources = source().discover(scope);
        assertEquals(Arrays.asList(page("100"), page("101"), page("102"), attachment("att900")), ids(resources));
        KnowledgeResource notes = resources.get(3);
        assertEquals("notes.txt", notes.title());
        assertEquals(page("100"), notes.parentId().get());
        assertEquals("text/plain; charset=UTF-8", notes.contentType());
    }

    @Test
    public void oversizedAttachmentsAreNotDiscovered() throws Exception {
        config.includeAttachments(true).maxAttachmentBytes(3);
        assertFalse(ids(source().discover(SourceScope.builder().startPoint("100").maxDepth(1).build()))
                .contains(attachment("att900")));
    }

    @Test
    public void maxResourcesStopsCrawlingAndPaging() throws Exception {
        List<KnowledgeResource> resources = source().discover(
                SourceScope.builder().startPoint("100").maxDepth(5).maxResources(2).build());
        assertEquals(Arrays.asList(page("100"), page("101")), ids(resources));
    }

    @Test
    public void pagingFollowsNextLinks() throws Exception {
        for (int i = 0; i < 5; i++) {
            confluence.page("20" + i, "Seite " + i, "DEV", "<p>x</p>");
            confluence.child("102", "20" + i);
        }
        List<SourceLink> links = source().discoverLinks(page("102"));
        assertEquals(5, links.size());
        assertEquals(5, confluence.requestCount("/child/page"));
    }

    // --- load ---------------------------------------------------------------------------------------------

    @Test
    public void loadConvertsTheViewToStructuredText() throws Exception {
        KnowledgeDocument document = source().load(page("101"));

        assertEquals("# Kapitel 1\n\n## Einleitung\n\nEins", document.text());
        assertEquals(page("100"), document.resource().parentId().get());
    }

    @Test
    public void loadReportsLabelsAsMetadata() throws Exception {
        assertEquals("howto,java", source().load(page("100")).resource().metadata().get("confluence.labels").get());
    }

    @Test
    public void loadDownloadsTextAttachments() throws Exception {
        KnowledgeDocument document = source().load(attachment("att900"));
        assertEquals("Notizen äöü", document.text());
        assertEquals("1", document.resource().revision().version());
    }

    @Test
    public void binaryAttachmentsAreUnsupported() {
        assertKind(Kind.UNSUPPORTED, () -> source().load(attachment("att901")));
        assertEquals(0, confluence.requestCount("/download/"));
    }

    @Test
    public void downloadLinksOutsideTheBaseAreRejected() {
        confluence.attachments.get("att900").downloadOverride = "//evil.example.org/steal";
        assertKind(Kind.INVALID_RESPONSE, () -> source().load(attachment("att900")));
        for (URI uri : confluence.requests) {
            assertEquals("confluence.example.org", uri.getHost());
        }
    }

    @Test
    public void attachmentIdIsNotAPage() {
        assertKind(Kind.NOT_FOUND, () -> source().load(page("900")));
        assertKind(Kind.UNSUPPORTED, () -> source().load(KnowledgeResourceId.of("confluence", "confluence-dc/page/att900")));
    }

    @Test
    public void foreignAndMalformedIdsAreUnsupported() {
        assertKind(Kind.UNSUPPORTED, () -> source().load(KnowledgeResourceId.of("confluence", "other/page/100")));
        assertKind(Kind.UNSUPPORTED, () -> source().load(KnowledgeResourceId.of("wiki", "confluence-dc/page/100")));
        assertKind(Kind.UNSUPPORTED, () -> source().discoverLinks(KnowledgeResourceId.of("confluence", "confluence-dc/blog/1")));
        assertTrue(confluence.requests.isEmpty());
    }

    // --- Links und Suche ----------------------------------------------------------------------------------

    @Test
    public void linksOfAnAttachmentAreEmptyButCheckedForExistence() throws Exception {
        assertTrue(source().discoverLinks(attachment("att900")).isEmpty());
        assertKind(Kind.NOT_FOUND, () -> source().discoverLinks(attachment("att1")));
    }

    @Test
    public void searchSendsEscapedCqlRestrictedToConfiguredSpaces() throws Exception {
        confluence.searchResults.addAll(Arrays.asList("101", "102", "103"));
        config.searchSpaceKey("DEV").searchSpaceKey("OPS");

        List<SourceSearchHit> hits = source().search(new SourceQuery("say \"hi\" \\ ok", 2));

        assertEquals("type=page AND text ~ \"say \\\"hi\\\" \\\\ ok\" AND space in (\"DEV\",\"OPS\")", confluence.lastCql);
        assertEquals(2, hits.size());
        assertEquals(page("101"), hits.get(0).resourceId());
        assertEquals("Kapitel 1", hits.get(0).title());
    }

    // --- Anmeldung ----------------------------------------------------------------------------------------

    @Test
    public void basicAuthIsSentAndTheSecretIsResolvedOncePerOperationAndWiped() throws Exception {
        source().discover(SourceScope.builder().startPoint("DEV").maxDepth(3).build());

        assertEquals(1, secrets.issued.size());
        assertTrue(secrets.issued.get(0).isClosed());
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "alice:s3cr3t-Pässwort".getBytes(StandardCharsets.UTF_8));
        assertTrue(confluence.requests.size() > 3);
        for (Map<String, String> headers : confluence.requestHeaders) {
            assertEquals(expected, headers.get("Authorization"));
            assertEquals("application/json", headers.get("Accept"));
        }
    }

    @Test
    public void tokenWithoutPrincipalIsSentAsBearer() throws Exception {
        secrets.principal = null;
        secrets.secret = "pat-token";
        source().load(page("100"));
        assertEquals("Bearer pat-token", confluence.requestHeaders.get(0).get("Authorization"));
    }

    @Test
    public void withoutCredentialRefNoSecretIsRequestedAndNoAuthorizationSent() throws Exception {
        ConfluenceKnowledgeSource anonymous = new ConfluenceKnowledgeSource(SOURCE,
                ConfluenceConfig.builder(FakeConfluence.BASE).build(), confluence, null);
        anonymous.load(page("100"));
        assertTrue(secrets.issued.isEmpty());
        assertFalse(confluence.requestHeaders.get(0).containsKey("Authorization"));
    }

    @Test
    public void unavailableSecretsMapToPortKindsWithoutTouchingConfluence() {
        Object[][] cases = {
                {Reason.NOT_AVAILABLE, Kind.UNAVAILABLE},
                {Reason.NOT_FOUND, Kind.ACCESS_DENIED},
                {Reason.ACCESS_DENIED, Kind.ACCESS_DENIED},
                {Reason.CANCELLED, Kind.ACCESS_DENIED},
        };
        for (Object[] c : cases) {
            secrets.failWith = (Reason) c[0];
            KnowledgeSourceException e = assertKind((Kind) c[1], () -> source().load(page("100")));
            assertTrue(e.getMessage(), e.getMessage().contains("keepass:confluence"));
        }
        assertTrue(confluence.requests.isEmpty());
    }

    @Test
    public void secretIsWipedWhenConfluenceFails() {
        confluence.forcedResponse = new ConfluenceHttpResponse(500, "text/html", new byte[0]);
        assertKind(Kind.UNAVAILABLE, () -> source().load(page("100")));
        assertTrue(secrets.issued.get(0).isClosed());
    }

    // --- HTTP-Fehler --------------------------------------------------------------------------------------

    @Test
    public void httpStatusesMapToPortKinds() {
        Object[][] cases = {
                {401, Kind.ACCESS_DENIED}, {403, Kind.ACCESS_DENIED}, {302, Kind.ACCESS_DENIED},
                {404, Kind.NOT_FOUND}, {429, Kind.UNAVAILABLE}, {503, Kind.UNAVAILABLE}, {400, Kind.INVALID_RESPONSE},
        };
        for (Object[] c : cases) {
            confluence.forcedResponse = new ConfluenceHttpResponse((Integer) c[0], "application/json",
                    "{\"message\":\"geheime Details alice:s3cr3t\"}".getBytes(StandardCharsets.UTF_8));
            KnowledgeSourceException e = assertKind((Kind) c[1], () -> source().load(page("100")));
            assertFalse(e.getMessage(), e.getMessage().contains("geheime"));
        }
    }

    @Test
    public void networkFailuresAreUnavailable() {
        confluence.forcedFailure = new IOException("Connection refused");
        assertKind(Kind.UNAVAILABLE, () -> source().discover(SourceScope.of("100")));
    }

    @Test
    public void htmlLoginPagesAndBrokenJsonAreInvalidResponses() {
        confluence.forcedResponse = new ConfluenceHttpResponse(200, "text/html", "<html>Login</html>".getBytes(StandardCharsets.UTF_8));
        assertKind(Kind.INVALID_RESPONSE, () -> source().load(page("100")));
        confluence.forcedResponse = new ConfluenceHttpResponse(200, "application/json", "{kaputt".getBytes(StandardCharsets.UTF_8));
        assertKind(Kind.INVALID_RESPONSE, () -> source().load(page("100")));
        confluence.forcedResponse = new ConfluenceHttpResponse(200, "application/json", "[]".getBytes(StandardCharsets.UTF_8));
        assertKind(Kind.INVALID_RESPONSE, () -> source().load(page("100")));
    }

    @Test
    public void accessDeniedAbortsDiscovery() {
        confluence.forcedResponse = new ConfluenceHttpResponse(401, "application/json", new byte[0]);
        assertKind(Kind.ACCESS_DENIED, () -> source().discover(SourceScope.of("DEV")));
    }

    // --- Diverses -----------------------------------------------------------------------------------------

    @Test
    public void toStringShowsNoSecrets() {
        String text = source().toString();
        assertTrue(text, text.contains("keepass:confluence"));
        assertFalse(text, text.contains("s3cr3t"));
        assertFalse(text, text.contains("alice"));
    }

    @Test
    public void constructorRequiresSecretProviderWhenCredentialsAreConfigured() {
        try {
            new ConfluenceKnowledgeSource(SOURCE, config.build(), confluence, null);
            fail();
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    @Test
    public void cqlWithoutSpaces() {
        assertEquals("type=page AND text ~ \"java\"", ConfluenceKnowledgeSource.cql(" java ", new ArrayList<String>()));
    }

    @Test
    public void textLikeMediaTypes() {
        for (String type : new String[] {"text/plain", "text/csv; charset=ISO-8859-1", "application/json",
                "application/xml", "application/vnd.api+json", "image/svg+xml"}) {
            assertTrue(type, ConfluenceKnowledgeSource.isTextLike(type));
        }
        for (String type : new String[] {"application/pdf", "image/png", "application/octet-stream", ""}) {
            assertFalse(type, ConfluenceKnowledgeSource.isTextLike(type));
        }
    }

    private static List<KnowledgeResourceId> ids(List<KnowledgeResource> resources) {
        List<KnowledgeResourceId> ids = new ArrayList<KnowledgeResourceId>();
        for (KnowledgeResource resource : resources) {
            ids.add(resource.id());
        }
        return ids;
    }

    private static KnowledgeSourceException assertKind(Kind kind, Call call) {
        try {
            call.run();
            fail("erwartet " + kind);
            return null;
        } catch (KnowledgeSourceException e) {
            assertEquals(e.getMessage(), kind, e.kind());
            return e;
        }
    }

    private interface Call {
        void run() throws KnowledgeSourceException;
    }
}
