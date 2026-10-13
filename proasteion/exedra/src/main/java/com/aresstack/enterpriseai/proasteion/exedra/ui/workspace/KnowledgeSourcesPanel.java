package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.FavoriteMarker;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.FavoriteStarButton;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLinkRow;
import com.aresstack.enterpriseai.ui.comic.control.ComicSearchBar;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Die Drawer-Seite „Wissensquellen“: oben genau ein „+ Quelle“ (wie „+ Neuer Chat“; welche Quelltypen es gibt,
 * entscheiden die angebundenen Adapter, nicht die Oberfläche), darunter je Quelle eine {@link KnowledgeSourceRow}
 * mit Häkchen, Indexstand, „Jetzt indexieren“, Bearbeiten und Entfernen, unten ein stiller Hinweis, was das
 * Häkchen bewirkt. Das Indexverzeichnis steht im Einstellungen-Dialog (Reiter „Index“). Ohne
 * {@link KnowledgeSourceActions} bleiben die Knöpfe wirkungslos.
 */
public final class KnowledgeSourcesPanel extends JPanel {

    public static final String ADD_LABEL = "+ Quelle";
    static final String SEARCH_PLACEHOLDER = "Quellen durchsuchen…";
    static final String SEARCH_TOOLTIP = "Suchtext oder direkte Adresse eingeben. Erkennt eine Quelle ihr Adressmuster, "
            + "werden nur passende angehakte Quellen durchsucht (Enter)";
    static final String EMPTY_TEXT = "Noch keine Wissensquelle. „+ Quelle“ legt eine an.";
    static final String HINT_TEXT = "<html>Der Chat durchsucht nur angehakte Quellen.</html>";

    private final ComicPalette palette;
    private final ResearchPillButton addButton;
    private final JPanel list = new WidthTrackingPanel();
    private final List<KnowledgeSourceRow> rows = new ArrayList<KnowledgeSourceRow>();
    private final List<ReaderLinkRow> hitRows = new ArrayList<ReaderLinkRow>();
    private final List<FavoriteStarButton> hitStars = new ArrayList<FavoriteStarButton>();
    private FavoriteMarker favoriteMarker;
    private ReaderLink lastFirstVisible;
    private List<KnowledgeSourceItem> items = Collections.emptyList();
    private KnowledgeSourceActions actions;
    private final ComicSearchBar searchBar;
    private SourceSearchState search;

