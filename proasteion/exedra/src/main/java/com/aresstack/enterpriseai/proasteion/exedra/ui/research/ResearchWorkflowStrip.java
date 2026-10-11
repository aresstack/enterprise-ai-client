package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Die Benutzerführung des Recherche-Workflows über dem Composer (askai arch {@code ScopingComposerAccessory}): die vier
 * Phasen Konzept → Quellen → Gliederung → Dokument als Comic-Stufen mit dem Zustand der aktiven Phase, darunter die
 * Fortschrittszeile des Quellenlaufs und die roten Aktions-Tags des aktuellen Zustands. Die Leiste zeigt nur, was ihr
 * {@link Snapshot} sagt; ein Klick meldet die Aktions-ID. Nur auf dem EDT.
 */
public final class ResearchWorkflowStrip extends JPanel {

    /** Eine Phase für die Anzeige: {@code progress} ist DONE, ACTIVE oder OPEN. */
    public static final class PhaseItem {
        final String title;
        final String progress;
        final String stateLabel;

        public PhaseItem(String title, String progress, String stateLabel) {
            this.title = title == null ? "" : title;
            this.progress = progress == null ? "OPEN" : progress;
            this.stateLabel = stateLabel == null ? "" : stateLabel;
        }
    }

    /** Ein Aktions-Tag. */
    public static final class ActionItem {
        final String id;
        final String label;
        final String tooltip;
        final boolean enabled;

        public ActionItem(String id, String label, String tooltip, boolean enabled) {
            this.id = id;
            this.label = label;
            this.tooltip = tooltip;
            this.enabled = enabled;
        }
    }

    /** Was die Leiste zeigt. */
    public static final class Snapshot {
        final List<PhaseItem> phases;
        final String progressLine;
        final List<ActionItem> actions;
        final boolean busy;

        public Snapshot(List<PhaseItem> phases, String progressLine, List<ActionItem> actions, boolean busy) {
            this.phases = phases == null ? Collections.<PhaseItem>emptyList() : phases;
            this.progressLine = progressLine == null ? "" : progressLine;
            this.actions = actions == null ? Collections.<ActionItem>emptyList() : actions;
            this.busy = busy;
        }
    }

    private final PhaseStepper stepper = new PhaseStepper();
    private final JLabel progress = new JLabel(" ");
    private final JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private Consumer<String> actionHandler;
    private Snapshot snapshot = new Snapshot(null, null, null, false);

    public ResearchWorkflowStrip() {
        super(new BorderLayout(0, 2));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        progress.setFont(ResearchUiTypography.regular(12f));
        progress.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        actionRow.setOpaque(false);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(progress, BorderLayout.NORTH);
        south.add(actionRow, BorderLayout.CENTER);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(stepper);
        add(stepper, BorderLayout.NORTH);
        add(south, BorderLayout.CENTER);
    }

    /** Wer eine Aktion ausführt (Aktions-ID). */
    public void setActionHandler(Consumer<String> handler) {
        this.actionHandler = handler;
    }

