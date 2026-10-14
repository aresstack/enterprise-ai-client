package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.exedra.ui.icon.ApplicationIcons;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowCloseButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowResizer;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowShape;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;

import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Der modale Rahmen um {@link SourcePanel}: rahmenlos und rund wie der Einstellungen-Dialog (Tintenkontur als Rand
 * und Greifzone, die Überschrift zieht, das Comic-✕ bricht ab), Escape und Schließen brechen ab. {@link #show}
 * blockiert auf dem EDT und liefert die gespeicherte Quelle; {@link #confirmRemove} ist die kleine Rückfrage vor
 * dem Entfernen einer Quelle.
 */
public final class SourceDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int WINDOW_PADDING = 4;
    static final int RESIZE_GRIP = 6;

    private final SourcePanel panel;
    private SourceDefinition result;

    private SourceDialog(Window owner, SourceDefinition initial, String originalId, List<KnowledgeSourceType> types,
                         SourceActions actions, ComicPalette palette) {
        super(owner, originalId == null ? "Quelle hinzufügen" : "Quelle bearbeiten", ModalityType.APPLICATION_MODAL);
        ApplicationIcons.apply(this);
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.panel = new SourcePanel(initial, originalId, types, actions, palette);
        panel.setListener((outcome, source) -> {
            result = outcome == SourcePanel.Outcome.SAVED ? source : null;
            dispose();
        });
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.windowBorder(palette, WINDOW_PADDING));
        content.add(panel, BorderLayout.CENTER);
        panel.setWindowControls(new ComicWindowCloseButton(palette, panel::cancel, SourcePanel.CANCEL_LABEL, 24));
        ComicWindowDragger.install(panel.header());
        ComicWindowResizer.install(content, RESIZE_GRIP);
        content.registerKeyboardAction(event -> panel.cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.cancel();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                panel.focusFirstField();
            }
        });
        setContentPane(content);
        ComicWindowShape.install(this, content, palette, WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        setMinimumSize(new Dimension(560, 460));
        setSize(new Dimension(720, 600));
        setLocationRelativeTo(owner);
    }

    /**
     * Zeigt den Dialog modal (nur auf dem EDT).
     *
     * @param originalId die ID in der Datei beim Bearbeiten, {@code null} beim Hinzufügen
     * @return die gespeicherte Quelle; {@code null}, wenn abgebrochen
     */
    public static SourceDefinition show(Window owner, SourceDefinition initial, String originalId,
                                        List<KnowledgeSourceType> types, SourceActions actions,
                                        ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        SourceDialog dialog = new SourceDialog(owner, initial, originalId, types, actions, colors);
        dialog.setVisible(true);
        return dialog.result;
    }

    /**
     * Die kleine Rückfrage vor dem Entfernen, im selben rahmenlosen Stil (nur auf dem EDT).
     *
     * @return {@code true}, wenn der Benutzer „Entfernen“ wählt
     */
    public static boolean confirmRemove(Window owner, String sourceId, String typeName, ComicPalette palette) {
        return confirm(owner, "Quelle entfernen", "Quelle „" + sourceId + "“ entfernen?",
                (typeName == null ? "" : typeName + ": ")
                        + "Die Quelle verschwindet aus der Liste und aus der Konfiguration, ihr Index wird gelöscht. "
                        + "Die Zeilen in der Datei werden nur auskommentiert.",
                "Entfernen", palette);
    }

    /**
     * A small question before a risky step, in the same undecorated comic style (EDT only): heading, text, a red
     * confirm button and „Abbrechen“; Escape cancels.
     *
     * @return {@code true} if the user chose the confirm button
     */
    public static boolean confirm(Window owner, String title, String question, String text, String confirmLabel,
                                  ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        final boolean[] confirmed = {false};
        final JDialog dialog = new JDialog(owner, title, ModalityType.APPLICATION_MODAL);
        ApplicationIcons.apply(dialog);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(colors.getSurface());
        content.setBorder(BorderFactory.createCompoundBorder(ComicBorder.windowBorder(colors, WINDOW_PADDING),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel heading = new JLabel(question);
        heading.setFont(ResearchUiTypography.semiBold(15f));
        heading.setForeground(colors.getInk());
        JLabel body = new JLabel(FormRows.html(text));
        body.setForeground(colors.getInk());
        ComposerButton accept = ComposerButton.primary(null, confirmLabel, ResearchUiPalette.DANGER_RED, null);
        ComposerButton cancel = new ComposerButton(null, SourcePanel.CANCEL_LABEL, false);
        accept.addActionListener(event -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        cancel.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(accept);
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        content.registerKeyboardAction(event -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        ComicWindowDragger.install(heading);
        dialog.setContentPane(content);
        dialog.pack();
        ComicWindowShape.install(dialog, content, colors, WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        dialog.setLocationRelativeTo(owner);
        dialog.getRootPane().setDefaultButton(null);
        dialog.setVisible(true);
        return confirmed[0];
    }

    public SourcePanel panel() {
        return panel;
    }
}
