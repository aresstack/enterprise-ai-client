package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Ein Farbfeld im Comic-Stil: abgerundete Fläche in der Farbe mit Tintenkontur, beim Überfahren oder mit Fokus ein
 * Akzentring. Ohne Text; die Farbe nennt der Tooltip bzw. der Name für Screenreader.
 */
final class ColorSwatch extends JButton {

    private static final int WIDTH = 34;
    private static final int HEIGHT = 24;
    private static final int SMALL = 22;
    private static final int RADIUS = 5;
    private static final int RING = 2;

    private final ComicPalette palette;
    private final int width;
    private final int height;
    private Color color;

    /** Das große Feld neben dem Hex-Wert. */
    ColorSwatch(Color color, ComicPalette palette) {
        this(color, palette, WIDTH, HEIGHT);
    }

    /** Ein kleines Feld in der Farbauswahl. */
    static ColorSwatch small(Color color, ComicPalette palette) {
        return new ColorSwatch(color, palette, SMALL, SMALL);
    }

    private ColorSwatch(Color color, ComicPalette palette, int width, int height) {
        this.palette = palette;
        this.width = width;
        this.height = height;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setColor(color);
    }

    void setColor(Color color) {
        this.color = color;
        String hex = SettingsColors.hex(color);
        setToolTipText(hex);
        getAccessibleContext().setAccessibleDescription(hex);
        repaint();
    }

    Color color() {
        return color;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = ResearchUiPainter.prepare(graphics);
        try {
            int w = getWidth();
            int h = getHeight();
            boolean hot = getModel().isRollover() || isFocusOwner();
            if (hot) {
                ResearchUiPainter.fillRound(g2, 0, 0, w, h, RADIUS + RING, ResearchUiPalette.ACCENT_BLUE);
            }
            ResearchUiPainter.fillRound(g2, RING, RING, w - 2 * RING, h - 2 * RING, RADIUS, color);
            ResearchUiPainter.strokeRound(g2, RING, RING, w - 2 * RING, h - 2 * RING, RADIUS, palette.getInk());
        } finally {
            g2.dispose();
        }
    }
}