    public KnowledgeSourcesPanel(ComicPalette palette) {
        super(new BorderLayout());
        this.palette = palette;
        setOpaque(false);
        addButton = pill(ADD_LABEL, "Eine Wissensquelle hinzufügen (Wiki, Confluence, Dateien ...)");
        JPanel addRows = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addRows.setOpaque(false);
        addRows.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 8));
        addRows.add(addButton);

        searchBar = new ComicSearchBar(SEARCH_PLACEHOLDER, SEARCH_TOOLTIP, palette);
        searchBar.addSearchAction(event -> {
            String text = searchBar.getText().trim();
            if (text.isEmpty()) {
                clearSearch();
            } else if (actions != null) {
                actions.searchRequested(text);
            }
        });
        searchBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (searchBar.getText().trim().isEmpty() && search != null) {
                    clearSearch();
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        searchBar.getTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchBar.setText("");
                    clearSearch();
                }
            }
        });
        JPanel searchRow = new JPanel(new BorderLayout());
        searchRow.setOpaque(false);
        searchRow.setBorder(BorderFactory.createEmptyBorder(ResearchUiMetrics.SLIM_BAR_TOP_GAP, 8, 4, 4));
        searchRow.add(searchBar, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(searchRow, BorderLayout.NORTH);
        north.add(addRows, BorderLayout.CENTER);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JScrollPane scroll = new ComicScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        // Beim Scrollen der Treffer ab der obersten sichtbaren Zeile weiter vorladen (MainframeMate).
        final javax.swing.JViewport viewport = scroll.getViewport();
        viewport.addChangeListener(event -> reportFirstVisibleHit(viewport.getViewRect()));

        JLabel hint = mutedText(HINT_TEXT, 6, 6);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(hint, BorderLayout.SOUTH);
        refresh();
    }

    /** Ein gedämpfter, umbrechender Hinweis in der Breite des Drawers. */
    private static JLabel mutedText(String html, int top, int bottom) {
        JLabel label = new JLabel(html);
        label.setFont(ResearchUiTypography.regular(11f));
        label.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(top, 14, bottom, 12));
        return label;
    }

    /** Die stille Abschnittsüberschrift („WISSENSQUELLEN“, Treffer der Suche). */
    private static JLabel sectionHeader(String text) {
        JLabel header = new JLabel(text);
        header.setFont(ResearchUiTypography.semiBold(11f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 8));
        return header;
    }

    private ResearchPillButton pill(String label, String tooltip) {
        ResearchPillButton pill = new ResearchPillButton(label, ResearchUiMetrics.NEW_CHAT_HEIGHT - 4,
                ResearchUiMetrics.RADIUS_CONTROL, ResearchUiMetrics.NEW_CHAT_PADDING_H - 2);
        pill.setFont(ResearchUiTypography.semiBold(12.5f));
        pill.setToolTipText(tooltip);
        pill.addActionListener(event -> {
            if (actions != null) {
                actions.addRequested();
            }
        });
        return pill;
    }

    public void setActions(KnowledgeSourceActions actions) {
        this.actions = actions;
        refresh();
    }

    /** Der Stern rechts an jedem Treffer; {@code null} = ohne Stern. */
    public void setFavoriteMarker(FavoriteMarker marker) {
        this.favoriteMarker = marker;
        refresh();
    }

    /** Nach einer Änderung der Favoriten: die Sterne der Treffer neu zeichnen. */
    public void refreshFavorites() {
        for (FavoriteStarButton star : hitStars) {
            star.refresh();
        }
    }

    /** Die Sterne der Trefferliste in Anzeigereihenfolge (für Tests). */
    public List<FavoriteStarButton> hitStars() {
        return Collections.unmodifiableList(new ArrayList<FavoriteStarButton>(hitStars));
    }

    public void setItems(List<KnowledgeSourceItem> sources) {
        this.items = sources == null ? Collections.<KnowledgeSourceItem>emptyList()
                : new ArrayList<KnowledgeSourceItem>(sources);
        refresh();
    }

    /**
     * Zeigt das Ergebnis der Suchleiste statt der Quellen (wie die globale Suche in MainframeMate, mit wenigen Details:
     * Titel, Auszug, Fundstelle); {@code null} zeigt wieder die Quellen.
     */
    public void setSearchState(SourceSearchState state) {
        this.search = state;
        refresh();
    }

    public SourceSearchState searchState() {
        return search;
    }

    public ComicSearchBar searchBar() {
        return searchBar;
    }

    private void clearSearch() {
        boolean wasSearching = search != null;
        search = null;
        refresh();
        if (wasSearching && actions != null) {
            actions.searchCleared();
        }
    }

    public List<KnowledgeSourceItem> items() {
        return Collections.unmodifiableList(items);
    }

    /** Die Zeilen in Anzeigereihenfolge (für Tests). */
    public List<KnowledgeSourceRow> rows() {
        return Collections.unmodifiableList(new ArrayList<KnowledgeSourceRow>(rows));
    }

    /** „+ Quelle“ oben. */
    public ResearchPillButton addButton() {
        return addButton;
    }

    private void refresh() {
        boolean canAdd = actions != null && actions.canAdd();
        addButton.setEnabled(canAdd);
        list.removeAll();
        rows.clear();
        if (search != null) {
            showHits(search);
            return;
        }
        JLabel header = sectionHeader("WISSENSQUELLEN");
        header.setAlignmentX(LEFT_ALIGNMENT);
        list.add(header);
        if (items.isEmpty()) {
            JLabel empty = new JLabel("<html>" + EMPTY_TEXT + "</html>");
            empty.setFont(ResearchUiTypography.regular(12f));
            empty.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 8));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            list.add(empty);
        }
        for (KnowledgeSourceItem item : items) {
            KnowledgeSourceRow row = new KnowledgeSourceRow(item, actions, palette);
            row.setAlignmentX(LEFT_ALIGNMENT);
            rows.add(row);
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    /** Meldet den obersten sichtbaren Treffer, sobald er wechselt. */
    private void reportFirstVisibleHit(Rectangle visible) {
        if (search == null || actions == null) {
            return;
        }
        for (ReaderLinkRow row : hitRows) {
            if (row.getY() + row.getHeight() > visible.y) {
                ReaderLink first = row.link();
                if (first != lastFirstVisible) {
                    lastFirstVisible = first;
                    actions.hitsScrolled(first);
                }
                return;
            }
        }
    }

    /** Die Trefferliste: Direkttreffer zuerst hervorgehoben, danach die normale Suche in denselben Quellen. */
    private void showHits(SourceSearchState state) {
        JLabel summary = sectionHeader(state.summary().toUpperCase(Locale.GERMANY));
        summary.setAlignmentX(LEFT_ALIGNMENT);
        list.add(summary);
        hitRows.clear();
        hitStars.clear();
        lastFirstVisible = null;

        if (!state.directHits().isEmpty()) {
            JLabel direct = sectionHeader("DIREKTTREFFER · ADRESSE ERKANNT");
            direct.setForeground(ResearchUiPalette.ACCENT_BLUE);
            direct.setBorder(BorderFactory.createEmptyBorder(8, 10, 1, 8));
            direct.setAlignmentX(LEFT_ALIGNMENT);
            list.add(direct);
            addHitRows(state.directHits());
        }
        if (!state.searchHits().isEmpty()) {
            if (!state.directHits().isEmpty()) {
                JLabel found = sectionHeader("SUCHERGEBNISSE");
                found.setAlignmentX(LEFT_ALIGNMENT);
                list.add(found);
            }
            addHitRows(state.searchHits());
        }
        for (String problem : state.problems()) {
            JLabel line = new JLabel(problem);
            line.setFont(ResearchUiTypography.regular(11f));
            line.setForeground(ResearchUiPalette.DANGER_RED);
            line.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 8));
            line.setAlignmentX(LEFT_ALIGNMENT);
            list.add(line);
        }
        list.add(Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    /** Fügt Treffer nach Quelle gruppiert hinzu; Direkttreffer und normale Suche rufen dies getrennt auf. */
    private void addHitRows(List<ReaderLink> hits) {
        String currentSource = null;
        for (ReaderLink hit : hits) {
            if (!hit.sourceId().equals(currentSource)) {
                currentSource = hit.sourceId();
                JLabel source = new JLabel(currentSource);
                source.setFont(ResearchUiTypography.semiBold(11.5f));
                source.setForeground(ResearchUiPalette.ACCENT_BLUE);
                source.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 8));
                source.setAlignmentX(LEFT_ALIGNMENT);
                list.add(source);
            }
            FavoriteStarButton star = FavoriteStarButton.forLink(hit, favoriteMarker);
            if (star != null) {
                hitStars.add(star);
            }
            ReaderLinkRow row = new ReaderLinkRow(hit, (link, pin) -> {
                if (actions != null) {
                    actions.linkOpened(link, pin);
                }
            }, star == null ? null : star.component(), palette);
            row.setAlignmentX(LEFT_ALIGNMENT);
            hitRows.add(row);
            list.add(row);
        }
    }

    /** Die Liste folgt der Breite des Drawers, damit lange Texte gekürzt werden und ⟳/✎ sichtbar bleiben. */
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
