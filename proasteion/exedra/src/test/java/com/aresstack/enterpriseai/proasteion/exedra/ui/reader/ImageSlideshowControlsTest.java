package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Bedienung der Diashow wie MainframeMates {@code ImageStripPanel.openOverlay}: Klick auf ein Inline-Bild, Knöpfe
 * nur in ihrer Zone, Tasten und Zoom, Speichern mit Meldung, SVG und animiertes GIF.
 */
public class ImageSlideshowControlsTest {

    private static final String BASE = "https://confluence.invalid/";
    private static final String A = BASE + "download/a.png";
    private static final String B = BASE + "download/b.png";

    @Test
    public void clickOnAnInlineImageOpensTheSlideshowAtThatImage() throws Exception {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
        final ReaderLink link = new ReaderLink("wiki", "confluence:confluence.invalid/page/1", "Seite", "", "");
        onEdt(() -> {
            reader.open(link, true);
            reader.showHtml(link.key(), "<p>Erstes</p><p><img src=\"" + A + "\"></p><p>Zweites</p><p><img src=\""
                    + B + "\"></p>", BASE, images(), pngLoader());
            reader.setSize(800, 900);
            for (int i = 0; i < 3; i++) {
                reader.validate();
                layout(reader);
            }
            ReaderHtmlView view = reader.htmlView(link.key());
            BufferedImage screen = new BufferedImage(Math.max(1, view.getWidth()), Math.max(1, view.getHeight()),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = screen.createGraphics();
            view.paint(g); // legt die Bildflächen fest, wie beim ersten Zeichnen auf dem Bildschirm
            g.dispose();
            Point onB = find(view, B);
            assertNotNull("zweites Bild gezeichnet", onB);
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_CLICKED, 0L, 0, onB.x, onB.y, 1, false,
                    MouseEvent.BUTTON1));
            ImageSlideshowPanel show = reader.slideshow();
            assertNotNull("Diashow offen", show);
            assertEquals(1, show.index());
            assertEquals(B, show.current().src());
        });
    }

    @Test
    public void buttonsAppearOnlyInTheirZone() throws Exception {
        final ImageSlideshowPanel show = slideshow(pngLoader());
        onEdt(() -> {
            show.setSize(700, 460);
            show.doLayout();
        });
        onEdt(() -> {
            show.hover(null);
            assertArrayEquals(new boolean[] {false, false, false, false}, show.visibleButtons());
            int w = show.getComponent(0).getWidth();
            int h = show.getComponent(0).getHeight();
            assertTrue(w > 0 && h > 0);
            show.hover(new Point(w - 20, 20));
            assertArrayEquals("oben rechts: Herunterladen", new boolean[] {false, false, true, false},
                    show.visibleButtons());
            show.hover(new Point(w - 20, h - 20));
            assertArrayEquals("unten rechts: Zoom", new boolean[] {false, false, false, true}, show.visibleButtons());
            show.hover(new Point(10, h / 2));
            assertArrayEquals("links ohne vorheriges Bild nichts", new boolean[] {false, false, false, false},
                    show.visibleButtons());
            show.hover(new Point(w - 10, h / 2));
            assertArrayEquals("rechts: weiter", new boolean[] {false, true, false, false}, show.visibleButtons());
            show.next();
            show.hover(new Point(10, h / 2));
            assertArrayEquals("links: zurück", new boolean[] {true, false, false, false}, show.visibleButtons());
            show.hover(new Point(w / 2, 5));
            assertArrayEquals("Mitte oben: nichts", new boolean[] {false, false, false, false}, show.visibleButtons());
        });
    }

    @Test
    public void keysZoomAndSaveWorkLikeMainframeMate() throws Exception {
        final ImageSlideshowPanel show = slideshow(pngLoader());
        final File target = File.createTempFile("bild", ".png");
        target.deleteOnExit();
        onEdt(() -> {
            assertEquals("next", show.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(KeyStroke.getKeyStroke("RIGHT")));
            String before = show.infoText();
            show.getActionMap().get("zoomIn").actionPerformed(null);
            assertNotEquals("Zoom ändert sich", before, show.infoText());
            assertTrue(show.infoText(), show.infoText().contains("Zoom: 125 %"));
            show.getActionMap().get("zoomOut").actionPerformed(null);
            assertTrue(show.infoText(), show.infoText().contains("Zoom: 100 %"));
            show.getActionMap().get("next").actionPerformed(null);
            assertEquals(1, show.index());
            show.getActionMap().get("previous").actionPerformed(null);
            assertEquals(0, show.index());

            assertEquals("a.png", ImageSlideshowPanel.fileName(show.current()));
            assertEquals(ImageSlideshowPanel.SAVED_PREFIX + target.getName(), show.saveTo(target));
            try {
                assertArrayEquals(png(), Files.readAllBytes(target.toPath()));
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            assertTrue(show.saveTo(new File(target, "kein/verzeichnis.png"))
                    .startsWith(ImageSlideshowPanel.SAVE_FAILED_PREFIX));
        });
    }

    @Test
    public void svgIsRasterisedAndAnimatedGifsAreRecognised() throws Exception {
        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"30\">"
                + "<rect width=\"40\" height=\"30\" fill=\"red\"/></svg>").getBytes(StandardCharsets.UTF_8);
        ReaderImageStore.Loaded vector = ReaderImageStore.Loaded.of(svg);
        assertTrue("SVG gerastert", vector.ok());
        assertEquals("image/png", vector.visionMediaType());
        byte[] png = vector.visionBytes();
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);

        byte[] animated = gif(2);
        assertTrue("zwei Bilder: animiert", ReaderImageStore.Loaded.of(animated).isAnimatedGif());
        assertFalse("ein Bild: nicht animiert", ReaderImageStore.Loaded.of(gif(1)).isAnimatedGif());
        assertEquals("image/gif", ReaderImageStore.Loaded.of(animated).visionMediaType());

        final ImageSlideshowPanel show = slideshow((image, done) -> done.accept(animated));
        onEdt(() -> assertTrue(show.infoText(), show.infoText().contains("(8 × 6)")));
    }

    /** draw.io-Zeichnung: Adresse der PNG-Vorschau, geladen wird SVG; gespeichert wird nie SVG-Text als .png. */
    @Test
    public void aDrawioSvgBehindAPngAddressIsSavedAsSvgOrAsRealPng() throws Exception {
        final byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"30\">"
                + "<rect width=\"40\" height=\"30\" fill=\"red\"/></svg>").getBytes(StandardCharsets.UTF_8);
        final String src = BASE + "download/attachments/42/Bild%20A.png?version=3&api=v2#drawio=Bild%20A&page=42"
                + "&revision=3";
        final ImageSlideshowPanel[] holder = new ImageSlideshowPanel[1];
        onEdt(() -> holder[0] = new ImageSlideshowPanel(new ReaderImageStore(
                Arrays.asList(new ReaderImage(src, src, "Bild A")), (image, done) -> done.accept(svg)), 0));
        final ImageSlideshowPanel show = holder[0];
        final File dir = Files.createTempDirectory("zeichnung").toFile();
        dir.deleteOnExit();
        onEdt(() -> {
            ReaderImageStore.Loaded loaded = show.loadedCurrent();
            assertTrue("Anzeige", loaded != null && loaded.ok());
            assertEquals("Bild A.svg", ImageSlideshowPanel.fileName(show.current(), loaded));
            try {
                File asSvg = new File(dir, "Bild A.svg");
                asSvg.deleteOnExit();
                assertEquals(ImageSlideshowPanel.SAVED_PREFIX + "Bild A.svg", show.saveTo(asSvg));
                assertArrayEquals(svg, Files.readAllBytes(asSvg.toPath()));
                File asPng = new File(dir, "Bild A.png");
                asPng.deleteOnExit();
                assertEquals(ImageSlideshowPanel.SAVED_PREFIX + "Bild A.png", show.saveTo(asPng));
                byte[] png = Files.readAllBytes(asPng.toPath());
                assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, Arrays.copyOf(png, 4));
                assertNotNull("echtes PNG", ImageIO.read(asPng));
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    /**
     * draw.io: Umschalter „Gerendert“/„Original“. Standard ist die gerenderte Zeichnung; „Original“ lädt nur die
     * Vorschau ({@code &view=original}); scheitert sie (Einmalcode-Abfrage), sofort zurück auf „Gerendert“ und der
     * Knopf nennt die nötige Anmeldung. Speichern richtet sich nach dem gezeigten Inhalt.
     */
    @Test
    public void drawioDiagramsSwitchBetweenRenderedAndOriginal() throws Exception {
        final byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"30\">"
                + "<rect width=\"40\" height=\"30\" fill=\"red\"/></svg>").getBytes(StandardCharsets.UTF_8);
        final String drawing = BASE + "download/attachments/42/Bild%20A.png?version=3&api=v2#drawio=Bild%20A&page=42"
                + "&revision=3";
        final List<String> requested = new java.util.ArrayList<String>();
        final boolean[] otp = {false};
        ReaderImageLoader loader = new ReaderImageLoader() {
            @Override
            public void load(ReaderImage image, java.util.function.Consumer<byte[]> done) {
                throw new AssertionError("nur die Variante mit Grund wird benutzt");
            }

            @Override
            public void load(ReaderImage image, java.util.function.Consumer<byte[]> done,
                             java.util.function.Consumer<String> failed) {
                requested.add(image.src());
                if (!image.src().endsWith("&view=original")) {
                    done.accept(image.src().equals(drawing) ? svg : png());
                } else if (otp[0]) {
                    failed.accept("Bild: erwartet Bild, erhalten text/html [Seite: TWO_FACTOR „Einmalcode“]");
                } else {
                    done.accept(png());
                }
            }
        };
        final ImageSlideshowPanel[] holder = new ImageSlideshowPanel[1];
        onEdt(() -> holder[0] = new ImageSlideshowPanel(new ReaderImageStore(Arrays.asList(
                new ReaderImage(drawing, drawing, "Bild A"), new ReaderImage(A, A, "Foto")), loader), 0));
        final ImageSlideshowPanel show = holder[0];
        onEdt(() -> {
            assertTrue("Umschalter bei draw.io", show.drawioToggleVisible());
            assertFalse("Standard: gerendert", show.isShowingDrawioOriginal());
            assertEquals(Arrays.asList(drawing), requested);
            assertEquals("Bild A.svg", ImageSlideshowPanel.fileName(show.current(), show.loadedCurrent()));

            show.showDrawio(true);
            assertTrue(show.isShowingDrawioOriginal());
            assertEquals(drawing + "&view=original", requested.get(requested.size() - 1));
            assertTrue(show.showsPicture());
            assertEquals("Bild A.png", ImageSlideshowPanel.fileName(show.current(), show.loadedCurrent()));

            show.next();
            assertFalse("kein Umschalter bei gewöhnlichen Bildern", show.drawioToggleVisible());
            show.previous();
            assertTrue("die Wahl gilt weiter", show.isShowingDrawioOriginal());

            show.showDrawio(false);
            assertFalse(show.isShowingDrawioOriginal());
            assertEquals("Bild A.svg", ImageSlideshowPanel.fileName(show.current(), show.loadedCurrent()));
        });

        otp[0] = true;
        final ImageSlideshowPanel[] fresh = new ImageSlideshowPanel[1];
        onEdt(() -> fresh[0] = new ImageSlideshowPanel(new ReaderImageStore(Arrays.asList(
                new ReaderImage(drawing, drawing, "Bild A")), loader), 0));
        onEdt(() -> {
            fresh[0].showDrawio(true);
            assertFalse("zurück auf Gerendert", fresh[0].isShowingDrawioOriginal());
            assertTrue(fresh[0].showsPicture());
            assertEquals("Original (Anmeldung nötig)", fresh[0].originalModeText());
            assertEquals("Bild A.svg", ImageSlideshowPanel.fileName(fresh[0].current(), fresh[0].loadedCurrent()));
        });
    }

    /**
     * Mit Einmalcode-Weg: freigeschaltete Quelle zeigt das Original zuerst; ist die Sitzung abgelaufen, wird nicht
     * gefragt, sondern gerendert gezeigt; erst die ausdrückliche Wahl von „Original“ bringt die Abfrage, mit
     * „Zurück zu Gerendert“.
     */
    @Test
    public void originalIsPreferredWhenUnlockedAndTheCodeIsAskedOnlyOnExplicitChoice() throws Exception {
        final byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"30\">"
                + "<rect width=\"40\" height=\"30\" fill=\"red\"/></svg>").getBytes(StandardCharsets.UTF_8);
        final String drawing = BASE + "download/attachments/42/Bild%20A.png?version=3&api=v2#drawio=Bild%20A&page=42"
                + "&revision=3";
        final boolean[] unlocked = {true};
        final boolean[] expired = {false};
        final ReaderSignIn challenge = new ReaderSignIn() {
            public String service() {
                return "Confluence";
            }

            public String message() {
                return "Code per E-Mail";
            }

            public String error() {
                return "";
            }

            public boolean resendAvailable() {
                return true;
            }

            public void submit(char[] code, java.util.function.Consumer<ReaderSignIn> next,
                               java.util.function.Consumer<String> failed) {
                next.accept(null);
            }

            public void resend(java.util.function.Consumer<ReaderSignIn> next,
                               java.util.function.Consumer<String> failed) {
                next.accept(this);
            }
        };
        final ReaderImageLoader loader = new ReaderImageLoader() {
            @Override
            public void load(ReaderImage image, java.util.function.Consumer<byte[]> done) {
                throw new AssertionError("nur die Variante mit Einmalcode wird benutzt");
            }

            @Override
            public void load(ReaderImage image, java.util.function.Consumer<byte[]> done,
                             java.util.function.Consumer<String> failed,
                             java.util.function.Consumer<ReaderSignIn> signIn) {
                if (!image.src().endsWith("&view=original")) {
                    done.accept(svg);
                } else if (expired[0]) {
                    signIn.accept(challenge);
                } else {
                    done.accept(png());
                }
            }

            @Override
            public boolean downloadsUnlocked() {
                return unlocked[0];
            }
        };
        final ImageSlideshowPanel[] holder = new ImageSlideshowPanel[1];
        onEdt(() -> holder[0] = new ImageSlideshowPanel(new ReaderImageStore(Arrays.asList(
                new ReaderImage(drawing, drawing, "Bild A")), loader), 0));
        onEdt(() -> {
            assertTrue("freigeschaltet: Original zuerst", holder[0].isShowingDrawioOriginal());
            assertEquals("Bild A.png", ImageSlideshowPanel.fileName(holder[0].current(), holder[0].loadedCurrent()));
        });

        expired[0] = true;
        onEdt(() -> holder[0] = new ImageSlideshowPanel(new ReaderImageStore(Arrays.asList(
                new ReaderImage(drawing, drawing, "Bild A")), loader), 0));
        final ImageSlideshowPanel show = holder[0];
        onEdt(() -> {
            assertFalse("abgelaufen: ohne Frage gerendert", show.isShowingDrawioOriginal());
            assertTrue(show.signInPanel() == null);
            assertTrue(show.showsPicture());
            assertEquals("Original (Anmeldung nötig)", show.originalModeText());

            show.showDrawio(true);
            assertNotNull("ausdrücklich gewählt: Abfrage an Stelle des Bildes", show.signInPanel());
            assertTrue(show.isShowingDrawioOriginal());

            show.showDrawio(false);
            assertTrue("Zurück zu Gerendert", show.signInPanel() == null);
            assertFalse(show.isShowingDrawioOriginal());
            assertTrue(show.showsPicture());
        });
    }

    // ------------------------------------------------------------------ Hilfen

    private static List<ReaderImage> images() {
        return Arrays.asList(new ReaderImage(A, A, "Erstes"), new ReaderImage(B, B, "Zweites"));
    }

    private static ImageSlideshowPanel slideshow(ReaderImageLoader loader) throws Exception {
        final ImageSlideshowPanel[] holder = new ImageSlideshowPanel[1];
        onEdt(() -> holder[0] = new ImageSlideshowPanel(new ReaderImageStore(images(), loader), 0));
        return holder[0];
    }

    private static ReaderImageLoader pngLoader() {
        return (image, done) -> done.accept(png());
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] gif(int frames) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream stream = ImageIO.createImageOutputStream(out);
        writer.setOutput(stream);
        writer.prepareWriteSequence(null);
        for (int i = 0; i < frames; i++) {
            BufferedImage frame = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
            frame.setRGB(i, 0, 0xFF0000);
            writer.writeToSequence(new IIOImage(frame, null, null), null);
        }
        writer.endWriteSequence();
        stream.close();
        return out.toByteArray();
    }

    private static Point find(ReaderHtmlView view, String src) {
        for (int y = 0; y < view.getHeight(); y += 3) {
            for (int x = 0; x < view.getWidth(); x += 3) {
                ReaderImage image = view.imageAt(new Point(x, y));
                if (image != null && image.src().equals(src)) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw ex;
        }
    }
}
