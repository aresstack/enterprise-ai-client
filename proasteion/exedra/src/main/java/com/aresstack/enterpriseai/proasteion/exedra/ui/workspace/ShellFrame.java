package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.state.ApplicationStateService;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowHoldCloseButton;
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
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Das rahmenlose Hauptfenster: {@code setUndecorated(true)}, eine Tintenkontur als Fensterrand, dessen Polster
 * zugleich die Greifzone zum Vergrößern ist, die schlanke Kopfzeile der Arbeitsfläche als Zieh-Fläche und das
 * Comic-✕ ({@link ComicWindowHoldCloseButton}) ganz rechts darin: Hover zeigt Minimieren, ein Klick minimiert,
 * erst Gedrückthalten schließt. Die Ecken sind rund wie beim Windows-11-Rahmen des
 * askai-Fensters ({@link ComicWindowShape}), maximiert eckig. Schließen läuft über {@code WINDOW_CLOSING}, damit
 * die Fenster-Listener der Composition Root (Shutdown) wie gewohnt greifen. Braucht ein Display; der
 * Headless-Start und der Smoke-Test der Composition Root berühren diese Klasse nicht.
 */
public final class ShellFrame {

    static final int WINDOW_PADDING = 4;
    static final int RESIZE_GRIP = 6;
    static final String STATE_WINDOW_WIDTH = "window.width";
    static final String STATE_WINDOW_HEIGHT = "window.height";
    static final String STATE_WINDOW_X = "window.x";
    static final String STATE_WINDOW_Y = "window.y";
    static final String STATE_WINDOW_MAXIMIZED = "window.maximized";

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
        }, new Runnable() {
            @Override
            public void run() {
                frame.setState(Frame.ICONIFIED);
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
     * Stellt Größe, Lage und Maximierung aus dem Anwendungszustand wieder her (wie MainframeMate) und merkt sie
     * sich beim Schließen. Vor {@code setVisible(true)} aufrufen. Eine Lage, die auf keinem Bildschirm mehr
     * sichtbar ist (abgezogener Monitor), wird verworfen und das Fenster zentriert.
     */
    public static void rememberBounds(final JFrame frame, final ApplicationStateService state) {
        if (frame == null || state == null) {
            return;
        }
        int width = state.getInt(STATE_WINDOW_WIDTH, -1);
        int height = state.getInt(STATE_WINDOW_HEIGHT, -1);
        if (width > 0 && height > 0) {
            Dimension minimum = frame.getMinimumSize();
            frame.setSize(Math.max(width, minimum.width), Math.max(height, minimum.height));
            int x = state.getInt(STATE_WINDOW_X, Integer.MIN_VALUE);
            int y = state.getInt(STATE_WINDOW_Y, Integer.MIN_VALUE);
            Rectangle bounds = new Rectangle(x, y, frame.getWidth(), frame.getHeight());
            if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && onAnyScreen(bounds)) {
                frame.setLocation(x, y);
            } else {
                frame.setLocationRelativeTo(null);
            }
        }
        if (state.getBoolean(STATE_WINDOW_MAXIMIZED, false)) {
            ComicWindowDragger.maximize(frame);
        }
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveBounds(frame, state);
            }
        });
    }

    static void saveBounds(JFrame frame, ApplicationStateService state) {
        boolean maximized = ComicWindowDragger.isMaximized(frame);
        Rectangle normal = maximized ? ComicWindowDragger.restoredBounds(frame) : frame.getBounds();
        Map<String, String> entries = new LinkedHashMap<String, String>();
        if (normal != null) {
            entries.put(STATE_WINDOW_WIDTH, Integer.toString(normal.width));
            entries.put(STATE_WINDOW_HEIGHT, Integer.toString(normal.height));
            entries.put(STATE_WINDOW_X, Integer.toString(normal.x));
            entries.put(STATE_WINDOW_Y, Integer.toString(normal.y));
        }
        entries.put(STATE_WINDOW_MAXIMIZED, Boolean.toString(maximized));
        state.putAllAndSave(entries);
    }

    /** Ob die Titelzeile des Fensters auf einem der Bildschirme greifbar bleibt. */
    private static boolean onAnyScreen(Rectangle bounds) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        Rectangle grip = new Rectangle(bounds.x, bounds.y, bounds.width, Math.min(bounds.height, 40));
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(grip)) {
                return true;
            }
        }
        return false;
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
        return content(title, workspace, palette, closeAction, new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    /**
     * @param closeAction    was Gedrückthalten des ✕ tut (im Fenster: {@code WINDOW_CLOSING} auslösen)
     * @param minimizeAction was ein Klick auf das ✕ tut (im Fenster: ikonifizieren)
     */
    public static JPanel content(String title, ChatWorkspacePanel workspace, ComicPalette palette,
                                 Runnable closeAction, Runnable minimizeAction) {
        if (workspace == null || palette == null || closeAction == null || minimizeAction == null) {
            throw new IllegalArgumentException("workspace, palette, closeAction and minimizeAction must not be null");
        }
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.windowBorder(palette, WINDOW_PADDING));
        content.add(workspace, BorderLayout.CENTER);
        workspace.setWindowTitle(title);
        workspace.setWindowControls(new ComicWindowHoldCloseButton(palette, minimizeAction, closeAction, 24));
        ComicWindowDragger.install(workspace.topBar());
        ComicWindowResizer.install(content, RESIZE_GRIP);
        return content;
    }
}
