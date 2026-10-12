package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Der Zauberer des Einrichtungsassistenten: ein Lückenfüller im freien Platz unter den Feldern eines Schritts. Jeder
 * Schritt legt seinen Platz fest ({@link Placement}: unten links, mittig oder rechts) und seine Höhe; die Breite folgt
 * dem Seitenverhältnis. Passt er dort nicht vollständig in die Fläche, weil die Felder des Schritts zu weit
 * herunterreichen oder eine Fehlermeldung den Platz nimmt, erscheint er gar nicht; abgeschnitten wird er nie. Die
 * Komponente beansprucht selbst keinen Platz ({@link #getPreferredSize()} ist leer), sie bekommt nur, was der Schritt
 * übrig lässt, und liegt daher nie über Feldern oder Texten.
 */
final class SetupWizardMascot extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Wo der Zauberer im freien Platz steht; immer auf dem unteren Rand. */
    enum Placement {
        LEFT, CENTER, RIGHT
    }

    /** Das allgemeine Bild neben dieser Klasse; Rückfall, wenn ein Schrittbild fehlt. */
    static final String RESOURCE = "wizard/wizard-00-mascot.png";
    /** Vorgabe für den Mindestabstand zum letzten Feld darüber. */
    static final int DEFAULT_GAP = 12;
    /** Vorgabe für den Abstand zum linken bzw. rechten Rand bei {@link Placement#LEFT}/{@link Placement#RIGHT}. */
    static final int DEFAULT_SIDE_INSET = 24;

    private final transient Art art;
    private final int height;
    private final Placement placement;
    private final int gap;
    private final int sideInset;

    /**
     * @param art       das Bild; {@code null} = kein Zauberer
     * @param height    Höhe in logischen Pixeln
     * @param placement Platz im freien Bereich
     * @param gap       Mindestabstand zum oberen Rand der Fläche (zu den Feldern darüber)
     * @param sideInset Abstand zum Seitenrand (links/rechts)
     */
    SetupWizardMascot(Art art, int height, Placement placement, int gap, int sideInset) {
        if (height <= 0 || gap < 0 || sideInset < 0 || placement == null) {
            throw new IllegalArgumentException("height must be positive, gaps must not be negative, placement set");
        }
        this.art = art;
        this.height = height;
        this.placement = placement;
        this.gap = gap;
        this.sideInset = sideInset;
        setOpaque(false);
        setFocusable(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, 0);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, 0);
    }

    Placement placement() {
        return placement;
    }

    /** Breite und Höhe des Zauberers, unabhängig vom Platz; leer ohne Bild. */
    Dimension figureSize() {
        if (art == null) {
            return new Dimension(0, 0);
        }
        return new Dimension(art.widthFor(height), height);
    }

    /** {@code true}, wenn der Zauberer an seinem Platz vollständig in die Fläche passt und gezeichnet wird. */
    boolean fits() {
        if (art == null) {
            return false;
        }
        Dimension figure = figureSize();
        return figure.width + 2 * sideInset <= getWidth() && figure.height + gap <= getHeight();
    }

    /** Wo der Zauberer steht, in Koordinaten dieser Komponente (auch wenn er nicht passt). */
    Rectangle figureBounds() {
        Dimension figure = figureSize();
        int x;
        switch (placement) {
            case LEFT:
                x = sideInset;
                break;
            case RIGHT:
                x = getWidth() - sideInset - figure.width;
                break;
            default:
                x = (getWidth() - figure.width) / 2;
        }
        return new Rectangle(x, getHeight() - figure.height, figure.width, figure.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!fits()) {
            return;
        }
        Rectangle figure = figureBounds();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            AffineTransform device = g2.getTransform();
            int pixelWidth = (int) Math.round(figure.width * Math.abs(device.getScaleX()));
            int pixelHeight = (int) Math.round(figure.height * Math.abs(device.getScaleY()));
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(art.scaled(Math.max(1, pixelWidth), Math.max(1, pixelHeight)), figure.x, figure.y,
                    figure.width, figure.height, null);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Das geladene Bild samt der zuletzt verkleinerten Fassung; ein Exemplar je Assistent, geteilt von allen
     * Schritten (alle zeigen den Zauberer in derselben Größe).
     */
    static final class Art {
        private final BufferedImage original;
        private BufferedImage scaled;

        Art(BufferedImage original) {
            if (original == null) {
                throw new IllegalArgumentException("original must not be null");
            }
            this.original = original;
        }

        /** Lädt das allgemeine Bild; {@code null}, wenn es fehlt oder nicht lesbar ist (dann kein Zauberer). */
        static Art load() {
            return load(RESOURCE);
        }

        /**
         * Lädt ein Bild relativ zu dieser Klasse aus dem Klassenpfad; {@code null}, wenn es fehlt oder nicht lesbar
         * ist.
         */
        static Art load(String resource) {
            InputStream in = SetupWizardMascot.class.getResourceAsStream(resource);
            if (in == null) {
                return null;
            }
            try {
                BufferedImage image = ImageIO.read(in);
                return image == null ? null : new Art(image);
            } catch (IOException e) {
                return null;
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nur gelesen
                }
            }
        }

        int widthFor(int height) {
            return (int) Math.round((double) original.getWidth() * height / original.getHeight());
        }

        /** Die Fassung in Gerätepixeln; schrittweise halbiert, damit das Verkleinern nicht pixelig wird. */
        BufferedImage scaled(int width, int height) {
            if (scaled != null && scaled.getWidth() == width && scaled.getHeight() == height) {
                return scaled;
            }
            BufferedImage current = original;
            int w = current.getWidth();
            int h = current.getHeight();
            do {
                w = Math.max(width, w / 2);
                h = Math.max(height, h / 2);
                if (w < width * 2 || h < height * 2) {
                    w = width;
                    h = height;
                }
                current = resize(current, w, h);
            } while (w != width || h != height);
            scaled = current;
            return scaled;
        }

        private static BufferedImage resize(BufferedImage source, int width, int height) {
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(source, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            return target;
        }
    }
}
