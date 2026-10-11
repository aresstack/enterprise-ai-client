package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.SvgImageRenderer;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Die Bilder eines Lese-Reiters: lädt jedes höchstens einmal über den {@link ReaderImageLoader}, dekodiert es (SVG
 * über Batik gerastert, GIF als erstes Bild) und teilt es zwischen Inline-Ansicht und Diashow; ebenso die
 * Beschreibung (je Bild einmal angefragt). Nur auf dem EDT.
 */
public final class ReaderImageStore {

    private static final SvgImageRenderer SVG = new SvgImageRenderer();
    private static final int SVG_WIDTH = 1200;

    private final List<ReaderImage> images;
    private final ReaderImageLoader loader;
    private final Map<String, Loaded> loaded = new HashMap<String, Loaded>();
    private final Map<String, List<Consumer<Loaded>>> waiting = new HashMap<String, List<Consumer<Loaded>>>();
    private final Map<String, ReaderImageDescription> descriptions = new HashMap<String, ReaderImageDescription>();
    private final Map<String, List<Consumer<ReaderImageDescription>>> describing =
            new HashMap<String, List<Consumer<ReaderImageDescription>>>();

    public ReaderImageStore(List<ReaderImage> images, ReaderImageLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        this.images = images == null ? Collections.<ReaderImage>emptyList()
                : Collections.unmodifiableList(new ArrayList<ReaderImage>(images));
        this.loader = loader;
    }

    /** Die Bilder der Seite in Dokumentreihenfolge. */
    public List<ReaderImage> images() {
        return images;
    }

    /** Das Bild mit dieser Adresse oder {@code null}. */
    public ReaderImage bySrc(String src) {
        for (ReaderImage image : images) {
            if (image.src().equals(src)) {
                return image;
            }
        }
        return null;
    }

    /** Das schon geladene Bild oder {@code null}. */
    public Loaded loaded(ReaderImage image) {
        return loaded.get(image.key());
    }

    /** Lädt das Bild (einmal) und meldet es; ist es schon da, sofort. */
    public void request(final ReaderImage image, Consumer<Loaded> done) {
        Loaded known = loaded.get(image.key());
        if (known != null) {
            done.accept(known);
            return;
        }
        List<Consumer<Loaded>> queue = waiting.get(image.key());
        if (queue != null) {
            queue.add(done);
            return;
        }
        queue = new ArrayList<Consumer<Loaded>>();
        queue.add(done);
        waiting.put(image.key(), queue);
        loader.load(image, bytes -> finishLoading(image, Loaded.of(bytes)),
                reason -> finishLoading(image, Loaded.failed(reason)));
    }

    private void finishLoading(ReaderImage image, Loaded result) {
        loaded.put(image.key(), result);
        List<Consumer<Loaded>> listeners = waiting.remove(image.key());
        if (listeners != null) {
            for (Consumer<Loaded> listener : listeners) {
                listener.accept(result);
            }
        }
    }

    /** Die schon vorliegende Beschreibung oder {@code null}. */
    public ReaderImageDescription description(ReaderImage image) {
        return descriptions.get(image.key());
    }

    /**
     * Beschreibt das Bild (einmal je Reiter; Fehlschläge werden beim nächsten Mal neu versucht). Das Bild wird dafür
     * geladen, falls es noch nicht da ist; ein SVG geht gerastert als PNG an das Modell.
     */
    public void describe(final ReaderImage image, Consumer<ReaderImageDescription> done) {
        ReaderImageDescription known = descriptions.get(image.key());
        if (known != null) {
            done.accept(known);
            return;
        }
        List<Consumer<ReaderImageDescription>> queue = describing.get(image.key());
        if (queue != null) {
            queue.add(done);
            return;
        }
        queue = new ArrayList<Consumer<ReaderImageDescription>>();
        queue.add(done);
        describing.put(image.key(), queue);
        request(image, loaded -> {
            if (!loaded.ok()) {
                finishDescription(image, ReaderImageDescription.unavailable("Das Bild ist nicht ladbar."));
                return;
            }
            loader.describe(image, loaded.visionMediaType(), loaded.visionBytes(),
                    result -> finishDescription(image, result));
        });
    }

    private void finishDescription(ReaderImage image, ReaderImageDescription result) {
        if (result.available()) {
            descriptions.put(image.key(), result);
        }
        List<Consumer<ReaderImageDescription>> listeners = describing.remove(image.key());
        if (listeners != null) {
            for (Consumer<ReaderImageDescription> listener : listeners) {
                listener.accept(result);
            }
        }
    }

    /**
     * Ein geladenes Bild: Original-Bytes (zum Speichern), dekodiertes Bild (erstes Bild eines GIFs, SVG gerastert)
     * und ob es ein animiertes GIF ist (die Diashow spielt es ab, wie MainframeMate); {@link #ok()} ist
     * {@code false}, wenn es nicht ladbar war.
     */
    public static final class Loaded {
        /** Text, wenn die Quelle keinen Grund nennt. */
        static final String FAILED_TEXT = "Bild konnte nicht geladen werden";

        private final byte[] bytes;
        private final BufferedImage image;
        private final boolean svg;
        private final String failure;

        private Loaded(byte[] bytes, BufferedImage image, boolean svg, String failure) {
            this.bytes = bytes;
            this.image = image;
            this.svg = svg;
            this.failure = image != null ? null : failure == null || failure.trim().isEmpty() ? FAILED_TEXT : failure;
        }

