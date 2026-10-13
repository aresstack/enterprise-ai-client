package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Issue 90: the reading view follows the available width at every resize (sidebar shown or hidden, window narrower
 * or wider) without a reload. Text and code wrap, a wide diagram shrinks proportionally, and the reader never needs a
 * horizontal scroll bar for any of them.
 */
public class ReaderResponsiveLayoutTest {

    private static final String BASE = "https://confluence.invalid/confluence/";
    private static final String DIAGRAM = BASE + "download/attachments/9/diagram.png";
    private static final int DIAGRAM_W = 1800;
    private static final int DIAGRAM_H = 600;
    private static final String CODE_LINE = "    if (request.isAuthorized() && configuration.featureEnabled(\"export\")) "
            + "{ exporter.writeAll(repository.findAllByOwner(currentUser), destinationDirectory, options); }";

    @Test
    public void textCodeAndDiagramsFollowTheWidthAtEveryResize() throws Exception {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
        final ReaderLink link = new ReaderLink("wiki", "confluence:confluence.invalid/confluence/page/9", "Seite", "",
                "");
        SwingUtilities.invokeAndWait(() -> {
            reader.open(link, false);
            reader.showHtml(link.key(), page(), BASE,
                    Collections.singletonList(new ReaderImage(DIAGRAM, DIAGRAM, "Architektur")), new PngLoader());
            ReaderHtmlView view = reader.htmlView(link.key());
            int[] widths = {1400, 600, 1100, 450, 900};
            for (int width : widths) {
                reader.setSize(width, 700);
                layout(reader);
                layout(reader);
                assertFits(reader, view, width);
            }
            reader.setSize(2600, 700);
            layout(reader);
            layout(reader);
            Dimension natural = view.knownImageSizes().get(0);
            assertEquals("wide enough: natural size", DIAGRAM_W, natural.width);
            assertEquals(DIAGRAM_H, natural.height);
        });
    }

    private static void assertFits(KnowledgeReaderPanel reader, ReaderHtmlView view, int width) {
        JScrollPane scroll = find(reader);
        assertNotNull(scroll);
        int viewport = scroll.getViewport().getWidth();
        Component page = scroll.getViewport().getView();
        assertTrue("page wider than the reader at " + width + ": " + page.getWidth() + " > " + viewport,
                page.getWidth() <= viewport);
        assertTrue("view wider than the page at " + width, view.getWidth() <= page.getWidth());
        for (int offset = 0; offset < view.getDocument().getLength(); offset++) {
            Rectangle caret;
            try {
                caret = view.modelToView(offset);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            if (caret != null) {
                assertTrue("character " + offset + " at x=" + caret.x + " right of " + view.getWidth() + " (reader "
                        + width + ")", caret.x <= view.getWidth());
            }
        }
        Dimension diagram = view.knownImageSizes().get(0);
        assertTrue("diagram " + diagram.width + " wider than the view " + view.getWidth() + " at " + width,
                diagram.width <= view.getWidth());
        double ratio = DIAGRAM_W / (double) DIAGRAM_H;
        assertEquals("aspect ratio kept at " + width, ratio, diagram.width / (double) diagram.height, 0.05);
    }

    private static String page() {
        return "<h1>Schnittstelle</h1><p>Die Schnittstelle liefert alle Datensätze des angemeldeten Benutzers und "
                + "schreibt sie in das Zielverzeichnis, sofern die Funktion freigeschaltet ist.</p>"
                + "<p><img src=\"" + DIAGRAM + "\" alt=\"Architektur\"></p>"
                + "<pre>" + CODE_LINE + "\n" + CODE_LINE + "</pre>"
                + "<ul><li>Erster Punkt mit einer längeren Beschreibung, die umbrechen muss, wenn es eng wird.</li></ul>";
    }

    /** Like validate() without a screen: outside in. */
    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static JScrollPane find(Component component) {
        if (!component.isVisible()) {
            return null;
        }
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JScrollPane found = find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Delivers a wide PNG at once. */
    private static final class PngLoader implements ReaderImageLoader {
        @Override
        public void load(ReaderImage image, Consumer<byte[]> done) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(DIAGRAM_W, DIAGRAM_H, BufferedImage.TYPE_INT_RGB), "png", out);
                done.accept(out.toByteArray());
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void describe(ReaderImage image, String mediaType, byte[] bytes,
                             Consumer<ReaderImageDescription> done) {
        }
    }

}
