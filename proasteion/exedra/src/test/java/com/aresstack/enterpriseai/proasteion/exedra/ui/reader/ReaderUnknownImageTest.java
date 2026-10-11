package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Angelos Live-Fehler: ein Jira-Symbol im Link erschien als grauer „Bild“-Platzhalter, und ein Klick darauf öffnete
 * den Jira-Link. Ein {@code <img>} ohne bekanntes Bild hat keine Bildfläche mehr; Inhaltsbilder bleiben wie bisher.
 */
public class ReaderUnknownImageTest {

    private static final String BASE = "https://confluence.invalid/confluence/";
    private static final String A = BASE + "download/attachments/7/a.png";
    private static final String JIRA = "https://jira.invalid/browse/PROJ-12345";
    private static final String PAGE = "<p>Vorher <img src=\"" + A + "\" alt=\"Architektur\"> Ende</p>"
            + "<p><a href=\"" + JIRA + "\"><img src=\"/images/icons/jira-issue.png\"> PROJ-12345</a></p>"
            + "<p>Ohne Link <img src=\"/images/icons/emoticons/smile.png\"> und gross"
            + " <img src=\"https://other.invalid/unknown.png\" width=\"400\" height=\"300\"> Text</p>"
            + "<p>Mit Text <img src=\"/images/icons/status.png\" alt=\"erledigt\"> danach</p>";

    /** Regressionstests 1, 2 und 5: kein Platzhalter, Schlüssel sichtbar, Klick darauf öffnet den Jira-Link. */
    @Test
    public void unknownImagesGetNoPlaceholderAndTheJiraKeyOpensItsLink() throws Exception {
        final List<String> opened = new ArrayList<String>();
        run(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), opened::add,
                    CachingMermaidImageRenderer.forChat());
            ReaderLink link = link();
            reader.open(link, true);
            reader.showHtml(link.key(), PAGE, BASE, Collections.singletonList(new ReaderImage(A, A, "Architektur")),
                    new PngLoader());
            ReaderHtmlView view = laidOut(reader.htmlView(link.key()));

            List<Dimension> unknown = view.unknownImageSizes();
            assertEquals(4, unknown.size());
            int lineHeight = view.getFontMetrics(view.getFont()).getHeight() * 2;
            int withArea = 0;
            for (Dimension size : unknown) {
                assertTrue("kein Bildplatzhalter: " + size, size.height < lineHeight && size.width < 160);
                withArea += size.width > 0 ? 1 : 0;
            }
            assertEquals("nur das img mit Alternativtext belegt Platz", 1, withArea);
            assertTrue(view.getText(), text(view).contains("PROJ-12345"));

            Point key = pointOf(view, "PROJ-12345");
            assertNull(view.imageAt(key));
            click(view, key);
            assertEquals(Collections.singletonList(JIRA), opened);
            assertNull("kein Bildklick", reader.slideshow());
        });
    }

    /** Regressionstest 3: ein Inhaltsbild lädt inline, und ein Klick öffnet die Diashow statt eines Links. */
    @Test
    public void aContentImageStillLoadsInlineAndOpensTheSlideshow() throws Exception {
        final List<String> opened = new ArrayList<String>();
        run(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), opened::add,
                    CachingMermaidImageRenderer.forChat());
            ReaderLink link = link();
            reader.open(link, true);
            PngLoader loader = new PngLoader();
            reader.showHtml(link.key(), PAGE, BASE, Collections.singletonList(new ReaderImage(A, A, "Architektur")),
                    loader);
            ReaderHtmlView view = laidOut(reader.htmlView(link.key()));

            assertEquals(1, view.inlineImages().size());
            assertEquals(A, view.inlineImages().get(0).src());
            assertEquals(Collections.singletonList(A), loader.loaded);

            Rectangle at = imageRect(view, A);
            Point inside = new Point(at.x + 5, at.y + 5);
            assertNotNull(view.imageAt(inside));
            click(view, inside);
            assertNotNull(reader.slideshow());
            assertEquals(A, reader.slideshow().current().src());
            assertTrue(opened.isEmpty());
        });
    }

    private static ReaderLink link() {
        return new ReaderLink("wiki", "confluence:confluence.invalid/confluence/page/7", "Seite", "", "");
    }

    private static ReaderHtmlView laidOut(ReaderHtmlView view) {
        assertNotNull(view);
        view.setSize(800, view.preferredHeightForWidth(800));
        view.doLayout();
        BufferedImage canvas = new BufferedImage(800, Math.max(1, view.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            view.paint(g);
        } finally {
            g.dispose();
        }
        return view;
    }

    private static String text(ReaderHtmlView view) {
        try {
            return view.getDocument().getText(0, view.getDocument().getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Point pointOf(ReaderHtmlView view, String text) {
        try {
            int offset = text(view).indexOf(text);
            Rectangle start = view.modelToView(offset + 2);
            return new Point(start.x + 1, start.y + start.height / 2);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Rectangle imageRect(ReaderHtmlView view, String src) {
        Element img = find(((HTMLDocument) view.getDocument()).getDefaultRootElement(), src);
        assertNotNull(img);
        try {
            Rectangle start = view.modelToView(img.getStartOffset());
            Rectangle end = view.modelToView(img.getEndOffset());
            return new Rectangle(start.x, start.y, Math.max(1, end.x - start.x), start.height);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Element find(Element element, String src) {
        if (element.getAttributes().getAttribute(StyleConstants.NameAttribute) == HTML.Tag.IMG
                && src.equals(String.valueOf(element.getAttributes().getAttribute(HTML.Attribute.SRC)))) {
            return element;
        }
        for (int i = 0; i < element.getElementCount(); i++) {
            Element found = find(element.getElement(i), src);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Ein echter Linksklick: läuft durch den Link-Controller des HTML-Kits und den Bildklick der Ansicht. */
    private static void click(ReaderHtmlView view, Point at) {
        long now = System.currentTimeMillis();
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, now, 0, at.x, at.y, 1, false,
                MouseEvent.BUTTON1));
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, now, 0, at.x, at.y, 1, false,
                MouseEvent.BUTTON1));
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_CLICKED, now, 0, at.x, at.y, 1, false,
                MouseEvent.BUTTON1));
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

    private static final class PngLoader implements ReaderImageLoader {
        final List<String> loaded = new ArrayList<String>();

        @Override
        public void load(ReaderImage image, Consumer<byte[]> done) {
            loaded.add(image.src());
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
                done.accept(out.toByteArray());
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
