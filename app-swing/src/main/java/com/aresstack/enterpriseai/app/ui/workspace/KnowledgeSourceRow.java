package com.aresstack.enterpriseai.app.ui.workspace;

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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Eine Quelle im Drawer-Reiter „Wissensquellen“:
 *
 * <pre>
 * [✓] wiki                                ⟳ ✎ ✕
 *     MediaWiki · Hauptseite, Handbuch
 *     34 Seiten im Index · Stand 00:12
 * </pre>
 *
 * Das Häkchen schaltet die Quelle für Chat und Indexierung an und ab, ⟳ indexiert sie jetzt, ✎ und ein Klick auf
 * den Text öffnen den Quellen-Dialog, ✕ entfernt die Quelle (nach kurzer Rückfrage). Transparent in Ruhe, heller blauer Hauch beim Überfahren (wie die Chat-Zeilen);
 * abgewählte Quellen stehen gedämpft, ein Problem steht rot in der Statuszeile.
 */
public final class KnowledgeSourceRow extends JPanel {

    static final String INDEX_TOOLTIP = "Jetzt indexieren";
    static final String EDIT_TOOLTIP = "Bearbeiten";
    static final String REMOVE_TOOLTIP = "Quelle entfernen";
    private static final Color HOVER_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.94f);

    private final KnowledgeSourceItem item;
    private final JCheckBox check = new JCheckBox();
    private final ComposerButton indexButton;
    private final ComposerButton editButton;
    private final ComposerButton removeButton;
    private final JLabel statusLabel = new JLabel();
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
                : "Abgewählt: weder Indexierung beim Start noch Suche im Chat");
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
        JLabel title = new JLabel(item.id());
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

        if (item.editable()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(EDIT_TOOLTIP);
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
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && item.editable() && actions != null) {
                    actions.editRequested(item.id());
                }
            }
        });
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

    public String statusText() {
        return statusLabel.getText();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (hovered && item.editable()) {
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
