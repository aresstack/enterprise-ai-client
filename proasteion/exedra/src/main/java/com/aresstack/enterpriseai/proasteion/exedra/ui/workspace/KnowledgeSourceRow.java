package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.paint.ResearchCheckIcon;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Quelle im Drawer-Reiter „Wissensquellen“:
 *
 * <pre>
 * [✓] wiki                                ⟳ ✎ ✕
 *     MediaWiki · Hauptseite, Handbuch
 *     34 Seiten im Index · Stand 00:12
 *     › Spaces · 3 von 12                  (nur bei Quellen mit Bereichen, z. B. Confluence)
 * </pre>
 *
 * Das Häkchen schaltet die Quelle für Chat und Indexierung an und ab, ⟳ indexiert sie jetzt, ✎ öffnet den
 * Quellen-Dialog, ein Klick auf den Text zeigt die Quelle im Lesebereich (Doppelklick heftet an), ✕ entfernt die Quelle (nach kurzer Rückfrage). Transparent in Ruhe, heller blauer Hauch beim Überfahren (wie die Chat-Zeilen);
 * abgewählte Quellen stehen gedämpft, ein Problem steht rot in der Statuszeile. „Spaces“ (Pfeil als gezeichnetes
 * Chevron-Icon, nie als Schriftzeichen) klappt die Bereiche der Quelle auf. Jeder Bereich ist wie in MainframeMate
 * ein Häkchen plus ein eigener Name: das Häkchen legt nur fest, ob der Bereich durchsucht wird; der Name wird beim
 * Überfahren zum Link und öffnet seine Startseite (ohne sie: seine Seiten) im Lesebereich.
 */
public final class KnowledgeSourceRow extends JPanel {

