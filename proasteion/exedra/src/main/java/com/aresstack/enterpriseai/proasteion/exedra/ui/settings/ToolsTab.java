package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Kategorie „Tools“: alle Werkzeuge der zentralen Registry mit Häkchen „aktiv“, Name, Verfügbarkeit (Chat, Agent, …),
 * Beschreibung und Herkunft. Die Zeilen entstehen allein aus {@link ToolOverview#tools()}; ein neu registriertes
 * Werkzeug erscheint ohne Änderung hier. Neu geladen wird bei jedem Anzeigen des Reiters.
 *
 * <p>Die Häkchen bearbeiten den Entwurf {@code tools.disabled} (abgeschaltete Namen); Speichern schreibt ihn, und die
 * Registry liest ihn ohne Neustart. Abgeschaltete Namen, die gerade nicht registriert sind (z. B. Werkzeuge einer
 * anderen Recherche-Phase), bleiben erhalten.
 *
 * <p>Each tool also has an approval dropdown (Erlauben/Nachfragen/Verbieten), tools with sub-rules one per action and
 * scope; they edit the draft {@code tools.approval}.
 */
final class ToolsTab {

    static final String NOTE = "Die Werkzeuge der zentralen Registry der Anwendung. Ohne Häkchen wird ein Werkzeug "
            + "weder dem Chat noch einem Agenten angeboten; es bleibt registriert. Gilt nach dem Speichern sofort.";
    static final String ACTIVE_TOOLTIP = "Aktiv: dem Chat bzw. den Agenten anbieten";
    static final String EMPTY = "Keine Werkzeuge registriert. Die Übersicht füllt sich, sobald die Anwendung läuft.";
    static final String ORIGIN_PREFIX = "Herkunft: ";
    static final String MAX_ROUNDS_LABEL = "Max. Werkzeugrunden";
    static final String MAX_ROUNDS_HELP = "Werkzeugrunden je Antwort im normalen Chat (chat.tools.maxRounds, 1–32; "
            + "Standard 8). Verlangt das Modell danach noch ein Werkzeug, antwortet es in einer letzten Runde ohne "
            + "Werkzeuge aus den bisherigen Ergebnissen.";
    static final String TREE_LIMIT_LABEL = "Seitenbaum: Kinder je Abruf";
    static final String TREE_LIMIT_HELP = "So viele direkte Unterseiten liefert das Werkzeug knowledge_tree, wenn das "
            + "Modell keine Anzahl nennt (agent.tools.treeChildLimit, 0–10000; Standard 20; 0 = alle). Weitere holt "
            + "das Modell seitenweise nach. Gilt nach dem Speichern sofort.";
    static final String TURN_ANALYSIS_LABEL = "Werkzeugwahl protokollieren (Diagnose)";
    static final String TURN_ANALYSIS_HELP = "Schreibt je Chat-Nachricht Nutzertext, Herkunft (Text oder Diktat), die "
            + "linguistische Analyse, den Antwortplan, den an das Modell gesandten Hinweis und die "
            + "folgenden Werkzeugaufrufe ins "
            + "Protokoll (chat.diagnostics.turnAnalysis). Enthält Nutzertext; nur zur Fehlersuche einschalten.";
    static final String PLANNING_LABEL = "Antwort vor jeder Nachricht planen";
    static final String PLANNING_HELP = "Vor jeder Antwort fragt der Client das Modell in einem eigenen Aufruf, "
            + "was du wahrscheinlich sagen und erreichen wolltest (Diktatfehler, Bezüge, Absicht) und was der nächste "
            + "Schritt ist: direkt antworten, Programmhilfe, Suche oder Rückfrage. Die Antwort sieht deine Nachricht "
            + "und diesen Plan nebeneinander (chat.answerPlanning.enabled). Kostet einen zusätzlichen Modellaufruf je "
            + "Nachricht.";
    static final String USE_AT_LABEL = "Interpretation verwenden ab (%)";
    static final String USE_AT_HELP = "Ab dieser Sicherheit arbeitet die Antwort mit der Interpretation "
            + "(chat.answerPlanning.useAtPercent, 0–100; Standard 85). Darunter gilt sie nur als Hinweis.";
    static final String ASK_BELOW_LABEL = "Nachfragen unter (%)";
    static final String ASK_BELOW_HELP = "Unter dieser Sicherheit fragt die Antwort nach, statt eine Lesart als Tatsache "
            + "anzunehmen, und es wird nicht gesucht (chat.answerPlanning.askBelowPercent, 0–100; Standard 60).";
    static final String APPROVAL_TOOLTIP = "Genehmigung: Erlauben führt aus, Nachfragen wartet auf deine Bestätigung "
            + "in der gelben Werkzeug-Blase, Verbieten führt nie aus (tools.approval)";
    static final String SUB_RULE_PREFIX = "Genehmigung für ";
    /** No own rule: the built-in default of the tool for this action/scope, else the tool-level rule. */
    static final String INHERIT = "Standard";
    /** Policy names as stored in {@code tools.approval}, with their German labels, in dropdown order. */
    private static String[][] policies() {
        return new String[][]{{"ALLOW", "Erlauben"}, {"ASK", "Nachfragen"}, {"DENY", "Verbieten"}};
    }
    static final String REQUIRED_TOOLTIP = "Pflichtwerkzeug: ohne es startet bzw. arbeitet der Agent nicht; "
            + "es lässt sich nicht abschalten";

    private final ToolOverview overview;
    private final ComicPalette palette;
    private final JPanel lines = new JPanel();
    private final JTextField maxRounds;
    private final JTextField treeLimit;
    private final JCheckBox turnAnalysisLog;
    private final JCheckBox planning;
    private final JTextField useAt;
    private final JTextField askBelow;
    private final Map<String, JCheckBox> boxes = new LinkedHashMap<String, JCheckBox>();
    /** Approval dropdowns by rule key ({@code tool} or {@code tool:action/scope}). */
    private final Map<String, JComboBox<String>> approvalBoxes = new LinkedHashMap<String, JComboBox<String>>();
    /** The draft of {@code tools.approval}: rule key to policy name, in order; keys of unregistered tools stay. */
    private final Map<String, String> approvals = new LinkedHashMap<String, String>();
    /** Der Entwurf: abgeschaltete Werkzeuge, in Reihenfolge. */
    private final Set<String> disabled = new LinkedHashSet<String>();
    /** Pflichtwerkzeuge der zuletzt geladenen Registry: nie im Entwurf abgeschaltet. */
    private final Set<String> required = new LinkedHashSet<String>();
    private final JPanel panel;

    ToolsTab(ToolOverview overview, ComicPalette palette) {
        this.overview = overview;
        this.palette = palette;
        FormRows rows = new FormRows(palette);
        rows.note(NOTE);
        lines.setOpaque(false);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        rows.component(null, lines, null);
        FormRows loop = new FormRows(palette);
        maxRounds = loop.textField(MAX_ROUNDS_LABEL, MAX_ROUNDS_HELP);
        loop.note(MAX_ROUNDS_HELP);
        turnAnalysisLog = loop.checkBox(TURN_ANALYSIS_LABEL, TURN_ANALYSIS_HELP);
        loop.note(TURN_ANALYSIS_HELP);
        FormRows tree = new FormRows(palette);
        treeLimit = tree.textField(TREE_LIMIT_LABEL, TREE_LIMIT_HELP);
        tree.note(TREE_LIMIT_HELP);
        FormRows reading = new FormRows(palette);
        planning = reading.checkBox(PLANNING_LABEL, PLANNING_HELP);
        reading.note(PLANNING_HELP);
        useAt = reading.textField(USE_AT_LABEL, USE_AT_HELP);
        reading.note(USE_AT_HELP);
        askBelow = reading.textField(ASK_BELOW_LABEL, ASK_BELOW_HELP);
        reading.note(ASK_BELOW_HELP);
        panel = FormRows.column(palette,
                FormRows.plate("Werkzeugrunden im Chat", palette.getAccentOrange(), loop.panel(), palette),
                FormRows.plate("Planen der Antwort", palette.getAccentOrange(), reading.panel(), palette),
                FormRows.plate("Seitenbaum für das Modell", palette.getAccentOrange(), tree.panel(), palette),
                FormRows.plate("Tools", palette.getAgentPetrol(), rows.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    /** Liest die Registry neu und baut die Zeilen. */
    void reload() {
        List<ToolOverviewEntry> tools = overview.tools();
        lines.removeAll();
        boxes.clear();
        approvalBoxes.clear();
        required.clear();
        for (ToolOverviewEntry tool : tools) {
            if (tool.required()) {
                required.add(tool.name());
            }
        }
        disabled.removeAll(required);
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

    private JComponent line(final ToolOverviewEntry tool) {
        final JCheckBox name = new JCheckBox(tool.name(), !disabled.contains(tool.name()));
        name.putClientProperty("html.disable", Boolean.TRUE);
        name.setOpaque(false);
        name.setFocusPainted(false);
        name.setForeground(palette.getInk());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        name.setToolTipText(tool.required() ? REQUIRED_TOOLTIP : ACTIVE_TOOLTIP);
        name.getAccessibleContext().setAccessibleName("aktiv: " + tool.name());
        if (tool.required()) {
            name.setSelected(true);
            name.setEnabled(false);
        }
        name.addActionListener(event -> {
            if (name.isSelected()) {
                disabled.remove(tool.name());
            } else {
                disabled.add(tool.name());
            }
        });
        boxes.put(tool.name(), name);
        JLabel availability = new JLabel(tool.availability());
        availability.putClientProperty("html.disable", Boolean.TRUE);
        availability.setForeground(palette.getAgentPetrol());
        availability.getAccessibleContext().setAccessibleName("Verfügbar in " + tool.availability());
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        east.setOpaque(false);
        east.add(availability);
        if (!tool.required()) {
            east.add(approvalBox(tool.name(), tool.name(), false));
        }
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(name, BorderLayout.CENTER);
        head.add(east, BorderLayout.EAST);
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
        if (tool.required() || tool.approvalSubRules().isEmpty()) {
            line.add(origin, BorderLayout.SOUTH);
        } else {
            JPanel south = new JPanel();
            south.setOpaque(false);
            south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
            for (String rule : tool.approvalSubRules()) {
                JPanel sub = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                sub.setOpaque(false);
                JLabel label = new JLabel(SUB_RULE_PREFIX + rule);
                label.putClientProperty("html.disable", Boolean.TRUE);
                label.setForeground(palette.getInk());
                sub.add(label);
                sub.add(approvalBox(tool.name() + ":" + rule, tool.name() + " " + rule, true));
                sub.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                south.add(sub);
            }
            origin.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            south.add(origin);
            line.add(south, BorderLayout.SOUTH);
        }
        line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        line.getAccessibleContext().setAccessibleName("Tool " + tool.name());
        return line;
    }

    private JComboBox<String> approvalBox(final String key, String accessibleName, final boolean inherit) {
        final JComboBox<String> box = new JComboBox<String>();
        if (inherit) {
            box.addItem(INHERIT);
        }
        for (String[] policy : policies()) {
            box.addItem(policy[1]);
        }
        box.setToolTipText(APPROVAL_TOOLTIP);
        box.getAccessibleContext().setAccessibleName("Genehmigung: " + accessibleName);
        select(box, key, inherit);
        box.addActionListener(event -> {
            String policy = policyName((String) box.getSelectedItem());
            if (policy == null || (!inherit && "ALLOW".equals(policy))) {
                approvals.remove(key); // no rule = allowed, and a sub-rule without value follows the tool
            } else {
                approvals.put(key, policy);
            }
        });
        approvalBoxes.put(key, box);
        return box;
    }

    private void select(JComboBox<String> box, String key, boolean inherit) {
        String label = label(approvals.get(key));
        box.setSelectedItem(label != null ? label : inherit ? INHERIT : policies()[0][1]);
    }

    private static String policyName(String label) {
        for (String[] policy : policies()) {
            if (policy[1].equals(label)) {
                return policy[0];
            }
        }
        return null;
    }

    private static String label(String policyName) {
        for (String[] policy : policies()) {
            if (policy[0].equalsIgnoreCase(policyName == null ? "" : policyName.trim())) {
                return policy[1];
            }
        }
        return null;
    }

    void load(SettingsForm form) {
        maxRounds.setText(form.chatToolsMaxRounds());
        treeLimit.setText(form.toolsTreeChildLimit());
        turnAnalysisLog.setSelected(form.turnAnalysisLog());
        planning.setSelected(form.answerPlanningEnabled());
        useAt.setText(form.answerPlanningUseAtPercent());
        askBelow.setText(form.answerPlanningAskBelowPercent());
        disabled.clear();
        for (String item : form.disabledTools().split(",")) {
            if (!item.trim().isEmpty() && !required.contains(item.trim())) {
                disabled.add(item.trim());
            }
        }
        for (Map.Entry<String, JCheckBox> box : boxes.entrySet()) {
            box.getValue().setSelected(!disabled.contains(box.getKey()));
        }
        approvals.clear();
        for (String entry : form.toolApprovals().split(";")) {
            int eq = entry.indexOf('=');
            if (eq > 0 && label(entry.substring(eq + 1)) != null) {
                approvals.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim().toUpperCase(Locale.ROOT));
            }
        }
        for (Map.Entry<String, JComboBox<String>> box : approvalBoxes.entrySet()) {
            select(box.getValue(), box.getKey(), box.getKey().indexOf(':') >= 0);
        }
    }

    void store(SettingsForm.Builder b) {
        StringBuilder text = new StringBuilder();
        for (String name : disabled) {
            if (required.contains(name)) {
                continue; // ein Pflichtwerkzeug wird nie abgeschaltet gespeichert
            }
            text.append(text.length() == 0 ? "" : ",").append(name);
        }
        b.disabledTools(text.toString());
        StringBuilder rules = new StringBuilder();
        for (Map.Entry<String, String> rule : approvals.entrySet()) {
            rules.append(rules.length() == 0 ? "" : ";").append(rule.getKey()).append('=').append(rule.getValue());
        }
        b.toolApprovals(rules.toString());
        b.chatToolsMaxRounds(maxRounds.getText());
        b.toolsTreeChildLimit(treeLimit.getText());
        b.turnAnalysisLog(turnAnalysisLog.isSelected());
        b.answerPlanningEnabled(planning.isSelected());
        b.answerPlanningUseAtPercent(useAt.getText());
        b.answerPlanningAskBelowPercent(askBelow.getText());
    }

    // Für Tests.

    /** Das Häkchen „aktiv“ des Werkzeugs oder {@code null}, solange der Reiter es nicht zeigt. */
    JCheckBox activeBox(String name) {
        return boxes.get(name);
    }

    /** The approval dropdown of a rule key ({@code tool} or {@code tool:action/scope}), or {@code null}. */
    JComboBox<String> approvalBox(String key) {
        return approvalBoxes.get(key);
    }

    JPanel lines() {
        return lines;
    }

    JTextField maxRounds() {
        return maxRounds;
    }

    JTextField treeLimit() {
        return treeLimit;
    }
}
