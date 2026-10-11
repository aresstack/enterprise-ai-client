package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Angelos Abnahme: Bilder bleiben inline, die Diashow läuft über alle Bilder, die Beschreibung wird erst beim ersten
 * Umschalten erzeugt und beim zweiten aus dem Cache gezeigt.
 */
public class ReaderImagesTest {

    private static final String BASE = "https://confluence.invalid/confluence/";
    private static final String A = BASE + "download/attachments/7/a.png";
    private static final String B = BASE + "download/attachments/7/b.png";
    private static final String C = BASE + "download/attachments/7/c.png";
    private static final String HTML = "<h2>Überblick</h2><p>Vorher <img src=\"" + A + "\" alt=\"Architektur\"> mitten"
            + "</p><p><img src=\"" + B + "\" alt=\"Ablauf\"></p><p>Text <img src=\"" + C + "\"> Ende</p>";

    @Test
    public void imagesStayInlineAndTheSlideshowWalksAllOfThem() throws Exception {
        final RecordingLoader loader = new RecordingLoader();
        run(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink link = link();
            reader.open(link, true);
            reader.showHtml(link.key(), HTML, BASE, images(), loader);

            ReaderHtmlView view = reader.htmlView(link.key());
            assertNotNull(view);
            assertEquals(Arrays.asList(A, B, C), srcs(view.inlineImages()));
            assertTrue(view.getText().contains("Vorher"));
            assertNotNull("Banner auch über der Lese-Ansicht", reader.navigationSky(link.key()));
            assertTrue(reader.imagesButton().isVisible());
            assertEquals("Bilder (3)", reader.imagesButton().getText());

            reader.openSlideshow(link.key(), 0);
            ImageSlideshowPanel show = reader.slideshow();
            assertNotNull(show);
            assertEquals(0, show.index());
            assertFalse(show.hasPrevious());
            show.next();
            assertEquals(B, show.current().src());
            assertTrue(show.infoText(), show.infoText().startsWith("2 / 3"));
            show.next();
            assertEquals(C, show.current().src());
            assertFalse(show.hasNext());
            show.next();
            assertEquals(2, show.index());
            show.previous();
            show.previous();
            assertEquals(A, show.current().src());
            assertEquals(0, loader.describeCalls.size());
        });
    }

    @Test
    public void theDescriptionIsMadeAtTheFirstSwitchAndThenComesFromTheCache() throws Exception {
        final RecordingLoader loader = new RecordingLoader();
        run(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink link = link();
            reader.open(link, true);
            reader.showHtml(link.key(), HTML, BASE, images(), loader);
            reader.openSlideshow(link.key(), 1);
            ImageSlideshowPanel show = reader.slideshow();
            assertFalse(show.isShowingDescription());
            assertEquals("Seite und Diashow fragen das Modell nicht", 0, loader.describeCalls.size());

            show.showDescription();
            assertTrue(show.isShowingDescription());
            assertEquals(1, loader.describeCalls.size());
            assertEquals(B, loader.describeCalls.get(0).src());
            assertEquals(ImageSlideshowPanel.DESCRIBING_TEXT, show.descriptionText());

            loader.pending.accept(ReaderImageDescription.of("Ein Ablaufdiagramm."));
            assertEquals("Ein Ablaufdiagramm.", show.descriptionText());

            show.showImage();
            assertFalse(show.isShowingDescription());
            show.showDescription();
            assertEquals("Ein Ablaufdiagramm.", show.descriptionText());
            assertEquals("Der zweite Wechsel nutzt den Cache", 1, loader.describeCalls.size());
        });
    }

    private static ReaderLink link() {
        return new ReaderLink("wiki", "confluence:confluence.invalid/confluence/page/7", "Seite", "", "");
    }

    private static List<ReaderImage> images() {
        return Arrays.asList(new ReaderImage(A, A, "Architektur"), new ReaderImage(B, B, "Ablauf"),
                new ReaderImage(C, C, ""));
    }

    private static List<String> srcs(List<ReaderImage> images) {
        List<String> result = new ArrayList<String>();
        for (ReaderImage image : images) {
            result.add(image.src());
        }
        return result;
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

    /** Liefert sofort ein PNG; Beschreibungen hält er zurück, bis der Test sie freigibt. */
    private static final class RecordingLoader implements ReaderImageLoader {
        final List<ReaderImage> describeCalls = new ArrayList<ReaderImage>();
        Consumer<ReaderImageDescription> pending;

        @Override
        public void load(ReaderImage image, Consumer<byte[]> done) {
            done.accept(png());
        }

        @Override
        public void describe(ReaderImage image, String mediaType, byte[] bytes,
                             Consumer<ReaderImageDescription> done) {
            assertEquals("image/png", mediaType);
            describeCalls.add(image);
            pending = done;
        }

        private static byte[] png() {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
                return out.toByteArray();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
