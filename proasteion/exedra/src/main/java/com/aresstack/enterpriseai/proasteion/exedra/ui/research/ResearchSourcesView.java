package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Der Drawer-Reiter „Quellen“: der Quellenkatalog des Projekts (aus {@code sources.tsv}) als Comic-Plaketten mit
 * Nummer, Titel und Adresse, darüber der Stand des Quellenlaufs. Ein Klick öffnet die Quelle im Lesebereich; das
 * Projekt hält nur Verweise, keine Textkopien. Nur auf dem EDT.
 */
public final class ResearchSourcesView extends JPanel {

    /** Eine Quelle für die Anzeige. */
    public static final class SourceItem {
        final int number;
        final String resourceId;
        final String title;
        final String detail;

        public SourceItem(int number, String resourceId, String title, String detail) {
            this.number = number;
            this.resourceId = resourceId == null ? "" : resourceId;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final String EMPTY = "Noch keine Quelle übernommen. Wähle die Phase „Quellen“ und starte die "
            + "Recherche unten mit Play, wenn das Konzept steht.";

    private final JLabel summary = new JLabel(" ");
    private final ListPanel list = new ListPanel();
    private Consumer<String> openHandler;
    private List<SourceItem> items = Collections.emptyList();

    public ResearchSourcesView() {
        super(new BorderLayout());
        setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        setOpaque(true);
        summary.setFont(ResearchUiTypography.regular(12f));
        summary.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        summary.setBorder(BorderFactory.createEmptyBorder(8, 12, 6, 12));
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        add(summary, BorderLayout.NORTH);
        add(new ComicScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        render(null, "");
    }

    /** Die Play-Aktion des Reiters (unten mittig). */
    public void setPlayBar(ResearchPlayBar bar) {
        add(bar, BorderLayout.SOUTH);
        revalidate();
    }

    /** Wer eine Quelle öffnet (Ressourcen-ID). */
    public void setOpenHandler(Consumer<String> handler) {
        this.openHandler = handler;
    }

    /** Neuer Stand: die Quellen und eine Zeile zum Lauf (gelesene Seiten, übernommene Quellen, Endegrund). */
    public void render(List<SourceItem> sources, String runSummary) {
        items = sources == null ? Collections.<SourceItem>emptyList() : new ArrayList<SourceItem>(sources);
        summary.setText(runSummary == null || runSummary.trim().isEmpty()
                ? (items.isEmpty() ? EMPTY : items.size() + " Quellen übernommen.") : runSummary);
        list.removeAll();
        for (SourceItem item : items) {
            list.add(new SourcePlate(item));
            list.add(Box.createVerticalStrut(6));
        }
        list.revalidate();
        list.repaint();
    }

    /** Anzahl der angezeigten Quellen (Tests). */
    public int sourceCount() {
        return items.size();
    }

    /** Öffnet die Quelle an Position {@code index} wie ein Klick (Tests, Tastatur). */
    public void open(int index) {
        if (openHandler != null && index >= 0 && index < items.size()) {
            openHandler.accept(items.get(index).resourceId);
        }
    }

    /** Text der Kopfzeile (Tests). */
    public String summaryText() {
        return summary.getText();
    }

    private final class SourcePlate extends JComponent {
        private static final int HEIGHT = 46;
        private final SourceItem item;
        private boolean hover;

        SourcePlate(final SourceItem item) {
            this.item = item;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(item.detail.isEmpty() ? item.title : item.title + " - " + item.detail);
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (openHandler != null) {
                        openHandler.accept(item.resourceId);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                ComicPalette palette = ComicPalette.defaultPalette();
                Color ink = palette.getInk();
                int width = getWidth() - 2;
                g2.setColor(hover ? new Color(0xFFF6D6) : Color.WHITE);
                g2.fillRoundRect(1, 1, width, HEIGHT - 2, 12, 12);
                g2.setColor(hover ? ink : ResearchUiPalette.LIGHT_CONTROL_BORDER);
                g2.setStroke(new BasicStroke(hover ? 1.8f : 1.4f));
                g2.drawRoundRect(1, 1, width, HEIGHT - 2, 12, 12);
                // Nummer als gelbe Marke wie die Belege im Dokument
                String number = String.valueOf(item.number);
                g2.setFont(ResearchUiTypography.semiBold(12f));
                FontMetrics badge = g2.getFontMetrics();
                int badgeWidth = Math.max(22, badge.stringWidth(number) + 10);
                g2.setColor(palette.getAccentYellow());
                g2.fillRoundRect(8, 12, badgeWidth, 20, 10, 10);
                g2.setColor(ink);
                g2.drawString(number, 8 + (badgeWidth - badge.stringWidth(number)) / 2,
                        12 + (20 + badge.getAscent() - badge.getDescent()) / 2);
                int textX = 16 + badgeWidth;
                int textWidth = width - textX - 8;
                g2.setFont(ResearchUiTypography.semiBold(13f));
                FontMetrics title = g2.getFontMetrics();
                g2.drawString(fit(item.title, title, textWidth), textX, 19);
                g2.setFont(ResearchUiTypography.regular(11.5f));
                FontMetrics detail = g2.getFontMetrics();
                g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                g2.drawString(fit(item.detail, detail, textWidth), textX, 36);
            } finally {
                g2.dispose();
            }
        }
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (text == null || metrics.stringWidth(text) <= width) {
            return text == null ? "" : text;
        }
        String shortened = text;
        while (shortened.length() > 1 && metrics.stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    /** Folgt der Breite des Viewports, damit die Plaketten mitwachsen. */
    private static final class ListPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
