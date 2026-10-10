package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowCloseButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowResizer;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowShape;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Der modale Rahmen um {@link SourcePanel}: rahmenlos und rund wie der Einstellungen-Dialog (Tintenkontur als Rand
 * und Greifzone, die Überschrift zieht, das Comic-✕ bricht ab), Escape und Schließen brechen ab. {@link #show}
 * blockiert auf dem EDT und liefert, wie der Dialog endete.
 */
public final class SourceDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int WINDOW_PADDING = 4;
    static final int RESIZE_GRIP = 6;

    /** Wie der Dialog endete und, nach dem Speichern, die gespeicherte Quelle. */
    public static final class Result {
        private final SourcePanel.Outcome outcome;
        private final SourceForm source;

        Result(SourcePanel.Outcome outcome, SourceForm source) {
            this.outcome = outcome;
            this.source = source;
        }

        public SourcePanel.Outcome outcome() {
            return outcome;
        }

        /** Die gespeicherte Quelle, sonst {@code null}. */
        public SourceForm source() {
            return source;
        }
    }

    private final SourcePanel panel;
    private Result result = new Result(SourcePanel.Outcome.CANCELLED, null);

    private SourceDialog(Window owner, SourceForm initial, String originalId, SourceActions actions,
                         ComicPalette palette) {
        super(owner, SourcePanel.titleFor(initial, originalId), ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.panel = new SourcePanel(initial, originalId, actions, palette);
        panel.setListener((outcome, source) -> {
            result = new Result(outcome, source);
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
     */
    public static Result show(Window owner, SourceForm initial, String originalId, SourceActions actions,
                              ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        SourceDialog dialog = new SourceDialog(owner, initial, originalId, actions, colors);
        dialog.setVisible(true);
        return dialog.result;
    }

    public SourcePanel panel() {
        return panel;
    }
}
