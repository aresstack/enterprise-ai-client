package com.aresstack.enterpriseai.ui.comic.control;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Lets a FRAMELESS window be moved by dragging a handle component (the slim comic top bar): a left
 * press on the handle's free area remembers the offset, dragging moves the window ancestor along,
 * a double-click toggles the frame between maximized and normal. Child controls (buttons, pills)
 * keep their own mouse events — only the handle itself drags.
 */
public final class ComicWindowDragger {

    private ComicWindowDragger() {
    }

    /** Install drag-to-move (and double-click to maximize) on {@code handle}. */
    public static void install(final JComponent handle) {
        if (handle == null) {
            throw new IllegalArgumentException("handle must not be null");
        }
        MouseAdapter adapter = new MouseAdapter() {
            private Point pressedOnScreen;
            private Point windowOrigin;

            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                Window window = SwingUtilities.getWindowAncestor(handle);
                if (window == null || isMaximized(window)) {
                    pressedOnScreen = null;
                    return;
                }
                pressedOnScreen = event.getLocationOnScreen();
                windowOrigin = window.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                Window window = SwingUtilities.getWindowAncestor(handle);
                if (pressedOnScreen == null || window == null) {
                    return;
                }
                Point now = event.getLocationOnScreen();
                window.setLocation(windowOrigin.x + now.x - pressedOnScreen.x,
                        windowOrigin.y + now.y - pressedOnScreen.y);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                pressedOnScreen = null;
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    toggleMaximized(SwingUtilities.getWindowAncestor(handle));
                }
            }
        };
        handle.addMouseListener(adapter);
        handle.addMouseMotionListener(adapter);
    }

    /** Whether the window is a frame that is currently maximized. */
    public static boolean isMaximized(Window window) {
        return window instanceof Frame
                && (((Frame) window).getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    /** Maximize a normal frame, restore a maximized one; other windows are left alone. */
    public static void toggleMaximized(Window window) {
        if (!(window instanceof Frame)) {
            return;
        }
        Frame frame = (Frame) window;
        if (isMaximized(frame)) {
            frame.setExtendedState(frame.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
        } else {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
    }
}
