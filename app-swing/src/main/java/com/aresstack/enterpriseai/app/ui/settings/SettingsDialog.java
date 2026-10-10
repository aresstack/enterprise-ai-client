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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Der modale Rahmen um {@link SettingsPanel}: rahmenlos wie das Hauptfenster (Tintenkontur als Rand und
 * Greifzone, die Überschrift zieht, das Comic-✕ rechts oben bricht ab), Escape bricht ab, Schließen des
 * Fensters ebenfalls. {@link #show} blockiert auf dem EDT und liefert das gespeicherte Formular
 * oder {@code null}.
 */
public final class SettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int WINDOW_PADDING = 4;
    static final int RESIZE_GRIP = 6;
    static final String CLOSE_TOOLTIP = "Abbrechen";

    private final SettingsPanel panel;
    private SettingsForm result;

    private SettingsDialog(Window owner, SettingsForm initial, List<String> problems, SettingsPanel.Mode mode,
                           SettingsDialogActions actions, ComicPalette palette) {
        super(owner, SettingsPanel.TITLE, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.panel = new SettingsPanel(initial, problems, mode, actions, palette);
        panel.setOnSaved(new Consumer<SettingsForm>() {
            @Override
            public void accept(SettingsForm saved) {
                result = saved;
                dispose();
            }
        });
        panel.setOnCancel(new Runnable() {
            @Override
            public void run() {
                result = null;
                dispose();
            }
        });
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.windowBorder(palette, WINDOW_PADDING));
        content.add(panel, BorderLayout.CENTER);
        panel.setWindowControls(new ComicWindowCloseButton(palette, new Runnable() {
            @Override
            public void run() {
                result = null;
                dispose();
            }
        }, CLOSE_TOOLTIP, 24));
        ComicWindowDragger.install(panel.header());
        ComicWindowResizer.install(content, RESIZE_GRIP);
        content.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = null;
                dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                result = null;
                dispose();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                panel.focusFirstField();
            }
        });
        setContentPane(content);
        ComicWindowShape.install(this, content, palette, WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        setMinimumSize(new Dimension(640, 520));
        setSize(new Dimension(800, 680));
        setLocationRelativeTo(owner);
    }

    /**
     * Zeigt den Dialog modal (nur auf dem EDT) und liefert das gespeicherte Formular oder {@code null} beim
     * Abbrechen. Der Dialog hat bereits gespeichert, wenn ein Formular zurückkommt.
     */
    public static SettingsForm show(Window owner, SettingsForm initial, List<String> problems,
                                    SettingsPanel.Mode mode, SettingsDialogActions actions, ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        SettingsDialog dialog = new SettingsDialog(owner, initial, problems, mode, actions, colors);
        dialog.setVisible(true);
        return dialog.result;
    }

    public SettingsPanel panel() {
        return panel;
    }
}
