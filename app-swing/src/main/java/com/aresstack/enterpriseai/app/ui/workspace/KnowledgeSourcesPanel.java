package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Drawer-Seite „Wissensquellen“: oben „+ MediaWiki“ und „+ Confluence“ (wie „+ Neuer Chat“), darunter je
 * Quelle eine {@link KnowledgeSourceRow} mit Häkchen, Indexstand, „Jetzt indexieren“ und Bearbeiten, unten ein
 * stiller Hinweis, was das Häkchen bewirkt. Ohne {@link KnowledgeSourceActions} bleiben alle Knöpfe wirkungslos.
 */
public final class KnowledgeSourcesPanel extends JPanel {

    public static final String ADD_WIKI_LABEL = "+ MediaWiki";
    public static final String ADD_CONFLUENCE_LABEL = "+ Confluence";
    static final String TYPE_MEDIAWIKI = "mediawiki";
    static final String TYPE_CONFLUENCE = "confluence";
    static final String EMPTY_TEXT = "Noch keine Wissensquelle. „+ MediaWiki“ oder „+ Confluence“ legt eine an.";
    static final String HINT_TEXT = "<html>Der Chat durchsucht nur angehakte Quellen; abgewählte werden auch beim "
            + "Start nicht indexiert.</html>";

    private final ComicPalette palette;
    private final ResearchPillButton addWiki;
    private final ResearchPillButton addConfluence;
    private final JPanel list = new JPanel();
    private final List<KnowledgeSourceRow> rows = new ArrayList<KnowledgeSourceRow>();
    private List<KnowledgeSourceItem> items = Collections.emptyList();
    private KnowledgeSourceActions actions;

    public KnowledgeSourcesPanel(ComicPalette palette) {
        super(new BorderLayout());
        this.palette = palette;
        setOpaque(false);
        addWiki = pill(ADD_WIKI_LABEL, "Eine MediaWiki-Site als Wissensquelle hinzufügen", TYPE_MEDIAWIKI);
        addConfluence = pill(ADD_CONFLUENCE_LABEL, "Einen Confluence-Bereich als Wissensquelle hinzufügen",
                TYPE_CONFLUENCE);
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        north.setOpaque(false);
        north.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 8));
        north.add(addWiki);
        north.add(addConfluence);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JScrollPane scroll = new ComicScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel hint = new JLabel(HINT_TEXT);
        hint.setFont(ResearchUiTypography.regular(11f));
        hint.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 14, 10, 12));

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(hint, BorderLayout.SOUTH);
        refresh();
    }

    private ResearchPillButton pill(String label, String tooltip, final String type) {
        ResearchPillButton pill = new ResearchPillButton(label, ResearchUiMetrics.NEW_CHAT_HEIGHT - 4,
                ResearchUiMetrics.RADIUS_CONTROL, ResearchUiMetrics.NEW_CHAT_PADDING_H - 2);
        pill.setFont(ResearchUiTypography.semiBold(12.5f));
        pill.setToolTipText(tooltip);
        pill.addActionListener(event -> {
            if (actions != null) {
                actions.addRequested(type);
            }
        });
        return pill;
    }

    public void setActions(KnowledgeSourceActions actions) {
        this.actions = actions;
        refresh();
    }

    public void setItems(List<KnowledgeSourceItem> sources) {
        this.items = sources == null ? Collections.<KnowledgeSourceItem>emptyList()
                : new ArrayList<KnowledgeSourceItem>(sources);
        refresh();
    }

    public List<KnowledgeSourceItem> items() {
        return Collections.unmodifiableList(items);
    }

    /** Die Zeilen in Anzeigereihenfolge (für Tests). */
    public List<KnowledgeSourceRow> rows() {
        return Collections.unmodifiableList(new ArrayList<KnowledgeSourceRow>(rows));
    }

    public ResearchPillButton addWikiButton() {
        return addWiki;
    }

    public ResearchPillButton addConfluenceButton() {
        return addConfluence;
    }

    private void refresh() {
        boolean canAdd = actions != null && actions.canAdd();
        addWiki.setEnabled(canAdd);
        addConfluence.setEnabled(canAdd);
        list.removeAll();
        rows.clear();
        JLabel header = new JLabel("WISSENSQUELLEN");
        header.setFont(ResearchUiTypography.semiBold(11f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 8));
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
}
