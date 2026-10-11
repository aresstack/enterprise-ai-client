package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;

/**
 * Kategorie „Tools“: nur lesend, alle Werkzeuge der zentralen Registry mit Name, Verfügbarkeit (Chat, Agent, …),
 * Beschreibung und Herkunft. Die Zeilen entstehen allein aus {@link ToolOverview#tools()}; ein neu registriertes
 * Werkzeug erscheint ohne Änderung hier. Neu geladen wird bei jedem Anzeigen des Reiters.
 */
final class ToolsTab {

    static final String NOTE = "Die Werkzeuge, die Chat und Agenten gerade angeboten werden, aus der zentralen "
            + "Registry der Anwendung. Nur lesend.";
    static final String EMPTY = "Keine Werkzeuge registriert. Die Übersicht füllt sich, sobald die Anwendung läuft.";
    static final String ORIGIN_PREFIX = "Herkunft: ";

    private final ToolOverview overview;
    private final ComicPalette palette;
    private final JPanel lines = new JPanel();
    private final JPanel panel;

    ToolsTab(ToolOverview overview, ComicPalette palette) {
        this.overview = overview;
        this.palette = palette;
        FormRows rows = new FormRows(palette);
        rows.note(NOTE);
        lines.setOpaque(false);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        rows.component(null, lines, null);
        panel = FormRows.column(palette, FormRows.plate("Tools", palette.getAgentPetrol(), rows.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    /** Liest die Registry neu und baut die Zeilen. */
    void reload() {
        List<ToolOverviewEntry> tools = overview.tools();
        lines.removeAll();
        if (tools.isEmpty()) {
            JLabel empty = new WrappingLabel(FormRows.flow(EMPTY));
            empty.setForeground(FormRows.MUTED);
            empty.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            lines.add(empty);
        }
        for (ToolOverviewEntry tool : tools) {
            lines.add(line(tool));
        }
        lines.revalidate();
        lines.repaint();
    }

    private JComponent line(ToolOverviewEntry tool) {
        JLabel name = new JLabel(tool.name());
        name.putClientProperty("html.disable", Boolean.TRUE);
        name.setForeground(palette.getInk());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        JLabel availability = new JLabel(tool.availability());
        availability.putClientProperty("html.disable", Boolean.TRUE);
        availability.setForeground(palette.getAgentPetrol());
        availability.getAccessibleContext().setAccessibleName("Verfügbar in " + tool.availability());
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(name, BorderLayout.CENTER);
        head.add(availability, BorderLayout.EAST);
        JLabel description = new WrappingLabel(FormRows.flow(tool.description()));
        description.setForeground(palette.getInk());
        JLabel origin = new WrappingLabel(FormRows.flow(ORIGIN_PREFIX + tool.origin()));
        origin.setForeground(FormRows.MUTED);
        origin.setFont(origin.getFont().deriveFont(Font.PLAIN, Math.max(11f, origin.getFont().getSize2D() - 1f)));
        JPanel line = new JPanel(new BorderLayout(0, 2));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, FormRows.MUTED),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        line.add(head, BorderLayout.NORTH);
        line.add(description, BorderLayout.CENTER);
        line.add(origin, BorderLayout.SOUTH);
        line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        line.getAccessibleContext().setAccessibleName("Tool " + tool.name());
        return line;
    }

    // Für Tests.

    JPanel lines() {
        return lines;
    }
}
