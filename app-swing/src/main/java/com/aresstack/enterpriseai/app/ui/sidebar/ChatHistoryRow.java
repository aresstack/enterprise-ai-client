package com.aresstack.enterpriseai.app.ui.sidebar;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Eine Zeile der Drawer-Listen (askai-java8 arch, {@code ChatHistoryRow}): zwei Zeilen, abgerundet,
 * reagiert auf Überfahren. Zeile 1: Aktivitätspunkt + Titel (13 Semi Bold) + Zeit rechts (11 Regular);
 * Zeile 2: stille Metadaten (11 Regular). Kein Dauerrahmen: transparent in Ruhe, ein sehr heller blauer
 * Hauch beim Überfahren, der helle {@link ResearchUiPalette#ACCENT_BLUE}-Hauch plus 3px Akzent links,
 * wenn gewählt. Der grüne Punkt heißt genau eines: in diesem Chat läuft gerade eine Verarbeitung.
 */
public final class ChatHistoryRow extends JComponent {

    private static final Color HOVER_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.94f);
    private static final Color SELECTED_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.88f);

    private final String title;
    private final String meta;
    private final String time;
    private final boolean busy;
    private final boolean selected;
    private final Runnable openAction;
    private final ComicPalette palette;
    private boolean hovered;

    public ChatHistoryRow(String title, String meta, String time, boolean busy, boolean selected,
                          Runnable openAction, ComicPalette palette) {
        this.title = title == null ? "" : title;
        this.meta = meta == null ? "" : meta;
        this.time = time == null ? "" : time;
        this.busy = busy;
        this.selected = selected;
        this.openAction = openAction;
        this.palette = palette == null ? ComicPalette.defaultPalette() : palette;
        setOpaque(false);
        setToolTipText(this.title);
        if (openAction != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && ChatHistoryRow.this.openAction != null) {
                    ChatHistoryRow.this.openAction.run();
                }
            }
        });
    }

    public String getTitle() {
        return title;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isBusy() {
        return busy;
    }

    /** Löst die Öffnen-Aktion aus (für Tests), sofern es eine gibt. */
    public void open() {
        if (openAction != null) {
            openAction.run();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = ResearchUiPainter.prepare(graphics);
        try {
            int radius = ResearchUiMetrics.CHAT_ROW_RADIUS;
            if (selected) {
                ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), radius, SELECTED_WASH);
                g2.setColor(ResearchUiPalette.ACCENT_BLUE);
                g2.fillRoundRect(0, 6, ResearchUiMetrics.CHAT_ROW_ACCENT_WIDTH, getHeight() - 12, 2, 2);
            } else if (hovered && openAction != null) {
                ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), radius, HOVER_WASH);
            }

            int paddingH = ResearchUiMetrics.CHAT_ROW_PADDING_H;
            int dotCenterY = 15;
            int dotX = paddingH;
            if (busy) {
                g2.setColor(palette.getAgentPetrol());
                g2.fillOval(dotX, dotCenterY - 4, 8, 8);
            }
            int textX = dotX + 8 + 8;

            g2.setFont(ResearchUiTypography.regular(11f));
            FontMetrics timeMetrics = g2.getFontMetrics();
            int timeWidth = time.isEmpty() ? 0 : timeMetrics.stringWidth(time);
            int rightEdge = getWidth() - paddingH;
            if (!time.isEmpty()) {
                g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                g2.drawString(time, rightEdge - timeWidth, dotCenterY + timeMetrics.getAscent() / 2 - 1);
            }
            int titleLimit = rightEdge - timeWidth - 8;

            g2.setFont(ResearchUiTypography.semiBold(13f));
            FontMetrics titleMetrics = g2.getFontMetrics();
            g2.setColor(palette.getInk());
            g2.drawString(ellipsize(title, titleMetrics, titleLimit - textX), textX,
                    dotCenterY + titleMetrics.getAscent() / 2 - 1);

            if (!meta.isEmpty()) {
                g2.setFont(ResearchUiTypography.regular(11f));
                FontMetrics metaMetrics = g2.getFontMetrics();
                g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                g2.drawString(ellipsize(meta, metaMetrics, rightEdge - textX), textX, getHeight() - 9);
            }
        } finally {
            g2.dispose();
        }
    }

    static String ellipsize(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth || text.isEmpty()) {
            return text;
        }
        String ellipsis = "…";
        int budget = maxWidth - metrics.stringWidth(ellipsis);
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) > budget) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(10, ResearchUiMetrics.CHAT_ROW_HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, ResearchUiMetrics.CHAT_ROW_HEIGHT);
    }
}