    static final String INDEX_TOOLTIP = "Jetzt indexieren";
    static final String EDIT_TOOLTIP = "Bearbeiten";
    static final String OPEN_TOOLTIP = "Im Lesebereich zeigen (Doppelklick heftet an)";
    static final String REMOVE_TOOLTIP = "Quelle entfernen";
    /** Höhe der aufgeklappten Bereichsliste, ab der sie rollt (etwa zehn Einträge). */
    private static final int MAX_PARTITIONS_HEIGHT = 190;
    private static final Color HOVER_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.94f);

    private final KnowledgeSourceItem item;
    private final JCheckBox check = new JCheckBox();
    private final ComposerButton indexButton;
    private final ComposerButton editButton;
    private final ComposerButton removeButton;
    private final JLabel statusLabel = new JLabel();
    private final JLabel partitionsToggle = new JLabel();
    private final List<JCheckBox> partitionBoxes = new ArrayList<JCheckBox>();
    private final List<JLabel> partitionLinks = new ArrayList<JLabel>();
    private boolean hovered;

    public KnowledgeSourceRow(final KnowledgeSourceItem item, final KnowledgeSourceActions actions,
                              ComicPalette palette) {
        super(new BorderLayout(8, 0));
        if (item == null || palette == null) {
            throw new IllegalArgumentException("item and palette must not be null");
        }
        this.item = item;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 4));

        check.setIcon(new ResearchCheckIcon());
        check.setSelectedIcon(new ResearchCheckIcon());
        check.setSelected(item.enabled());
        check.setOpaque(false);
        check.setFocusPainted(false);
        check.setRolloverEnabled(true);
        check.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        check.setToolTipText(item.enabled() ? "Angehakt: der Chat durchsucht diese Quelle"
                : "Abgewählt: der Chat durchsucht diese Quelle nicht");
        check.getAccessibleContext().setAccessibleName("Quelle " + item.id() + " verwenden");
        check.addActionListener(event -> {
            if (actions != null) {
                actions.enabledChanged(item.id(), check.isSelected());
            }
        });
        JPanel west = new JPanel(new BorderLayout());
        west.setOpaque(false);
        west.add(check, BorderLayout.NORTH);

        JPanel text = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, super.getPreferredSize().height); // Breite gibt der Drawer vor
            }
        };
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel title = new JLabel(item.title());
        title.setFont(ResearchUiTypography.semiBold(13f));
        title.setForeground(item.enabled() ? palette.getInk() : ResearchUiPalette.LIGHT_TEXT_MUTED);
        JLabel meta = new JLabel(item.description());
        meta.setFont(ResearchUiTypography.regular(11f));
        meta.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        statusLabel.setText(item.status().isEmpty() ? " " : item.status());
        statusLabel.setFont(ResearchUiTypography.regular(11f));
        statusLabel.setForeground(statusColor(item.state(), palette));
        text.add(title);
        text.add(meta);
        text.add(statusLabel);
        if (!item.partitionsLabel().isEmpty()) {
            text.add(partitionsToggle(actions));
            if (item.partitionsExpanded()) {
                text.add(partitionsList(actions, palette));
            }
        }

        indexButton = ComposerButton.iconButton(ComposerIcons.refresh(), INDEX_TOOLTIP);
        indexButton.setEnabled(item.indexable());
        indexButton.getAccessibleContext().setAccessibleName("Quelle " + item.id() + " jetzt indexieren");
        indexButton.addActionListener(event -> {
            if (actions != null) {
                actions.indexRequested(item.id());
            }
        });
        editButton = ComposerButton.iconButton(ComposerIcons.pencil(), EDIT_TOOLTIP);
        editButton.setEnabled(item.editable());
        editButton.getAccessibleContext().setAccessibleName("Quelle " + item.id() + " bearbeiten");
        editButton.addActionListener(event -> {
            if (actions != null) {
                actions.editRequested(item.id());
            }
        });
        removeButton = ComposerButton.iconButton(ComposerIcons.close(), REMOVE_TOOLTIP);
        removeButton.setEnabled(item.removable());
        removeButton.getAccessibleContext().setAccessibleName("Quelle " + item.id() + " entfernen");
        removeButton.addActionListener(event -> {
            if (actions != null) {
                actions.removeRequested(item.id());
            }
        });
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        east.setOpaque(false);
        east.add(indexButton);
        east.add(editButton);
        east.add(removeButton);
        JPanel eastTop = new JPanel(new BorderLayout());
        eastTop.setOpaque(false);
        eastTop.add(east, BorderLayout.NORTH);

        add(west, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
        add(eastTop, BorderLayout.EAST);

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(OPEN_TOOLTIP);
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
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && actions != null) {
                    actions.linkOpened(ReaderLink.source(item.id(), item.description()),
                            event.getClickCount() >= 2);
                }
            }
        });
    }

    /** „Spaces“ mit Chevron: klappt die Bereiche der Quelle auf und zu, ohne den Lesebereich zu öffnen. */
    private JLabel partitionsToggle(final KnowledgeSourceActions actions) {
        partitionsToggle.setIcon(item.partitionsExpanded() ? ComposerIcons.chevronDown() : ComposerIcons.chevronRight());
        partitionsToggle.setIconTextGap(2);
        partitionsToggle.setText(toggleText());
        partitionsToggle.setFont(ResearchUiTypography.semiBold(11f));
        partitionsToggle.setForeground(ResearchUiPalette.ACCENT_BLUE);
        partitionsToggle.setBorder(BorderFactory.createEmptyBorder(3, 0, 1, 0));
        partitionsToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        partitionsToggle.setToolTipText(item.partitionsLabel() + " anzeigen und auswählen, welche durchsucht werden");
        partitionsToggle.getAccessibleContext().setAccessibleName(item.partitionsLabel() + " von " + item.title());
        partitionsToggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && actions != null) {
                    actions.partitionsToggled(item.id(), !item.partitionsExpanded());
                }
            }
        });
        return partitionsToggle;
    }

    private String toggleText() {
        String text = item.partitionsLabel();
        List<KnowledgeSourcePartitionItem> partitions = item.partitions();
        if (partitions == null || partitions.isEmpty()) {
            return text;
        }
        int selected = 0;
        for (JCheckBox box : partitionBoxes) {
            selected += box.isSelected() ? 1 : 0;
        }
        if (partitionBoxes.isEmpty()) {
            for (KnowledgeSourcePartitionItem partition : partitions) {
                selected += partition.selected() ? 1 : 0;
            }
        }
        return selected == partitions.size() ? text + " · alle " + selected
                : text + " · " + selected + " von " + partitions.size();
    }

    /** Die Bereiche mit Häkchen (wie MainframeMate), bei vielen in einer begrenzten Liste mit Rollbalken. */
    private JComponent partitionsList(final KnowledgeSourceActions actions, ComicPalette palette) {
        List<KnowledgeSourcePartitionItem> partitions = item.partitions();
        if (!item.partitionsProblem().isEmpty() || partitions == null || partitions.isEmpty()) {
            JLabel note = new JLabel(!item.partitionsProblem().isEmpty() ? item.partitionsProblem()
                    : partitions == null ? item.partitionsLabel() + " werden geladen …"
                    : "Keine " + item.partitionsLabel() + " gefunden");
            note.setFont(ResearchUiTypography.regular(11f));
            note.setForeground(item.partitionsProblem().isEmpty() ? ResearchUiPalette.LIGHT_TEXT_MUTED
                    : ResearchUiPalette.DANGER_RED);
            return note;
        }
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        for (final KnowledgeSourcePartitionItem partition : partitions) {
            String name = partition.title().equals(partition.key()) ? partition.key()
                    : partition.key() + " – " + partition.title();
            final JCheckBox box = new JCheckBox();
            box.setIcon(new ResearchCheckIcon());
            box.setSelectedIcon(new ResearchCheckIcon());
            box.setSelected(partition.selected());
            box.setOpaque(false);
            box.setFocusPainted(false);
            box.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 4));
            box.setToolTipText(partition.selected() ? "Angehakt: " + partition.key() + " wird durchsucht"
                    : "Abgewählt: " + partition.key() + " wird nicht durchsucht");
            box.getAccessibleContext().setAccessibleName(partition.key() + " durchsuchen");
            box.addActionListener(event -> {
                partitionsToggle.setText(toggleText());
                if (actions != null) {
                    actions.partitionChanged(item.id(), partition.key(), box.isSelected());
                }
            });
            partitionBoxes.add(box);
            JLabel link = partitionLink(name, partition.link(), actions, palette);
            partitionLinks.add(link);
            JPanel line = new JPanel(new BorderLayout()) {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
            };
            line.setOpaque(false);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            line.add(box, BorderLayout.WEST);
            line.add(link, BorderLayout.CENTER);
            list.add(line);
        }
        partitionsToggle.setText(toggleText());
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension(0, Math.min(size.height, MAX_PARTITIONS_HEIGHT));
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        scroll.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        return scroll;
    }

    /**
     * Der Name eines Bereichs: in Ruhe normaler Text, beim Überfahren Link (blau, unterstrichen, Handcursor); ein
     * Klick öffnet {@code link} im Lesebereich (Doppelklick heftet an). Ohne Ziel bleibt er reiner Text.
     */
    private static JLabel partitionLink(String name, final ReaderLink link, final KnowledgeSourceActions actions,
                                        final ComicPalette palette) {
        final JLabel label = new JLabel(name);
        final Font plain = ResearchUiTypography.regular(11f);
        Map<TextAttribute, Object> underline = new HashMap<TextAttribute, Object>();
        underline.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        final Font underlined = plain.deriveFont(underline);
        label.setFont(plain);
        label.setForeground(palette.getInk());
        label.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        if (link == null) {
            label.setToolTipText(name);
            return label;
        }
        label.setToolTipText(name + (link.isSource() ? " (Seiten anzeigen)" : " (Startseite öffnen)"));
        label.getAccessibleContext().setAccessibleName(name + " im Lesebereich öffnen");
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                label.setFont(underlined);
                label.setForeground(ResearchUiPalette.ACCENT_BLUE);
                label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                label.setFont(plain);
                label.setForeground(palette.getInk());
                label.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && actions != null) {
                    actions.linkOpened(link, event.getClickCount() >= 2);
                }
            }
        });
        return label;
    }

    static Color statusColor(KnowledgeSourceItem.State state, ComicPalette palette) {
        switch (state) {
            case RUNNING:
                return palette.getAgentPetrol();
            case PROBLEM:
                return ResearchUiPalette.DANGER_RED;
            default:
                return ResearchUiPalette.LIGHT_CONTROL_TEXT;
        }
    }

    public KnowledgeSourceItem item() {
        return item;
    }

    public JCheckBox checkBox() {
        return check;
    }

    public ComposerButton indexButton() {
        return indexButton;
    }

    public ComposerButton editButton() {
        return editButton;
    }

    public ComposerButton removeButton() {
        return removeButton;
    }

    /** „Spaces“ mit Chevron (Tests); ohne Bereiche nicht im Bild. */
    public JLabel partitionsToggle() {
        return partitionsToggle;
    }

    /** Die Häkchen der aufgeklappten Bereiche (Tests). */
    public List<JCheckBox> partitionBoxes() {
        return Collections.unmodifiableList(partitionBoxes);
    }

    /** Die Namen der aufgeklappten Bereiche (Tests). */
    public List<JLabel> partitionLinks() {
        return Collections.unmodifiableList(partitionLinks);
    }

    public String statusText() {
        return statusLabel.getText();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (hovered) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), ResearchUiMetrics.CHAT_ROW_RADIUS,
                        HOVER_WASH);
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(graphics);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
