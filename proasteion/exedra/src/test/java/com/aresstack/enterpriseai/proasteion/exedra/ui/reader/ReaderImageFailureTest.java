package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Angelos Live-Fehler: statt „Bild konnte nicht geladen werden“ steht der Grund aus der Quelle im Overlay. */
public class ReaderImageFailureTest {

    private static final String BASE = "https://confluence.invalid/confluence/";
    private static final String A = BASE + "download/attachments/7/a.png";
    private static final String HTML = "<p>Vorher <img src=\"" + A + "\" alt=\"Architektur\"> Ende</p>";
    private static final String REASON = "Bild: erwartet Bild, erhalten application/json (HTTP 200, 18 Bytes)";

    @Test
    public void theReasonFromTheSourceReachesTheSlideshow() throws Exception {
        run(() -> {
            ImageSlideshowPanel show = open(new ReaderImageLoader() {
                @Override
                public void load(ReaderImage image, Consumer<byte[]> done) {
                    done.accept(null);
                }

                @Override
                public void load(ReaderImage image, Consumer<byte[]> done, Consumer<String> failed) {
                    failed.accept(REASON);
                }
            });
            assertTrue(show.statusText(), show.statusText().contains("Bild konnte nicht geladen werden"));
            assertTrue(show.statusText(), show.statusText().contains(REASON));
        });
    }

    @Test
    public void undecodableBytesSayWhatArrived() throws Exception {
        run(() -> {
            ImageSlideshowPanel show = open(new ReaderImageLoader() {
                @Override
                public void load(ReaderImage image, Consumer<byte[]> done) {
                    done.accept("{\"x\":1}".getBytes(StandardCharsets.UTF_8));
                }
            });
            assertTrue(show.statusText(), show.statusText().contains("Bilddaten nicht lesbar (7 Bytes"));
        });
    }

    @Test
    public void withoutAReasonTheGeneralTextStays() throws Exception {
        run(() -> {
            ImageSlideshowPanel show = open(new ReaderImageLoader() {
                @Override
                public void load(ReaderImage image, Consumer<byte[]> done) {
                    done.accept(null);
                }
            });
            assertTrue(show.statusText(), show.statusText().contains("Bild konnte nicht geladen werden"));
            assertFalse(show.statusText(), show.statusText().contains(":<br>"));
        });
    }

    private static ImageSlideshowPanel open(ReaderImageLoader loader) {
        KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
        ReaderLink link = new ReaderLink("wiki", "confluence:confluence.invalid/confluence/page/7", "Seite", "", "");
        reader.open(link, true);
        reader.showHtml(link.key(), HTML, BASE, Collections.singletonList(new ReaderImage(A, A, "Architektur")),
                loader);
        reader.openSlideshow(link.key(), 0);
        ImageSlideshowPanel show = reader.slideshow();
        assertEquals(A, show.current().src());
        return show;
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
}
