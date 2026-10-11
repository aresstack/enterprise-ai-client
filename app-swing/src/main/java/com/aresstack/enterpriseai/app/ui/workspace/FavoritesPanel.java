package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.app.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.app.ui.reader.ReaderLinkRow;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Drawer-Seite „Favoriten“ (Reiter mit dem gelben Stern): die gemerkten Quellen und Dokumente mit ihrer Adresse
 * samt Präfix (z. B. {@code wiki:intranet/Handbuch}), wie die Lesezeichen in MainframeMate. Ein Klick zeigt den Favoriten
 * im Lesebereich (Doppelklick heftet an), ✕ vergisst ihn. Gemerkt wird über ★ im Lesebereich.
 */
public final class FavoritesPanel extends JPanel {

    /** Was die Seite auslöst; Aufrufe auf dem EDT. */
    public interface Actions {
        void opened(ReaderLink favorite, boolean pin);

        void removed(ReaderLink favorite);
    }

    static final String EMPTY_TEXT = "Noch keine Favoriten. ★ im Lesebereich merkt eine Quelle oder ein Dokument.";
    static final String REMOVE_TOOLTIP = "Aus den Favoriten entfernen";

    private final ComicPalette palette;
    private final JPanel list = new WidthTrackingPanel();
    private final List<ReaderLinkRow> rows = new ArrayList<ReaderLinkRow>();
    private List<ReaderLink> favorites = Collections.emptyList();
    private Actions actions;

    public FavoritesPanel(ComicPalette palette) {
        super(new BorderLayout());
        this.palette = palette;
        setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JScrollPane scroll = new ComicScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        refresh();
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    public void setFavorites(List<ReaderLink> values) {
        this.favorites = values == null ? Collections.<ReaderLink>emptyList() : new ArrayList<ReaderLink>(values);
        refresh();
    }

    public List<ReaderLink> favorites() {
        return Collections.unmodifiableList(favorites);
    }

    /** Die Zeilen in Anzeigereihenfolge (für Tests). */
    public List<ReaderLinkRow> rows() {
        return Collections.unmodifiableList(new ArrayList<ReaderLinkRow>(rows));
    }

    private void refresh() {
        list.removeAll();
        rows.clear();
        JLabel header = new JLabel("FAVORITEN");
        header.setFont(ResearchUiTypography.semiBold(11f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(14, 10, 4, 8));
        header.setAlignmentX(LEFT_ALIGNMENT);
        list.add(header);
        if (favorites.isEmpty()) {
            JLabel empty = new JLabel("<html>" + EMPTY_TEXT + "</html>");
            empty.setFont(ResearchUiTypography.regular(12f));
            empty.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 8));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            list.add(empty);
        }
        for (final ReaderLink favorite : favorites) {
            ComposerButton remove = ComposerButton.iconButton(ComposerIcons.close(), REMOVE_TOOLTIP);
            remove.addActionListener(event -> {
                if (actions != null) {
                    actions.removed(favorite);
                }
            });
            ReaderLinkRow row = new ReaderLinkRow(favorite, (link, pin) -> {
                if (actions != null) {
                    actions.opened(link, pin);
                }
            }, remove, palette);
            row.setAlignmentX(LEFT_ALIGNMENT);
            rows.add(row);
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    /** Die Liste folgt der Breite des Drawers. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
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
