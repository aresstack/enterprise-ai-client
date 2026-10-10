package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowCloseButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowResizer;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowShape;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowEvent;

/**
 * Das rahmenlose Hauptfenster: {@code setUndecorated(true)}, eine Tintenkontur als Fensterrand, dessen Polster
 * zugleich die Greifzone zum Vergrößern ist, die schlanke Kopfzeile der Arbeitsfläche als Zieh-Fläche und das
 * Comic-✕ ({@link ComicWindowCloseButton}) ganz rechts darin. Die Ecken sind rund wie beim Windows-11-Rahmen des
 * askai-Fensters ({@link ComicWindowShape}), maximiert eckig. Schließen läuft über {@code WINDOW_CLOSING}, damit
 * die Fenster-Listener der Composition Root (Shutdown) wie gewohnt greifen. Braucht ein Display; der
 * Headless-Start und der Smoke-Test der Composition Root berühren diese Klasse nicht.
 */
public final class ShellFrame {

    static final int WINDOW_PADDING = 4;
    static final int RESIZE_GRIP = 6;
    static final String CLOSE_TOOLTIP = "Schließen";

    private ShellFrame() {
    }

    public static JFrame create(String title, ChatWorkspacePanel workspace, ComicPalette palette) {
        if (workspace == null || palette == null) {
            throw new IllegalArgumentException("workspace and palette must not be null");
        }
        ComicTheme.installMenuDefaults(palette);
        final JFrame frame = new JFrame(title == null ? "" : title);
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = content(frame.getTitle(), workspace, palette, new Runnable() {
            @Override
            public void run() {
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            }
        });
        frame.setContentPane(content);
        ComicWindowShape.install(frame, content, palette, WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        frame.setMinimumSize(new Dimension(640, 480));
        frame.setSize(new Dimension(1040, 720));
        frame.setLocationRelativeTo(null);
        return frame;
    }

    /**
     * Der Fensterinhalt ohne Fenster: Tintenrand mit Greifzone, Arbeitsfläche mit Titel, ✕ in der Kopfzeile,
     * Ziehen an der Kopfzeile. {@link #create} setzt ihn in das rahmenlose Fenster; Tests und Screenshots
     * rendern ihn headless.
     *
     * @param closeAction was das ✕ tut (im Fenster: {@code WINDOW_CLOSING} auslösen)
     */
    public static JPanel content(String title, ChatWorkspacePanel workspace, ComicPalette palette,
                                 Runnable closeAction) {
        if (workspace == null || palette == null || closeAction == null) {
            throw new IllegalArgumentException("workspace, palette and closeAction must not be null");
        }
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.windowBorder(palette, WINDOW_PADDING));
        content.add(workspace, BorderLayout.CENTER);
        workspace.setWindowTitle(title);
        workspace.setWindowControls(new ComicWindowCloseButton(palette, closeAction, CLOSE_TOOLTIP, 24));
        ComicWindowDragger.install(workspace.topBar());
        ComicWindowResizer.install(content, RESIZE_GRIP);
        return content;
    }
}
