package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchKnowledgeSourcesUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.KnowledgeReadingController;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.paint.CountBadgeIcon;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Angelos Abnahme mit einer Fake-Quelle über den echten Controller: Seite öffnen → Büroklammer mit Anzahl →
 * Liste → PDF anklicken → Overlay mit Seiten, Blättern, Zoom, Speichern und Öffnen. Dazu: ein unbekannter Dateityp
 * bleibt in der Liste und öffnet das Overlay mit Name, Typ, Größe, Speichern und Öffnen; Text, Bild und Office
 * (Vorschau der Quelle) zeigen ihren Inhalt; eine Seite ohne Anhänge blendet die Büroklammer aus. Dateinamen erfunden.
 */
public class ReaderAttachmentsTest {

    private static final SourceAddress SOURCE = SourceAddress.of("wiki-test");
    private static final String PAGE = "confluence:wiki.invalid/page/1";
    private static final String EMPTY_PAGE = "confluence:wiki.invalid/page/2";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private final List<KnowledgePrefetchService> services = new ArrayList<KnowledgePrefetchService>();

    @After
    public void tearDown() {
        for (KnowledgePrefetchService service : services) {
            service.shutdown();
        }
    }

    @Test
    public void openingAPdfAttachmentShowsItsPagesWithZoomSaveAndOpen() throws Exception {
        final byte[] pdf = pdf(3);
        final AttachmentPage source = new AttachmentPage(pdf);
        final List<File> opened = new ArrayList<File>();
        final File target = temp.newFile("kopie.pdf");
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReaderPanel reader = workspace.reader();
            reader.setAttachmentRenderer(Runnable::run);
            reader.setFileOpener(opened::add);
            controller(workspace, source).open(new ReaderLink(SOURCE.value(), PAGE, "Seite", "", ""), true);

            // Büroklammer mit Anzahl
            assertTrue("Büroklammer sichtbar", reader.attachmentsButton().isVisible());
            assertEquals(5, ((CountBadgeIcon) reader.attachmentsButton().getIcon()).count());
            assertEquals("5 Anhänge", reader.attachmentsButton().getToolTipText());

            // Liste
            JPopupMenu menu = reader.attachmentMenu();
            List<ReaderAttachmentList.Row> rows = ReaderAttachmentList.rows(menu);
            assertEquals(5, rows.size());
            assertEquals("Ablauf Beispiel.pdf", rows.get(0).nameText());
            assertEquals("PDF · 3 KB", rows.get(0).detailsText());
            assertEquals("Planung.xlsx", rows.get(1).nameText());
            assertEquals("Excel · 82 KB", rows.get(1).detailsText());
            assertEquals("daten.bin", rows.get(4).nameText());

            // PDF anklicken → Overlay mit Seiten
            rows.get(0).activate();
            ImageSlideshowPanel viewer = reader.slideshow();
            assertNotNull("Overlay offen", viewer);
            assertEquals("Ablauf Beispiel.pdf", viewer.attachment().fileName());
            assertEquals("PDF · 3 KB", viewer.fileInfoText());
            assertEquals(3, viewer.pageCount());
            assertTrue("Seite 1 ist gezeichnet", viewer.showsPicture());
            assertTrue(viewer.infoText(), viewer.infoText().startsWith("Seite 1 / 3"));
            assertFalse(viewer.hasPrevious());
            viewer.next();
            viewer.next();
            assertEquals(2, viewer.page());
            assertTrue(viewer.infoText(), viewer.infoText().startsWith("Seite 3 / 3"));
            assertFalse(viewer.hasNext());
            viewer.previous();
            assertEquals(1, viewer.page());

            // Zoom: größer, danach schärfer nachgezeichnet
            int width = viewer.pictureWidth();
            int zoom = viewer.zoomPercent();
            viewer.zoomIn();
            assertTrue(viewer.zoomPercent() > zoom);
            viewer.sharpenNow();
            assertTrue("schärfer gezeichnet", viewer.renderScale() > ImageSlideshowPanel.PDF_BASE_SCALE);
            assertTrue(viewer.pictureWidth() > width);
            viewer.zoomOut();

            // Speichern und Öffnen der Originaldatei
            assertEquals(ImageSlideshowPanel.FILE_SAVED_PREFIX + "kopie.pdf", viewer.saveTo(target));
            assertArrayEquals(pdf, read(target));
            assertNull(viewer.openOriginal());
            assertEquals(1, opened.size());
            assertEquals("Ablauf Beispiel.pdf", opened.get(0).getName());
            assertArrayEquals(pdf, read(opened.get(0)));
            assertEquals("Original einmal über die Quelle geladen", 1, (int) source.loads.get("att-pdf"));
        });
    }

    @Test
    public void anUnknownTypeStaysInTheListAndOpensWithNameTypeSizeSaveAndOpen() throws Exception {
        final AttachmentPage source = new AttachmentPage(pdf(1));
        final List<File> opened = new ArrayList<File>();
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReaderPanel reader = workspace.reader();
            reader.setFileOpener(opened::add);
            controller(workspace, source).open(new ReaderLink(SOURCE.value(), PAGE, "Seite", "", ""), true);

            ReaderAttachmentList.rows(reader.attachmentMenu()).get(4).activate();
            ImageSlideshowPanel viewer = reader.slideshow();
            assertNotNull(viewer);
            assertEquals("daten.bin", viewer.attachment().fileName());
            assertEquals(ReaderAttachmentKind.OTHER, viewer.attachment().kind());
            String status = viewer.statusText();
            assertTrue(status, status.contains("daten.bin"));
            assertTrue(status, status.contains("BIN · 12 B"));
            assertTrue(status, status.contains(ImageSlideshowPanel.NO_PREVIEW_TEXT));
            assertTrue(viewer.saveButton().isEnabled());
            assertNull("ohne Vorschau nichts geladen", source.loads.get("att-bin"));

            viewer.openButton().doClick();
            assertEquals(1, opened.size());
            assertEquals("daten.bin", opened.get(0).getName());
            assertArrayEquals(AttachmentPage.BIN, read(opened.get(0)));
        });
    }

    @Test
    public void textImageAndOfficeShowTheirContent() throws Exception {
        final AttachmentPage source = new AttachmentPage(pdf(1));
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReaderPanel reader = workspace.reader();
            controller(workspace, source).open(new ReaderLink(SOURCE.value(), PAGE, "Seite", "", ""), true);

            reader.openAttachment(PAGE_KEY, 2);
            ImageSlideshowPanel text = reader.slideshow();
            assertTrue(text.isShowingText());
            assertEquals("Zeile 1\nÄnderung 2\n", text.textContent());

            reader.openAttachment(PAGE_KEY, 3);
            ImageSlideshowPanel image = reader.slideshow();
            assertEquals("Architektur.png", image.attachment().fileName());
            assertTrue(image.showsPicture());

            // Office: das umgewandelte Dokument fehlt, das Bild der ersten Seite kommt von der Quelle
            reader.openAttachment(PAGE_KEY, 1);
            ImageSlideshowPanel office = reader.slideshow();
            assertTrue(office.showsPicture());
            assertEquals(Arrays.asList(PageView.Preview.DOCUMENT, PageView.Preview.POSTER), source.previews);
            assertNull("Vorschau ohne Original", source.loads.get("att-xlsx"));
        });
    }

    @Test
    public void clickingThePaperclipShowsTheListAndAnEntryOpensTheOverlay() throws Exception {
        final AttachmentPage source = new AttachmentPage(pdf(2));
        final List<JPopupMenu> shown = new ArrayList<JPopupMenu>();
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReaderPanel reader = workspace.reader();
            reader.setAttachmentRenderer(Runnable::run);
            reader.setAttachmentMenuShower(shown::add);
            controller(workspace, source).open(new ReaderLink(SOURCE.value(), PAGE, "Seite", "", ""), true);

            reader.attachmentsButton().doClick();

            assertEquals("ein Klick, eine Liste", 1, shown.size());
            List<ReaderAttachmentList.Row> rows = ReaderAttachmentList.rows(shown.get(0));
            assertEquals(5, rows.size());
            assertEquals("Ablauf Beispiel.pdf", rows.get(0).nameText());
            rows.get(0).activate();
            ImageSlideshowPanel viewer = reader.slideshow();
            assertNotNull("Overlay offen", viewer);
            assertEquals("Ablauf Beispiel.pdf", viewer.attachment().fileName());
            assertEquals(2, viewer.pageCount());
        });
    }

    @Test
    public void anOfficeFileWithoutPreviewShowsTheReasonAndKeepsSaveAndOpen() throws Exception {
        assertOfficeFailureVisible(new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND,
                "Vorschau: nicht gefunden (HTTP 404)"));
        assertOfficeFailureVisible(new KnowledgeSourceException(KnowledgeSourceException.Kind.INVALID_RESPONSE,
                "Vorschau: erwartet PDF oder Bild, erhalten text/html"));
    }

    private void assertOfficeFailureVisible(KnowledgeSourceException failure) throws Exception {
        final AttachmentPage source = new AttachmentPage(pdf(1));
        source.previewFailure = failure;
        final List<File> opened = new ArrayList<File>();
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReaderPanel reader = workspace.reader();
            reader.setFileOpener(opened::add);
            controller(workspace, source).open(new ReaderLink(SOURCE.value(), PAGE, "Seite", "", ""), true);

            reader.openAttachment(PAGE_KEY, 1);
            ImageSlideshowPanel office = reader.slideshow();
            assertFalse(office.showsPicture());
            String status = office.statusText();
            assertTrue(status, status.contains("Planung.xlsx"));
            assertTrue(status, status.contains("Excel · 82 KB"));
            assertTrue(status, status.contains(ImageSlideshowPanel.NO_PREVIEW_TEXT));
            assertTrue(status, status.contains(failure.getMessage()));
            assertEquals("alle Vorschauarten versucht", 3, source.previews.size());
            assertTrue(office.saveButton().isEnabled());
            office.openButton().doClick();
            assertEquals("Öffnen lädt das Original", 1, opened.size());
            assertEquals("Planung.xlsx", opened.get(0).getName());
        });
    }

    @Test
    public void aPageWithoutAttachmentsHidesThePaperclip() throws Exception {
        final AttachmentPage source = new AttachmentPage(pdf(1));
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReaderPanel reader = workspace.reader();
            KnowledgeReadingController controller = controller(workspace, source);
            controller.open(new ReaderLink(SOURCE.value(), PAGE, "Seite", "", ""), true);
            assertTrue(reader.attachmentsButton().isVisible());

            controller.open(new ReaderLink(SOURCE.value(), EMPTY_PAGE, "Leer", "", ""), true);
            assertFalse(reader.attachmentsButton().isVisible());
            assertNull(reader.attachmentMenu());

            // Suche auf und zu: die ausgeblendete Büroklammer bleibt aus
            reader.toolbar().open();
            reader.toolbar().close();
            assertFalse(reader.attachmentsButton().isVisible());
        });
    }

    @Test
    public void sizesAndKindsReadLikeAnEmailAttachmentList() {
        assertEquals("12 B", ReaderAttachment.size(12));
        assertEquals("459 KB", ReaderAttachment.size(469680));
        assertEquals("1,5 MB", ReaderAttachment.size(1572864));
        assertEquals("", ReaderAttachment.size(-1));
        assertEquals(ReaderAttachmentKind.PDF, ReaderAttachmentKind.of("application/octet-stream", "a.PDF"));
        assertEquals(ReaderAttachmentKind.IMAGE, ReaderAttachmentKind.of("image/svg+xml", "a.svg"));
        assertEquals(ReaderAttachmentKind.TEXT, ReaderAttachmentKind.of("application/json", "a"));
        assertEquals(ReaderAttachmentKind.OFFICE, ReaderAttachmentKind.of(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "a.docx"));
        assertEquals(ReaderAttachmentKind.OTHER, ReaderAttachmentKind.of("application/vnd.jgraph.mxfile", "a"));
        assertEquals("Word", new ReaderAttachment("k", "Brief.docx", "", -1).summary());
        assertEquals("Anhang ohne Pfad", "a_b.txt", ImageSlideshowPanel.safeFileName("a/b.txt"));
        assertEquals("Ä\r\n", ImageSlideshowPanel.decodeText(new byte[] {(byte) 0xC4, 13, 10}, "text/plain"));
    }

    // ------------------------------------------------------------------ Aufbau

    private static final String PAGE_KEY = new ReaderLink(SOURCE.value(), PAGE, "Seite", "", "").key();

    private ChatWorkspacePanel workspace() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        ChatShellPanel shell = new ChatShellPanel(model, new ChatShellActions() {
            @Override
            public void sendRequested(String text, boolean ragEnabled) {
            }

            @Override
            public void stopRequested() {
            }
        }, ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired());
        return new ChatWorkspacePanel(new ShellModeModel(false), shell, null, ComicPalette.defaultPalette());
    }

    private KnowledgeReadingController controller(ChatWorkspacePanel workspace, AttachmentPage source) {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        KnowledgePrefetchService prefetch = new KnowledgePrefetchService(indexing, (owner, port, id) -> port.load(id),
                () -> KnowledgePrefetchService.Settings.of(5, 1, 10));
        services.add(prefetch);
        final KnowledgeSourceRegistration registration = new KnowledgeSourceRegistration(source,
                SourceScope.of("1"));
        KnowledgeReadingController controller = new KnowledgeReadingController(workspace,
                () -> Collections.singletonList(registration), id -> SOURCE.value().equals(id) ? registration : null,
                new SearchKnowledgeSourcesUseCase(index, embeddings::modelIdentity, Runnable::run),
                new BrowseKnowledgeSourceUseCase((owner, port, id) -> port.load(id)), prefetch, null, null,
                Runnable::run, Runnable::run);
        controller.attach();
        return controller;
    }

    private static byte[] pdf(int pages) throws IOException {
        PDDocument document = new PDDocument();
        try {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDPageContentStream content = new PDPageContentStream(document, page);
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 24);
                content.newLineAtOffset(72, 700);
                content.showText("Seite " + i);
                content.endText();
                content.close();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } finally {
            document.close();
        }
    }

    private static byte[] png() {
        try {
            BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] read(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void run(Runnable body) throws Exception {
        try {
            SwingUtilities.invokeAndWait(body);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            throw ex;
        }
    }

    /** Eine Quelle mit Lese-Ansicht: Seite 1 hat fünf Anhänge (PDF, Excel, Text, PNG, unbekannt), Seite 2 keine. */
    private static final class AttachmentPage implements KnowledgeSourcePort, PageViewSource {
        static final byte[] BIN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        private static final byte[] TEXT = "Zeile 1\nÄnderung 2\n".getBytes(StandardCharsets.UTF_8);

        private final Map<String, byte[]> files = new HashMap<String, byte[]>();
        final Map<String, Integer> loads = new HashMap<String, Integer>();
        final List<PageView.Preview> previews = new ArrayList<PageView.Preview>();
        KnowledgeSourceException previewFailure;
        private final byte[] picture = png();

        AttachmentPage(byte[] pdf) {
            files.put("att-pdf", pdf);
            files.put("att-txt", TEXT);
            files.put("att-png", picture);
            files.put("att-bin", BIN);
            files.put("att-xlsx", new byte[] {'P', 'K', 3, 4});
        }

        @Override
        public SourceAddress sourceId() {
            return SOURCE;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId id) {
            return KnowledgeDocument.of(KnowledgeResource.builder(id, SOURCE).title("Seite").build(), "# Seite\n\nText");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId id) {
            return Collections.emptyList();
        }

        @Override
        public PageView pageView(KnowledgeResourceId page) {
            if (EMPTY_PAGE.equals(page.value())) {
                return new PageView("<p>Ohne Anhänge</p>", "https://wiki.invalid/", null);
            }
            return new PageView("<p>Mit Anhängen</p>", "https://wiki.invalid/", null, Arrays.asList(
                    attachment("att-pdf", "Ablauf Beispiel.pdf", "application/pdf", 3100),
                    attachment("att-xlsx", "Planung.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 84000),
                    attachment("att-txt", "notizen.txt", "text/plain;charset=UTF-8", TEXT.length),
                    attachment("att-png", "Architektur.png", "image/png", picture.length),
                    attachment("att-bin", "daten.bin", "application/x-unbekannt", BIN.length)));
        }

        private static PageView.Attachment attachment(String id, String name, String type, long size) {
            return new PageView.Attachment(id, "1", name, type, "1", size, "/download/" + id, "/view/" + id,
                    EnumSet.allOf(PageView.Preview.class));
        }

        @Override
        public byte[] loadViewImage(String src) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, src);
        }

        @Override
        public byte[] loadAttachment(PageView.Attachment attachment) throws KnowledgeSourceException {
            Integer count = loads.get(attachment.id());
            loads.put(attachment.id(), count == null ? 1 : count + 1);
            byte[] bytes = files.get(attachment.id());
            if (bytes == null) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, attachment.id());
            }
            return bytes;
        }

        @Override
        public byte[] loadAttachmentPreview(PageView.Attachment attachment, PageView.Preview preview)
                throws KnowledgeSourceException {
            previews.add(preview);
            if (previewFailure != null) {
                throw previewFailure;
            }
            if (preview == PageView.Preview.POSTER) {
                return picture;
            }
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "keine Umwandlung");
        }
    }
}
