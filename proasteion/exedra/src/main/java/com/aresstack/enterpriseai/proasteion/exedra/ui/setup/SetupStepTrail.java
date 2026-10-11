package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * Die Fortschrittsanzeige des Einrichtungsassistenten in Java2D: je Schritt ein Comic-Kreis mit Tintenkontur und
 * Nummer (erledigt: grün mit Haken, aktuell: blau), dazwischen Verbindungslinien, darunter der Name des Schritts.
 */
final class SetupStepTrail extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int DIAMETER = 26;

    private final String[] names;
    private final ComicPalette palette;
    private int current;
    private boolean[] done;

    SetupStepTrail(String[] names, ComicPalette palette) {
        this.names = names.clone();
        this.palette = palette;
        this.done = new boolean[names.length];
        setOpaque(false);
        getAccessibleContext().setAccessibleName("Fortschritt der Einrichtung");
    }

    void show(int currentStep, boolean[] completed) {
        this.current = currentStep;
        this.done = completed.clone();
        getAccessibleContext().setAccessibleDescription("Schritt " + (currentStep + 1) + " von " + names.length
                + ": " + names[currentStep]);
        repaint();
    }

    int current() {
        return current;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(names.length * 120, DIAMETER + 30);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int count = names.length;
            int slot = getWidth() / count;
            int top = 4;
            Font label = ResearchUiTypography.regular(11.5f);
            Font bold = ResearchUiTypography.semiBold(11.5f);
            for (int i = 0; i < count; i++) {
                int cx = slot * i + slot / 2;
                if (i < count - 1) {
                    g2.setColor(done[i] ? ResearchUiPalette.WAVE_GREEN : ResearchUiPalette.LIGHT_CONTROL_BORDER);
                    g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Line2D.Float(cx + DIAMETER / 2f + 4, top + DIAMETER / 2f,
                            cx + slot - DIAMETER / 2f - 4, top + DIAMETER / 2f));
                }
                Color fill = done[i] ? ResearchUiPalette.WAVE_GREEN
                        : i == current ? ResearchUiPalette.ACCENT_BLUE : Color.WHITE;
                Ellipse2D circle = new Ellipse2D.Float(cx - DIAMETER / 2f, top, DIAMETER, DIAMETER);
                g2.setColor(fill);
                g2.fill(circle);
                g2.setColor(palette.getInk());
                g2.setStroke(new BasicStroke(i == current ? 2.6f : 1.8f));
                g2.draw(circle);
                if (done[i]) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(new int[] {cx - 6, cx - 1, cx + 7}, new int[] {top + 13, top + 18, top + 8}, 3);
                } else {
                    g2.setFont(bold.deriveFont(13f));
                    g2.setColor(i == current ? Color.WHITE : palette.getInk());
                    String number = Integer.toString(i + 1);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(number, cx - fm.stringWidth(number) / 2,
                            top + (DIAMETER - fm.getHeight()) / 2 + fm.getAscent());
                }
                g2.setFont(i == current ? bold : label);
                g2.setColor(i == current ? palette.getInk() : ResearchUiPalette.LIGHT_CONTROL_TEXT);
                FontMetrics fm = g2.getFontMetrics();
                String name = names[i];
                g2.drawString(name, cx - fm.stringWidth(name) / 2, top + DIAMETER + 6 + fm.getAscent());
            }
        } finally {
            g2.dispose();
        }
    }
}