        static Loaded of(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return new Loaded(null, null, false, bytes == null ? null : "Bild: leere Antwort (0 Bytes)");
            }
            boolean svg = isSvg(bytes);
            BufferedImage image = svg ? renderSvg(bytes) : decode(bytes);
            return new Loaded(image == null ? null : bytes, image, svg,
                    "Bilddaten nicht lesbar (" + bytes.length + " Bytes, " + (svg ? "SVG nicht darstellbar)"
                            : "kein PNG, JPEG, GIF oder SVG)"));
        }

        /** Nicht geladen, mit dem Grund der Quelle ({@code null}: unbekannt). */
        static Loaded failed(String reason) {
            return new Loaded(null, null, false, reason);
        }

        public boolean ok() {
            return image != null;
        }

        /** Warum das Bild fehlt (für die Oberfläche); {@code null}, wenn es geladen ist. */
        public String failure() {
            return failure;
        }

        public byte[] bytes() {
            return bytes;
        }

        public BufferedImage image() {
            return image;
        }

        public boolean isAnimatedGif() {
            return bytes != null && isGif(bytes) && ReaderImageStore.isAnimatedGif(bytes);
        }

        /** Der Medientyp, den das Bildmodell bekommt: PNG, JPEG oder GIF wie geladen, alles andere als PNG. */
        String visionMediaType() {
            String sniffed = svg ? null : sniff(bytes);
            return sniffed == null ? "image/png" : sniffed;
        }

        byte[] visionBytes() {
            if (!svg && sniff(bytes) != null) {
                return bytes;
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "png", png);
            } catch (IOException e) {
                return bytes;
            }
            return png.toByteArray();
        }
    }

    private static String sniff(byte[] data) {
        if (data.length > 3 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        return isGif(data) ? "image/gif" : null;
    }

    private static boolean isGif(byte[] data) {
        return data.length > 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }

    private static boolean isSvg(byte[] data) {
        int length = Math.min(data.length, 2048);
        String head = new String(data, 0, length, StandardCharsets.UTF_8);
        return head.contains("<svg") && SvgImageRenderer.looksLikeSvg(new String(data, StandardCharsets.UTF_8));
    }

    /** SVG über Batik wie MainframeMate {@code SvgRenderer}, auf weißem Grund (Diagramme sind oft transparent). */
    private static BufferedImage renderSvg(byte[] data) {
        BufferedImage rendered;
        try {
            rendered = SVG.render(new String(data, StandardCharsets.UTF_8), SVG_WIDTH);
        } catch (RuntimeException e) {
            return null;
        }
        if (rendered == null) {
            return null;
        }
        BufferedImage opaque = new BufferedImage(rendered.getWidth(), rendered.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = opaque.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, opaque.getWidth(), opaque.getHeight());
            g2.drawImage(rendered, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return opaque;
    }

    /**
     * Dekodiert wie MainframeMate {@code ImageThumbnailPanel.decodeToBufferedImage}: {@link ImageIO}, sonst (etwa bei
     * manchen GIFs) über {@link ImageIcon} und {@link PixelGrabber}; animierte GIFs liefern ihr erstes Bild.
     */
    static BufferedImage decode(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                return image;
            }
        } catch (Exception ignored) {
            // weiter mit dem Toolkit-Weg
        }
        ImageIcon icon = new ImageIcon(data);
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int[] pixels = new int[w * h];
        PixelGrabber grabber = new PixelGrabber(icon.getImage(), 0, 0, w, h, pixels, 0, w);
        try {
            grabber.grabPixels();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, w, h, pixels, 0, w);
        return image;
    }

    /**
     * Aus MainframeMate {@code ImageStripPanel.isAnimatedGif}: mehr als ein Image-Descriptor-Block. Zwei Fehler des
     * Vorbilds behoben (dort wurde ein mit ImageIO geschriebenes animiertes GIF nie erkannt): der Lauf beginnt hinter
     * Logical Screen Descriptor und globaler Farbtabelle statt mitten darin, und der Image Descriptor ist 10 statt 9
     * Bytes lang (das gepackte Feld ist sein letztes Byte).
     */
    static boolean isAnimatedGif(byte[] data) {
        if (data.length < 13) {
            return false;
        }
        int imageCount = 0;
        int i = 13; // "GIF89a" / "GIF87a" und Logical Screen Descriptor
        int screen = data[10] & 0xFF;
        if ((screen & 0x80) != 0) {
            i += 3 * (1 << ((screen & 0x07) + 1)); // globale Farbtabelle
        }
        while (i < data.length) {
            int b = data[i] & 0xFF;
            if (b == 0x2C) { // Image Descriptor
                imageCount++;
                if (imageCount > 1) {
                    return true;
                }
                i += 10;
                if (i > data.length) {
                    break;
                }
                int packed = data[i - 1] & 0xFF;
                if ((packed & 0x80) != 0) {
                    i += 3 * (1 << ((packed & 0x07) + 1));
                }
                if (i >= data.length) {
                    break;
                }
                i++; // LZW minimum code size
                i = skipSubBlocks(data, i);
            } else if (b == 0x21) { // Extension
                i = skipSubBlocks(data, i + 2);
            } else if (b == 0x3B) { // Trailer
                break;
            } else {
                i++;
            }
        }
        return false;
    }

    private static int skipSubBlocks(byte[] data, int start) {
        int i = start;
        while (i < data.length) {
            int blockSize = data[i] & 0xFF;
            i++;
            if (blockSize == 0) {
                break;
            }
            i += blockSize;
        }
        return i;
    }
}
