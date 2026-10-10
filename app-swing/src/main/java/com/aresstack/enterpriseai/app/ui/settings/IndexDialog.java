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
 * Der modale Rahmen um {@link IndexPanel}, geöffnet über „Index …“ im Drawer-Reiter „Wissensquellen“: rahmenlos
 * und rund wie der Einstellungen-Dialog (Tintenkontur als Rand und Greifzone, die Überschrift zieht, das Comic-✕
 * bricht ab), Escape und Schließen brechen ab. {@link #show} blockiert auf dem EDT und liefert den gespeicherten
 * Stand oder {@code null}.
 */
public final class IndexDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int WINDOW_PADDING = 4;
    static final int RESIZE_GRIP = 6;

    private final IndexPanel panel;
    private IndexForm result;

    private IndexDialog(Window owner, IndexForm initial, IndexActions actions, ComicPalette palette) {
        super(owner, IndexPanel.TITLE, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.panel = new IndexPanel(initial, actions, palette);
        panel.setListener(saved -> {
            result = saved;
            dispose();
        });
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.windowBorder(palette, WINDOW_PADDING));
        content.add(panel, BorderLayout.CENTER);
        panel.setWindowControls(new ComicWindowCloseButton(palette, panel::cancel, IndexPanel.CANCEL_LABEL, 24));
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
        setMinimumSize(new Dimension(520, 300));
        setSize(new Dimension(640, 340));
        setLocationRelativeTo(owner);
    }

    /**
     * Zeigt den Dialog modal (nur auf dem EDT) und liefert den gespeicherten Stand oder {@code null} beim
     * Abbrechen. Der Dialog hat bereits gespeichert, wenn ein Stand zurückkommt.
     */
    public static IndexForm show(Window owner, IndexForm initial, IndexActions actions, ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        IndexDialog dialog = new IndexDialog(owner, initial, actions, colors);
        dialog.setVisible(true);
        return dialog.result;
    }

    public IndexPanel panel() {
        return panel;
    }
}
