package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.icon.ApplicationIcons;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowShape;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Name und Farbe einer Reitergruppe im Comic-Stil der App, gebaut wie {@code PasswordManagerPanel.confirm}: rahmenloser
 * Dialog mit Comic-Fensterrand, Überschrift zum Verschieben, Namensfeld, neun runde Farbfelder (Firefox-Farben),
 * OK und Abbrechen; Enter bestätigt, Esc bricht ab. Dient für „Neue Gruppe“ und „Gruppe bearbeiten“.
 */
final class ReaderTabGroupDialog {

    static final String NEW_TITLE = "Neue Gruppe";
    static final String EDIT_TITLE = "Gruppe bearbeiten";
    static final String NAME_LABEL = "Name (darf leer bleiben)";
    static final String COLOR_LABEL = "Farbe";
    private static final int WINDOW_PADDING = 4;

    private ReaderTabGroupDialog() {
    }

    /** Name (getrimmt, ggf. leer) und Farbe. */
    static final class Result {
        private final String name;
        private final ReaderTabGroup.Tint tint;

        Result(String name, ReaderTabGroup.Tint tint) {
            this.name = name;
            this.tint = tint;
        }

        String name() {
            return name;
        }

        ReaderTabGroup.Tint tint() {
            return tint;
        }
    }

    /**
     * Zeigt den Dialog modal (nur auf dem EDT) und liefert die Eingabe oder {@code null} bei Abbrechen/Esc.
     */
    static Result show(Component parent, String title, String name, ReaderTabGroup.Tint tint, ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        Window owner = parent == null ? null : (parent instanceof Window ? (Window) parent
                : SwingUtilities.getWindowAncestor(parent));
        final JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        ApplicationIcons.apply(dialog);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        final Result[] result = {null};
        final Form form = new Form(title, name, tint, colors, answer -> {
            result[0] = answer;
            dialog.dispose();
        });
        ComicWindowDragger.install(form.heading());
        dialog.setContentPane(form);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                form.nameField().requestFocusInWindow();
            }
        });
        dialog.pack();
        ComicWindowShape.install(dialog, form, colors, WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    /** Der Inhalt des Dialogs; ohne Fenster prüfbar. {@code done} erhält das Ergebnis oder {@code null}. */
    static final class Form extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JLabel heading;
        private final JTextField name = new JTextField(22);
        private final Map<ReaderTabGroup.Tint, Swatch> swatches =
                new EnumMap<ReaderTabGroup.Tint, Swatch>(ReaderTabGroup.Tint.class);
        private final ComposerButton ok;
        private final ComposerButton cancel;
        private final Consumer<Result> done;
        private ReaderTabGroup.Tint chosen;
        private boolean finished;

        Form(String title, String initialName, ReaderTabGroup.Tint initialTint, ComicPalette palette,
             Consumer<Result> done) {
            super(new BorderLayout(0, 10));
            this.done = done;
            this.chosen = initialTint == null ? ReaderTabGroup.Tint.BLUE : initialTint;
            setBackground(palette.getSurface());
            setBorder(BorderFactory.createCompoundBorder(ComicBorder.windowBorder(palette, WINDOW_PADDING),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)));

            heading = new JLabel(title);
            heading.setFont(ResearchUiTypography.semiBold(15f));
            heading.setForeground(palette.getInk());

            JPanel fields = new JPanel();
            fields.setOpaque(false);
            fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
            fields.add(caption(NAME_LABEL, palette));
            fields.add(Box.createVerticalStrut(4));
            name.setText(initialName == null ? "" : initialName);
            name.setFont(ResearchUiTypography.regular(13f));
            name.setForeground(palette.getInk());
            name.setBorder(ComicBorder.roundedBorder(palette, 4));
            name.setAlignmentX(LEFT_ALIGNMENT);
            name.addActionListener(event -> confirm());
            fields.add(name);
            fields.add(Box.createVerticalStrut(10));
            fields.add(caption(COLOR_LABEL, palette));
            fields.add(Box.createVerticalStrut(4));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.setOpaque(false);
            row.setAlignmentX(LEFT_ALIGNMENT);
            for (ReaderTabGroup.Tint tint : ReaderTabGroup.Tint.values()) {
                Swatch swatch = new Swatch(tint, palette);
                swatches.put(tint, swatch);
                row.add(swatch);
            }
            fields.add(row);

            ok = ComposerButton.primary(null, "OK", palette.getNavigationBlue(), null);
            cancel = new ComposerButton(null, "Abbrechen", false);
            ok.addActionListener(event -> confirm());
            cancel.addActionListener(event -> cancel());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(ok);

            add(heading, BorderLayout.NORTH);
            add(fields, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            registerKeyboardAction(event -> cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        JLabel heading() {
            return heading;
        }

        JTextField nameField() {
            return name;
        }

        ComposerButton okButton() {
            return ok;
        }

        ComposerButton cancelButton() {
            return cancel;
        }

        JComponent swatch(ReaderTabGroup.Tint tint) {
            return swatches.get(tint);
        }

        ReaderTabGroup.Tint chosen() {
            return chosen;
        }

        private void choose(ReaderTabGroup.Tint tint) {
            chosen = tint;
            for (Swatch swatch : swatches.values()) {
                swatch.repaint();
            }
        }

        private void confirm() {
            finish(new Result(name.getText().trim(), chosen));
        }

        private void cancel() {
            finish(null);
        }

        private void finish(Result result) {
            if (!finished) {
                finished = true;
                done.accept(result);
            }
        }

        private static JLabel caption(String text, ComicPalette palette) {
            JLabel label = new JLabel(text);
            label.setFont(ResearchUiTypography.semiBold(12f));
            label.setForeground(palette.getInk());
            label.setAlignmentX(LEFT_ALIGNMENT);
            return label;
        }

        /** Ein rundes Farbfeld; das gewählte trägt einen blauen Ring wie die Fokusringe der App. */
        private final class Swatch extends JComponent {
            private static final long serialVersionUID = 1L;
            private static final int SIZE = 28;
            private final ReaderTabGroup.Tint tint;
            private final ComicPalette palette;

            Swatch(final ReaderTabGroup.Tint tint, ComicPalette palette) {
                this.tint = tint;
                this.palette = palette;
                setPreferredSize(new Dimension(SIZE, SIZE));
                setToolTipText(tint.label());
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            choose(tint);
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = ResearchUiPainter.prepare(graphics);
                try {
                    int size = Math.min(getWidth(), getHeight());
                    if (tint == chosen) {
                        g2.setColor(ResearchUiPalette.ACCENT_BLUE);
                        g2.setStroke(new BasicStroke(2.5f));
                        g2.drawOval(2, 2, size - 4, size - 4);
                    }
                    int inset = 6;
                    g2.setColor(tint.color());
                    g2.fillOval(inset, inset, size - 2 * inset, size - 2 * inset);
                    g2.setColor(palette.getInk());
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(inset, inset, size - 2 * inset, size - 2 * inset);
                } finally {
                    g2.dispose();
                }
            }
        }
    }
}
