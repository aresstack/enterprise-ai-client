package com.aresstack.enterpriseai.ui.comic.control;

import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The warm AMBER search-bar variant for in-content search ("find in page"), copied from AskAI's
 * {@code ComicFindBar} (arch), itself ported from MainframeMate's {@code FindBarPanel}: same geometry as
 * {@link ComicSearchBar}, MainframeMate's amber palette (border 0xCCCCCC, focused 0xF57C00; background 0xFAFAFA,
 * focused warm cream 0xFFF8E1) and MainframeMate's German texts. The ↵ ◀ ▶ ✕ glyphs are drawn
 * {@link ComposerIcons} instead of Unicode characters.
 *
 * <p><b>Navigation mode</b> ({@link #setStepSearchEnabled(boolean)}): disabled (default), Enter
 * highlights ALL matches at once; enabled, the first Enter triggers the search and the ↵ button
 * transforms into ◀ ▶ prev/next arrows that step through matches. The mode toggles via a
 * right-click context menu and can be persisted through {@link StepSearchModeListener}.</p>
 */
public class ComicFindBar extends ComicSearchBar {

    /** MainframeMate's amber-700 focus border. */
    public static final Color FIND_BORDER_FOCUSED = new Color(0xF57C00);
    private static final Color FIND_BORDER_NORMAL = new Color(0xCCCCCC);
    private static final Color FIND_BG_NORMAL = new Color(0xFAFAFA);
    private static final Color FIND_BG_FOCUSED = new Color(0xFFF8E1); // warm cream
    private static final Color GLYPH = new Color(0x888888);

    private final JButton enterButton; // ↵  (initial state)
    private final JButton prevButton;  // ◀  (after first search, step-search mode)
    private final JButton nextButton;  // ▶  (after first search, step-search mode)
    private final JButton clearButton; // ✕  (always visible)

    private boolean stepSearchEnabled;
    private boolean arrowsVisible;

    private final List<ActionListener> searchListeners = new ArrayList<ActionListener>();
    private ActionListener prevListener;
    private ActionListener nextListener;
    private StepSearchModeListener stepSearchModeListener;
    private FindBarExitListener exitListener;

    /** Callback for persisting the step-search preference. */
    public interface StepSearchModeListener {
        void onStepSearchModeChanged(boolean stepSearch);
    }

    /** Callback for leaving the find bar via keyboard. */
    public interface FindBarExitListener {
        /** UP arrow pressed → transfer focus to the first line of the content. */
        void onExitUp();

        /** DOWN arrow pressed → transfer focus to the last line of the content. */
        void onExitDown();

        /** Enter on an empty field or Escape → dismiss / leave the find bar. */
        void onDismiss();
    }

    public ComicFindBar(String placeholder) {
        this(placeholder, null);
    }

    public ComicFindBar(String placeholder, String tooltip) {
        super(placeholder, tooltip,
                FIND_BG_NORMAL, FIND_BG_FOCUSED,
                FIND_BORDER_NORMAL, FIND_BORDER_FOCUSED,
                GLYPH, new Color(0xAAAAAA), 10, 1f);

        // The inherited go button becomes the always-visible clear (✕) button.
        clearButton = getGoButton();
        clearButton.setText(null);
        clearButton.setIcon(ComposerIcons.close());
        clearButton.setToolTipText("Suchfeld leeren");
        for (ActionListener al : clearButton.getActionListeners()) {
            clearButton.removeActionListener(al);
        }
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setText("");
                resetToEnterButton();
                fireSearch(e);
            }
        });

        enterButton = makeNavButton(ComposerIcons.enter(), "Suche starten (Enter)");
        enterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (getText().trim().isEmpty()) {
                    if (exitListener != null) {
                        exitListener.onDismiss();
                    }
                    return;
                }
                fireSearch(e);
                showArrows();
            }
        });

        prevButton = makeNavButton(ComposerIcons.chevronLeft(), "Vorheriger Treffer");
        prevButton.setVisible(false);
        prevButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (prevListener != null) {
                    prevListener.actionPerformed(e);
                }
            }
        });

        nextButton = makeNavButton(ComposerIcons.chevronRight(), "Nächster Treffer");
        nextButton.setVisible(false);
        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (nextListener != null) {
                    nextListener.actionPerformed(e);
                }
            }
        });

        JPanel east = getEastPanel();
        east.removeAll();
        east.add(enterButton);
        east.add(prevButton);
        east.add(nextButton);
        east.add(clearButton);

        installContextMenu();
        installArrowKeyBindings();
    }

    /** Register a listener for keyboard-driven exits from the find bar. */
    public void setExitListener(FindBarExitListener listener) {
        this.exitListener = listener;
    }

    private JButton makeNavButton(Icon icon, String tooltip) {
        JButton btn = new JButton(icon) {
            @Override
            public Dimension getPreferredSize() {
                int side = Math.max(getTextField().getPreferredSize().height, 20);
                return new Dimension(side, side);
            }
        };
        btn.setForeground(GLYPH);
        btn.setMargin(new Insets(0, 2, 0, 2));
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    // ------------------------------------------------------------------ key bindings

    private void installArrowKeyBindings() {
        final JTextField tf = getTextField();
        InputMap im = tf.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = tf.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "findbar.exitUp");
        am.put("findbar.exitUp", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (exitListener != null) {
                    exitListener.onExitUp();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "findbar.exitDown");
        am.put("findbar.exitDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (exitListener != null) {
                    exitListener.onExitDown();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "findbar.prevOrCursor");
        am.put("findbar.prevOrCursor", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (arrowsVisible && prevListener != null) {
                    prevListener.actionPerformed(e);
                } else {
                    int pos = tf.getCaretPosition();
                    if (pos > 0) {
                        tf.setCaretPosition(pos - 1);
                    }
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "findbar.nextOrCursor");
        am.put("findbar.nextOrCursor", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (arrowsVisible && nextListener != null) {
                    nextListener.actionPerformed(e);
                } else {
                    int pos = tf.getCaretPosition();
                    if (pos < tf.getText().length()) {
                        tf.setCaretPosition(pos + 1);
                    }
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "findbar.dismiss");
        am.put("findbar.dismiss", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (exitListener != null) {
                    exitListener.onDismiss();
                }
            }
        });
    }

    // ------------------------------------------------------------------ search wiring

    /**
     * Register a search action. In non-step mode the action fires on every Enter; in step mode it
     * fires on the first Enter and the bar then switches to ◀ ▶ match navigation. ✕ fires it once more with an
     * empty text, so the owner can drop its highlights.
     */
    @Override
    public void addSearchAction(final ActionListener listener) {
        searchListeners.add(listener);
        getTextField().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (getText().trim().isEmpty()) {
                    if (exitListener != null) {
                        exitListener.onDismiss();
                    }
                    return;
                }
                listener.actionPerformed(e);
                showArrows();
            }
        });
    }

    public void setPrevAction(ActionListener listener) {
        this.prevListener = listener;
    }

    public void setNextAction(ActionListener listener) {
        this.nextListener = listener;
    }

    private void fireSearch(ActionEvent e) {
        for (ActionListener l : searchListeners) {
            l.actionPerformed(e);
        }
    }

    // ------------------------------------------------------------------ arrow/enter state

    /** Switch to ◀ ▶ navigation (only in step-search mode; callable externally on results). */
    public void showArrows() {
        if (!stepSearchEnabled || arrowsVisible) {
            return;
        }
        arrowsVisible = true;
        enterButton.setVisible(false);
        prevButton.setVisible(true);
        nextButton.setVisible(true);
        revalidate();
        repaint();
    }

    /** Reset to the ↵ button (hide the arrows), e.g. when the text is cleared. */
    public void resetToEnterButton() {
        arrowsVisible = false;
        enterButton.setVisible(true);
        prevButton.setVisible(false);
        nextButton.setVisible(false);
        revalidate();
        repaint();
    }

    public boolean isArrowsVisible() {
        return arrowsVisible;
    }

    // ------------------------------------------------------------------ step-search mode

    /** Enable or disable step-search (prev/next) mode. Default: {@code false}. */
    public void setStepSearchEnabled(boolean enabled) {
        this.stepSearchEnabled = enabled;
        if (!enabled) {
            resetToEnterButton();
        }
    }

    public boolean isStepSearchEnabled() {
        return stepSearchEnabled;
    }

    /** Register a callback for when the user toggles step-search via the context menu. */
    public void setStepSearchModeListener(StepSearchModeListener listener) {
        this.stepSearchModeListener = listener;
    }

    JButton enterButton() {
        return enterButton;
    }

    JButton prevButton() {
        return prevButton;
    }

    JButton nextButton() {
        return nextButton;
    }

    JButton clearButton() {
        return clearButton;
    }

    private void installContextMenu() {
        final JPopupMenu popup = new JPopupMenu();
        final JCheckBoxMenuItem stepItem = new JCheckBoxMenuItem("Treffer einzeln durchgehen");
        stepItem.setToolTipText("Aktiviert: Pfeil-Navigation von Treffer zu Treffer. "
                + "Deaktiviert: Alle Treffer gleichzeitig hervorheben.");
        stepItem.setSelected(stepSearchEnabled);
        stepItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stepSearchEnabled = stepItem.isSelected();
                if (!stepSearchEnabled) {
                    resetToEnterButton();
                }
                if (stepSearchModeListener != null) {
                    stepSearchModeListener.onStepSearchModeChanged(stepSearchEnabled);
                }
            }
        });
        popup.add(stepItem);

        MouseAdapter rightClickAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    stepItem.setSelected(stepSearchEnabled);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        addMouseListener(rightClickAdapter);
        getTextField().addMouseListener(rightClickAdapter);
    }
}
