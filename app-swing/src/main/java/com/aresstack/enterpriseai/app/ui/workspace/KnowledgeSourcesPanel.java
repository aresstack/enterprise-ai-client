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
import javax.swing.JComponent;
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

/**
 * Die Drawer-Seite „Wissensquellen“: oben genau ein „+ Quelle“ (wie „+ Neuer Chat“; welche Quelltypen es gibt,
 * entscheiden die angebundenen Adapter, nicht die Oberfläche), darunter je Quelle eine {@link KnowledgeSourceRow}
 * mit Häkchen, Indexstand, „Jetzt indexieren“, Bearbeiten und Entfernen, dann ein
 * stiller Hinweis, was das Häkchen bewirkt, und unten der Bereich „Index“ mit „Index …“ für Indexverzeichnis
 * und Indexierung beim Start (öffnet den Index-Dialog; gilt beim nächsten Start). Ohne
 * {@link KnowledgeSourceActions} bzw. {@link #setIndexSettingsAction} bleiben die Knöpfe wirkungslos.
 */
public final class KnowledgeSourcesPanel extends JPanel {

    public static final String ADD_LABEL = "+ Quelle";
    static final String EMPTY_TEXT = "Noch keine Wissensquelle. „+ Quelle“ legt eine an.";
    static final String HINT_TEXT = "<html>Der Chat durchsucht nur angehakte Quellen; abgewählte werden auch beim "
            + "Start nicht indexiert.</html>";
    public static final String INDEX_LABEL = "Index …";
    static final String INDEX_TOOLTIP = "Indexverzeichnis und Indexierung beim Start ändern; gilt beim nächsten Start";
    static final String INDEX_HINT_TEXT = "<html>Indexverzeichnis und Indexierung beim Start; Änderungen gelten beim "
            + "nächsten Start.</html>";

    private final ComicPalette palette;
    private final ResearchPillButton addButton;
    private final ResearchPillButton indexButton;
    private final JPanel list = new WidthTrackingPanel();
    private final List<KnowledgeSourceRow> rows = new ArrayList<KnowledgeSourceRow>();
    private List<KnowledgeSourceItem> items = Collections.emptyList();
    private KnowledgeSourceActions actions;
    private Runnable indexSettingsAction;

    public KnowledgeSourcesPanel(ComicPalette palette) {
        super(new BorderLayout());
        this.palette = palette;
        setOpaque(false);
        addButton = pill(ADD_LABEL, "Eine Wissensquelle hinzufügen (Wiki, Confluence, Dateien ...)");
        JPanel addRows = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addRows.setOpaque(false);
        addRows.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 8));
        addRows.add(addButton);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JScrollPane scroll = new ComicScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel hint = mutedText(HINT_TEXT, 6, 6);

        indexButton = new ResearchPillButton(INDEX_LABEL, ResearchUiMetrics.NEW_CHAT_HEIGHT - 4,
                ResearchUiMetrics.RADIUS_CONTROL, ResearchUiMetrics.NEW_CHAT_PADDING_H - 2);
        indexButton.setFont(ResearchUiTypography.semiBold(12.5f));
        indexButton.setToolTipText(INDEX_TOOLTIP);
        indexButton.setEnabled(false);
        indexButton.addActionListener(event -> {
            if (indexSettingsAction != null) {
                indexSettingsAction.run();
            }
        });
        JPanel indexRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        indexRow.setOpaque(false);
        indexRow.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 8));
        indexRow.add(indexButton);
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        for (JComponent part : new JComponent[] {hint, sectionHeader("INDEX"), indexRow,
                mutedText(INDEX_HINT_TEXT, 4, 10)}) {
            part.setAlignmentX(LEFT_ALIGNMENT);
            south.add(part);
        }

        add(addRows, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
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

    /** Die stille Abschnittsüberschrift („WISSENSQUELLEN“, „INDEX“). */
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

    /** Was „Index …“ tut (öffnet produktiv den Index-Dialog über der Konfigurationsdatei); {@code null} sperrt. */
    public void setIndexSettingsAction(Runnable action) {
        this.indexSettingsAction = action;
        indexButton.setEnabled(action != null);
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

    /** „+ Quelle“ oben. */
    public ResearchPillButton addButton() {
        return addButton;
    }

    /** „Index …“ im Bereich „Index“ unten. */
    public ResearchPillButton indexButton() {
        return indexButton;
    }

    private void refresh() {
        boolean canAdd = actions != null && actions.canAdd();
        addButton.setEnabled(canAdd);
        list.removeAll();
        rows.clear();
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
