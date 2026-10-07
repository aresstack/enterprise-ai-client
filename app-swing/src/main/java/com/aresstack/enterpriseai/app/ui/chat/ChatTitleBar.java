package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.paint.ComicImpactPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Die Titelzeile der Shell: der Titel auf der gelb-orangen Comic-Impact-Platte (derselbe Painter wie AskAIs
 * Menü-Hover), darunter eine Tintenlinie über die volle Breite. Ruhige Fläche, ein einziger Akzent.
 */
public final class ChatTitleBar extends JComponent {

    private static final int HEIGHT = 46;
    private static final int PLATE_PADDING_H = 18;
    private static final int LEFT_INSET = 10;

    private final ComicPalette palette;
    private final ComicImpactPainter impactPainter;
    private String title;

    public ChatTitleBar(String title, ComicPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        this.palette = palette;
        this.impactPainter = new ComicImpactPainter(palette);
        this.title = title == null ? "" : title;
        setOpaque(true);
        setBackground(palette.getSurface());
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension(LEFT_INSET + metrics.stringWidth(title) + 2 * PLATE_PADDING_H + 20, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            int plateWidth = metrics.stringWidth(title) + 2 * PLATE_PADDING_H;
            int plateHeight = getHeight() - 8;
            Graphics2D plate = (Graphics2D) g2.create(LEFT_INSET, 3, plateWidth, plateHeight);
            try {
                impactPainter.paint(plate, plateWidth, plateHeight);
            } finally {
                plate.dispose();
            }
            g2.setColor(palette.getInk());
            int textY = 3 + (plateHeight - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
            g2.drawString(title, LEFT_INSET + PLATE_PADDING_H - 1, textY);

            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        } finally {
            g2.dispose();
        }
    }
}
