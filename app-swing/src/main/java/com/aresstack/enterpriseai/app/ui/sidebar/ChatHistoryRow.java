package com.aresstack.enterpriseai.app.ui.sidebar;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Eine Zeile der Drawer-Listen (askai-java8 arch, {@code ChatHistoryRow}): zwei Zeilen, abgerundet,
 * reagiert auf Überfahren. Zeile 1: Aktivitätspunkt + Titel (13 Semi Bold) + Zeit rechts (11 Regular);
 * Zeile 2: stille Metadaten (11 Regular). Kein Dauerrahmen: transparent in Ruhe, ein sehr heller blauer
 * Hauch beim Überfahren, der helle {@link ResearchUiPalette#ACCENT_BLUE}-Hauch plus 3px Akzent links,
 * wenn gewählt. Der grüne Punkt heißt genau eines: in diesem Chat läuft gerade eine Verarbeitung. Ein
 * {@code …}-Auslöser erscheint nur beim Überfahren; er (und der Rechtsklick) öffnet das Menü, das die
 * Arbeitsfläche liefert (z. B. „Löschen“) — die Zeile selbst besitzt keine Chat-Aktionen.
 */
public final class ChatHistoryRow extends JComponent {

    /** Baut das Aktionsmenü der Zeile bei Bedarf. */
    public interface MenuSupplier {
        JPopupMenu buildMenu();
    }

    private static final Color HOVER_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.94f);
    private static final Color SELECTED_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.88f);

    private final String title;
    private final String meta;
    private final String time;
    private final boolean busy;
    private final boolean selected;
    private final Runnable openAction;
    private final MenuSupplier menuSupplier;
    private final ComicPalette palette;
    private boolean hovered;
    private boolean menuHovered;

    public ChatHistoryRow(String title, String meta, String time, boolean busy, boolean selected,
                          Runnable openAction, ComicPalette palette) {
        this(title, meta, time, busy, selected, openAction, null, palette);
    }

    /** @param menuSupplier das Aktionsmenü ({@code …} beim Überfahren, Rechtsklick) oder {@code null} */
    public ChatHistoryRow(String title, String meta, String time, boolean busy, boolean selected,
                          Runnable openAction, MenuSupplier menuSupplier, ComicPalette palette) {
        this.title = title == null ? "" : title;
        this.meta = meta == null ? "" : meta;
        this.time = time == null ? "" : time;
        this.busy = busy;
        this.selected = selected;
        this.openAction = openAction;
        this.menuSupplier = menuSupplier;
        this.palette = palette == null ? ComicPalette.defaultPalette() : palette;
        setOpaque(false);
        setToolTipText(this.title);
        if (openAction != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                menuHovered = false;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                boolean inMenu = hasMenu() && menuHit().contains(event.getPoint());
                if (inMenu != menuHovered) {
                    menuHovered = inMenu;
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showMenu(event.getX(), event.getY());
                    return;
                }
                if (hasMenu() && hovered && menuHit().contains(event.getPoint())) {
                    Rectangle hit = menuHit();
                    showMenu(hit.x, hit.y + hit.height);
                } else if (SwingUtilities.isLeftMouseButton(event) && ChatHistoryRow.this.openAction != null) {
                    ChatHistoryRow.this.openAction.run();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showMenu(event.getX(), event.getY());
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private boolean hasMenu() {
        return menuSupplier != null;
    }

    private void showMenu(int x, int y) {
        JPopupMenu menu = menuSupplier == null ? null : menuSupplier.buildMenu();
        if (menu != null && menu.getComponentCount() > 0) {
            menu.show(this, x, y);
        }
    }

    /** Das Menü der Zeile (für Tests), oder {@code null} ohne Menü. */
    public JPopupMenu menu() {
        return menuSupplier == null ? null : menuSupplier.buildMenu();
    }

    /** Die Trefferfläche des {@code …}: rechts in Zeile 1, links der Uhrzeit. */
    private Rectangle menuHit() {
        FontMetrics timeMetrics = getFontMetrics(ResearchUiTypography.regular(11f));
        int timeWidth = time.isEmpty() ? 0 : timeMetrics.stringWidth(time) + 8;
        int size = 20;
        int x = getWidth() - ResearchUiMetrics.CHAT_ROW_PADDING_H - timeWidth - size;
        return new Rectangle(x, 6, size, size);
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
            if (hasMenu() && hovered) {
                Rectangle hit = menuHit();
                if (menuHovered) {
                    g2.setColor(ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.82f));
                    g2.fillOval(hit.x, hit.y, hit.width, hit.height);
                }
                g2.setColor(ResearchUiPalette.LIGHT_CONTROL_TEXT);
                int cx = hit.x + hit.width / 2;
                int cy = hit.y + hit.height / 2;
                for (int i = -1; i <= 1; i++) {
                    g2.fillOval(cx + i * 4 - 1, cy - 1, 2, 2);
                }
                titleLimit = hit.x - 6;
            }

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
