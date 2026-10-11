package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Die Statuszeile des Composers als Overlay: eine volle Zeile über der Fußzeile, zentriert, auf einer hellen Plakette,
 * damit sie über Text und Wellenform lesbar bleibt (statt in den Leerraum zwischen den Fußzeilen-Knöpfen gequetscht).
 * Zu lange Texte enden mit „…“; den vollen Text nennt der Tooltip des Composers bzw. die Fehlermeldung. Klicks gehen
 * an den Editor durch.
 */
final class ComposerStatusLine extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int PAD_H = 10;
    private static final int PAD_V = 2;
    private static final int BOTTOM = 2;

    /** Ton der Zeile: Hinweis, laufendes Diktat, Fehler. */
    enum Tone {
        MUTED(ResearchUiPalette.LIGHT_TEXT_MUTED),
        DICTATION(ResearchUiPalette.RECORDING_ORANGE),
        ERROR(ResearchUiPalette.DANGER_RED);

        final Color color;

        Tone(Color color) {
            this.color = color;
        }
    }

    private String text = "";
    private Tone tone = Tone.MUTED;

    ComposerStatusLine() {
        setOpaque(false);
    }

    void show(String value, Tone newTone) {
        String next = value == null ? "" : value.trim();
        Tone nextTone = newTone == null ? Tone.MUTED : newTone;
        if (!next.equals(text) || nextTone != tone) {
            text = next;
            tone = nextTone;
            repaint();
        }
    }

    String text() {
        return text;
    }

    Tone tone() {
        return tone;
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (text.isEmpty() || getWidth() <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font base = getFont() != null ? getFont() : UIManager.getFont("Label.font");
            if (base != null) {
                g2.setFont(base.deriveFont(Font.BOLD, 11.5f));
            }
            FontMetrics metrics = g2.getFontMetrics();
            String shown = fit(text, metrics, getWidth() - 2 * PAD_H - 8);
            int textWidth = metrics.stringWidth(shown);
            int height = metrics.getHeight() + 2 * PAD_V;
            int width = textWidth + 2 * PAD_H;
            int x = (getWidth() - width) / 2;
            int y = getHeight() - height - BOTTOM;
            g2.setColor(withAlpha(ResearchUiPalette.LIGHT_CONTROL_BG, 235));
            g2.fillRoundRect(x, y, width, height, height, height);
            g2.setColor(withAlpha(tone.color, 120));
            g2.drawRoundRect(x, y, width - 1, height - 1, height, height);
            g2.setColor(tone.color);
            g2.drawString(shown, x + PAD_H, y + PAD_V + metrics.getAscent());
        } finally {
            g2.dispose();
        }
    }

    /** Kürzt mit „…“ auf die verfügbare Breite. */
    static String fit(String value, FontMetrics metrics, int available) {
        if (metrics.stringWidth(value) <= available) {
            return value;
        }
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && metrics.stringWidth(value.substring(0, end) + ellipsis) > available) {
            end--;
        }
        return value.substring(0, end).trim() + ellipsis;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
