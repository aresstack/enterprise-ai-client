package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.OneTimeCodeChallenge;
import com.aresstack.enterpriseai.source.api.OneTimeCodeRequiredException;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SourceFile;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Dateianhänge als Wissensdokument ({@link SourceAttachmentReader}) und der Einmalcode beim Lesen auf Anforderung
 * ({@link UnlockingDocumentReader}), mit einer Fake-Quelle, die erst nach dem Code Dateien liefert.
 */
public class SourceAttachmentReaderTest {

    private static final SourceAddress SOURCE = SourceAddress.of("wiki-mit-dateien");
    private static final KnowledgeResourceId PAGE = KnowledgeResourceId.of("fake:host/page/7");
    private static final KnowledgeResourceId PDF = KnowledgeResourceId.of("fake:host/attachment/att1");

    private final FileSource source = new FileSource();
    private final KnowledgeDocumentReader direct = KnowledgeDocumentReader.direct();

    @Test
    public void anAttachmentIsReadAsFileAndKeepsTheOriginOfTheSource() throws Exception {
        source.locked = false;
        KnowledgeDocument document = new SourceAttachmentReader(direct, (file, content) ->
                "Text aus " + file.title() + ": " + new String(content, StandardCharsets.UTF_8)).read(SOURCE, source,
                PDF);

        assertEquals(PDF, document.id());
        assertEquals(PAGE, document.resource().parentId().get());
        assertEquals("application/pdf", document.resource().contentType());
        assertEquals("# Handbuch.pdf\n\nText aus Handbuch.pdf: %PDF", document.text());
    }

    @Test
    public void pagesStillGoThroughTheTextPath() throws Exception {
        KnowledgeDocument document = new SourceAttachmentReader(direct, (file, content) -> {
            throw new AssertionError("kein Dateiweg für Seiten");
        }).read(SOURCE, source, PAGE);
        assertEquals("# Seite", document.text());
    }

    @Test
    public void theCodeIsAskedOnceAndTheReadIsRepeated() throws Exception {
        final AtomicInteger asked = new AtomicInteger();
        KnowledgeDocumentReader reader = new UnlockingDocumentReader(
                new SourceAttachmentReader(direct, (file, content) -> "inhalt"), () -> (owner, challenge) -> {
                    asked.incrementAndGet();
                    assertSame(SOURCE, owner);
                    try {
                        assertEquals(null, challenge.submit("123456".toCharArray()));
                    } catch (KnowledgeSourceException e) {
                        throw new AssertionError(e);
                    }
                    return OneTimeCodePrompt.Outcome.UNLOCKED;
                });

        assertEquals("# Handbuch.pdf\n\ninhalt", reader.read(SOURCE, source, PDF).text());
        assertEquals("# Handbuch.pdf\n\ninhalt", reader.read(SOURCE, source, PDF).text());
        assertEquals(1, asked.get());
        assertEquals(Collections.singletonList("123456"), source.codes);
    }

    @Test
    public void aDeclinedCodeIsAnAccessDenial() throws Exception {
        KnowledgeDocumentReader reader = new UnlockingDocumentReader(
                new SourceAttachmentReader(direct, (file, content) -> "inhalt"),
                () -> OneTimeCodePrompt.none());
        try {
            reader.read(SOURCE, source, PDF);
            fail("ohne Code kein Inhalt");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.ACCESS_DENIED, e.kind());
            assertTrue(e.getCause() instanceof OneTimeCodeRequiredException);
        }
        assertTrue(source.codes.isEmpty());
    }

    @Test
    public void attachmentsAreListedThroughTheCatalog() throws Exception {
        List<KnowledgeResource> attachments = new ListKnowledgeAttachmentsUseCase(() -> KnowledgeSourceCatalog.of(
                new KnowledgeSourceRegistration(source, SourceScope.of("7")))).attachmentsOf(PAGE);
        assertEquals(1, attachments.size());
        assertEquals(PDF, attachments.get(0).id());
    }

    /** Eine Quelle mit einer Seite und einem PDF-Anhang, der erst nach dem Einmalcode lädt. */
    private static final class FileSource implements KnowledgeSourcePort, PageViewSource {
        boolean locked = true;
        final List<String> codes = new ArrayList<String>();

        @Override
        public SourceAddress sourceId() {
            return SOURCE;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            if (!PAGE.equals(resourceId)) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "kein Text");
            }
            return KnowledgeDocument.of(KnowledgeResource.builder(PAGE, SOURCE).title("Seite").build(), "# Seite");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }

        @Override
        public PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "keine Ansicht");
        }

        @Override
        public byte[] loadViewImage(String src) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "keine Bilder");
        }

        @Override
        public List<KnowledgeResource> attachmentsOf(KnowledgeResourceId page) {
            return Collections.singletonList(pdf());
        }

        @Override
        public boolean isAttachment(KnowledgeResourceId id) {
            return PDF.equals(id);
        }

        @Override
        public SourceFile loadAttachmentFile(KnowledgeResourceId attachment) throws KnowledgeSourceException {
            if (locked) {
                throw new OneTimeCodeRequiredException("Einmalcode nötig", new Challenge(), null);
            }
            return new SourceFile(pdf(), "%PDF".getBytes(StandardCharsets.UTF_8));
        }

        private KnowledgeResource pdf() {
            return KnowledgeResource.builder(PDF, SOURCE).title("Handbuch.pdf").contentType("application/pdf")
                    .parentId(PAGE).metadata(KnowledgeMetadata.empty().with(KnowledgeMetadata.FILE_SIZE, "4"))
                    .build();
        }

        private final class Challenge implements OneTimeCodeChallenge {
            @Override
            public String service() {
                return "host";
            }

            @Override
            public String title() {
                return "";
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public String error() {
                return "";
            }

            @Override
            public boolean resendAvailable() {
                return false;
            }

            @Override
            public OneTimeCodeChallenge submit(char[] code) {
                codes.add(new String(code));
                locked = false;
                return null;
            }

            @Override
            public OneTimeCodeChallenge resend() {
                return this;
            }
        }
    }
}