    public void render(Snapshot next) {
        snapshot = next == null ? new Snapshot(null, null, null, false) : next;
        stepper.repaint();
        stepper.revalidate();
        progress.setText(snapshot.progressLine.isEmpty() ? " " : snapshot.progressLine);
        progress.setToolTipText(snapshot.progressLine.isEmpty() ? null : snapshot.progressLine);
        actionRow.removeAll();
        for (final ActionItem action : snapshot.actions) {
            ResearchTagButton button = new ResearchTagButton(action.label, ResearchTagButton.ACTION_BACKGROUND,
                    ResearchTagButton.ACTION_FOREGROUND);
            button.setToolTipText(action.tooltip);
            button.setEnabled(action.enabled && !snapshot.busy);
            button.setName("research-action-" + action.id);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (actionHandler != null) {
                        actionHandler.accept(action.id);
                    }
                }
            });
            actionRow.add(button);
        }
        actionRow.setVisible(!snapshot.actions.isEmpty());
        revalidate();
        repaint();
    }

    /** Die Beschriftungen der sichtbaren Aktions-Tags (Tests, Barrierefreiheit). */
    public List<String> actionLabels() {
        List<String> labels = new ArrayList<String>();
        for (ActionItem action : snapshot.actions) {
            labels.add(action.label);
        }
        return labels;
    }

    /** Text der Fortschrittszeile. */
    public String progressText() {
        return snapshot.progressLine;
    }

    /** Die vier Stufen als gezeichnete Plaketten mit Verbindern; die aktive trägt ihren Zustand. */
    private final class PhaseStepper extends JComponent {
        private static final int HEIGHT = 30;
        private static final int GAP = 18;
        private static final int PAD = 10;
        private static final int ARC = 14;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, HEIGHT + 4);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            List<PhaseItem> phases = snapshot.phases;
            if (phases.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                ComicPalette palette = ComicPalette.defaultPalette();
                Color ink = palette.getInk();
                g2.setFont(ResearchUiTypography.semiBold(12f));
                FontMetrics metrics = g2.getFontMetrics();
                int available = getWidth() - 2 - GAP * (phases.size() - 1);
                int x = 1;
                for (int index = 0; index < phases.size(); index++) {
                    PhaseItem phase = phases.get(index);
                    String text = "ACTIVE".equals(phase.progress) && !phase.stateLabel.isEmpty()
                            ? phase.title + ": " + phase.stateLabel : phase.title;
                    int natural = metrics.stringWidth(text) + 2 * PAD + ("DONE".equals(phase.progress) ? 14 : 0);
                    int width = Math.min(natural, Math.max(60, available / phases.size()
                            + ("ACTIVE".equals(phase.progress) ? available / 4 : 0)));
                    Color fill = "DONE".equals(phase.progress) ? new Color(0xD7EFD9)
                            : "ACTIVE".equals(phase.progress) ? palette.getAccentYellow() : Color.WHITE;
                    g2.setColor(fill);
                    g2.fillRoundRect(x, 1, width, HEIGHT, ARC, ARC);
                    g2.setColor("OPEN".equals(phase.progress) ? ResearchUiPalette.LIGHT_CONTROL_BORDER : ink);
                    g2.setStroke(new BasicStroke("ACTIVE".equals(phase.progress) ? 2f : 1.4f));
                    g2.drawRoundRect(x, 1, width, HEIGHT, ARC, ARC);
                    int textX = x + PAD;
                    if ("DONE".equals(phase.progress)) {
                        // gezeichneter Haken statt Zeichen
                        g2.setColor(ResearchUiPalette.WAVE_GREEN);
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int mid = 1 + HEIGHT / 2;
                        g2.drawLine(textX, mid, textX + 3, mid + 4);
                        g2.drawLine(textX + 3, mid + 4, textX + 9, mid - 4);
                        textX += 14;
                    }
                    g2.setColor("OPEN".equals(phase.progress) ? ResearchUiPalette.LIGHT_TEXT_MUTED : ink);
                    String shown = fit(text, metrics, width - (textX - x) - PAD / 2);
                    g2.drawString(shown, textX, 1 + (HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2);
                    if (index < phases.size() - 1) {
                        g2.setColor(ResearchUiPalette.LIGHT_CONTROL_BORDER);
                        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int y = 1 + HEIGHT / 2;
                        g2.drawLine(x + width + 3, y, x + width + GAP - 3, y);
                    }
                    x += width + GAP;
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public String getToolTipText() {
            StringBuilder text = new StringBuilder();
            for (PhaseItem phase : snapshot.phases) {
                text.append(text.length() == 0 ? "" : " - ").append(phase.title);
                if (!phase.stateLabel.isEmpty()) {
                    text.append(" (").append(phase.stateLabel).append(')');
                }
            }
            return text.toString();
        }
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && metrics.stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }
}
