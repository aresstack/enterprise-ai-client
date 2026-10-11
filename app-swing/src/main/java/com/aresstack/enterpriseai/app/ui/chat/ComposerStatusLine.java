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
 * Die Statuszeile des Composers: die runde Plakette, mittig in einer eigenen Zeile direkt ÜBER dem Composer-Rahmen
 * (außerhalb davon), damit nichts über Wellenform oder Eingabetext liegt. Der Platz ist immer reserviert, damit der
 * Composer beim Erscheinen nicht springt. Direkt rechts neben dem Text ein dünnes ✕, das die Zeile wegklickt. Zu lange Texte enden mit „…“; den vollen Text nennt der Tooltip des Composers.
 */
final class ComposerStatusLine extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int HEIGHT = 22;
    private static final int PAD_H = 10;
    private static final int PAD_V = 2;
    private static final int GAP = 10;
    private static final int CLOSE_SIZE = 7;
    private static final int CLOSE_HIT = 16;

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
    private transient Rectangle lastPill;

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

    /** Die Plakette mittig in der Zeile; das ✕ sitzt direkt rechts neben dem Text in ihr. */
    private Rectangle pillBounds(FontMetrics metrics, String shown) {
        int width = PAD_H + metrics.stringWidth(shown) + GAP + CLOSE_SIZE + PAD_H;
        int height = metrics.getHeight() + 2 * PAD_V;
        return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
    }

    private Rectangle closeBounds() {
        Rectangle pill = lastPill;
        if (pill == null) {
            return new Rectangle();
        }
        int x = pill.x + pill.width - PAD_H - CLOSE_SIZE - (CLOSE_HIT - CLOSE_SIZE) / 2;
        return new Rectangle(x, pill.y + (pill.height - CLOSE_HIT) / 2, CLOSE_HIT, CLOSE_HIT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (text.isEmpty() || getWidth() <= 0) {
            lastPill = null;
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
            String shown = fit(text, metrics, getWidth() - 2 * PAD_H - GAP - CLOSE_SIZE - 8);
            Rectangle pill = pillBounds(metrics, shown);
            lastPill = pill;
            g2.setColor(withAlpha(ResearchUiPalette.LIGHT_CONTROL_BG, 235));
            g2.fillRoundRect(pill.x, pill.y, pill.width, pill.height, pill.height, pill.height);
            g2.setColor(withAlpha(tone.color, 120));
            g2.drawRoundRect(pill.x, pill.y, pill.width - 1, pill.height - 1, pill.height, pill.height);
            g2.setColor(tone.color);
            g2.drawString(shown, pill.x + PAD_H, pill.y + PAD_V + metrics.getAscent());

            // Dünnes Kreuz zum Wegklicken, direkt rechts neben dem Text.
            int half = CLOSE_SIZE / 2;
            int cx = pill.x + pill.width - PAD_H - half;
            int cy = pill.y + pill.height / 2;
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

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
