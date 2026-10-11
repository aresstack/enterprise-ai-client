package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Die Statuszeile des Composers: eine eigene Zeile direkt ÜBER dem Composer-Rahmen (außerhalb davon), mit fester Höhe
 * und linksbündigem Text, damit nichts über Wellenform oder Eingabetext liegt und beim Sprechen nichts hin- und
 * herwandert. Der Platz ist immer reserviert, damit der Composer beim Erscheinen nicht springt. Rechts ein dünnes ✕,
 * das die Zeile wegklickt. Zu lange Texte enden mit „…“; den vollen Text nennt der Tooltip des Composers.
 */
final class ComposerStatusLine extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int HEIGHT = 20;
    private static final int PAD_LEFT = 12;
    private static final int CLOSE_SIZE = 8;
    private static final int CLOSE_HIT = 16;
    private static final int CLOSE_RIGHT = 10;

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
    private Runnable onClose;
    private boolean closeHover;

    ComposerStatusLine() {
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!text.isEmpty() && closeBounds().contains(event.getPoint()) && onClose != null) {
                    onClose.run();
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(!text.isEmpty() && closeBounds().contains(event.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                updateHover(false);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Was das ✕ tut (die Zeile schließen); ohne Aktion bleibt das ✕ ohne Wirkung. */
    void onClose(Runnable action) {
        onClose = action;
    }

    void show(String value, Tone newTone) {
        String next = value == null ? "" : value.trim();
        Tone nextTone = newTone == null ? Tone.MUTED : newTone;
        if (!next.equals(text) || nextTone != tone) {
            text = next;
            tone = nextTone;
            if (text.isEmpty()) {
                updateHover(false);
            }
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
    public Dimension getPreferredSize() {
        return new Dimension(0, HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    private void updateHover(boolean hover) {
        if (hover != closeHover) {
            closeHover = hover;
            setCursor(hover ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            setToolTipText(hover ? "Meldung schließen" : null);
            repaint();
        }
    }

    private Rectangle closeBounds() {
        int x = getWidth() - CLOSE_RIGHT - CLOSE_HIT;
        return new Rectangle(x, (getHeight() - CLOSE_HIT) / 2, CLOSE_HIT, CLOSE_HIT);
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
            Rectangle close = closeBounds();
            String shown = fit(text, metrics, close.x - PAD_LEFT - 8);
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setColor(tone.color);
            g2.drawString(shown, PAD_LEFT, baseline);

            // Dünnes Kreuz zum Wegklicken, rechts in der Ecke.
            int cx = close.x + close.width / 2;
            int cy = close.y + close.height / 2;
            int half = CLOSE_SIZE / 2;
            g2.setColor(closeHover ? ResearchUiPalette.TEXT_DARK : ResearchUiPalette.LIGHT_TEXT_MUTED);
            g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - half, cy - half, cx + half, cy + half);
            g2.drawLine(cx - half, cy + half, cx + half, cy - half);
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
}
