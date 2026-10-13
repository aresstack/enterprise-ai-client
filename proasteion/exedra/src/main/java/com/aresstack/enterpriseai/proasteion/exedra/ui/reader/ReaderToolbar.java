package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.CopyrightFooter;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Die eine Zeile unter dem Lesebereich: links die Werkzeuge (heute die Lupe), in der Mitte Zurück und Vor, rechts
 * „© 2026 AresStack“ an derselben Stelle wie unter dem Chat, davor die rechten Werkzeuge (die Büroklammer, #99). Klick auf die Lupe (oder Strg+F) klappt die Suche in derselben Zeile auf:
 * Feld, „aktueller Treffer / Gesamtzahl“, vorheriger, nächster, Schließen; Branding und Zurück/Vor sind solange
 * ausgeblendet.
 * Enter springt zum nächsten Treffer (bei neuem Text: neue Suche), Shift+Enter zum vorigen, Esc klappt zu.
 * Amber ist nur der Akzent der offenen Suche (Lupe und Unterkante des Felds).
 *
 * <p>Gesucht wird mit dem {@link ReaderFind} des aktiven Reiters; Begriff und Auf/Zu stehen in dessen
 * {@link FindState}, so behält jeder Reiter seine Suche.</p>
 */
final class ReaderToolbar extends JPanel {

    static final String FIND_PLACEHOLDER = "Im Dokument suchen…";
    static final String FIND_TOOLTIP = "Im Dokument suchen (Strg+F)";
    /** Quadratische Knöpfe („quadratischer Lupen-Button“). */
    private static final int BUTTON_SIZE = 22;

    /** Suchzustand eines Reiters. */
    static final class FindState {
        boolean open;
        String query = "";
        /** Der Text, nach dem zuletzt gesucht wurde; {@code null}, solange nichts markiert ist. */
        String searched;
    }

    private final ComposerButton findButton;
    private final JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    /** Tools directly left of the branding; they never move it (#99). */
    private final JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    private final JPanel search = new JPanel(new BorderLayout(4, 0));
    private final JTextField field = new PlaceholderField(FIND_PLACEHOLDER);
    private final JLabel counter = new JLabel();
    private final ComposerButton previousButton;
    private final ComposerButton nextButton;
    private final ComposerButton closeButton;
    private final JPanel branding = new JPanel(new BorderLayout());
    private final JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
    private final ComposerButton backButton;
    private final ComposerButton forwardButton;
    private ReaderFind find;
    private FindState state;
    private boolean syncing;

    ReaderToolbar() {
        super();
        setLayout(new RowLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, CopyrightFooter.SIDE_MARGIN, CopyrightFooter.BOTTOM_MARGIN,
                CopyrightFooter.SIDE_MARGIN));

        findButton = small(ComposerIcons.magnifier(), FIND_TOOLTIP);
        findButton.setAccent(ResearchUiPalette.FIND_AMBER);
        findButton.addActionListener(event -> {
            if (state != null && state.open) {
                close();
            } else {
                open();
            }
        });
        tools.setOpaque(false);
        tools.add(findButton);
        trailing.setOpaque(false);

        field.setFont(ResearchUiTypography.regular(12f));
        field.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        field.setOpaque(false);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ResearchUiPalette.FIND_AMBER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                remember();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                remember();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                remember();
            }
        });
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "reader.find.next", this::next);
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "reader.find.previous",
                this::previous);
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "reader.find.close", this::close);

        counter.setFont(ResearchUiTypography.regular(11f));
        counter.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        previousButton = small(ComposerIcons.chevronUp(), "Vorheriger Treffer (Shift+Enter)");
        previousButton.addActionListener(event -> previous());
        nextButton = small(ComposerIcons.chevronDown(), "Nächster Treffer (Enter)");
        nextButton.addActionListener(event -> next());
        closeButton = small(ComposerIcons.close(), "Suche schließen (Esc)");
        closeButton.addActionListener(event -> close());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        controls.setOpaque(false);
        controls.add(counter);
        controls.add(previousButton);
        controls.add(nextButton);
        controls.add(closeButton);
        search.setOpaque(false);
        search.add(field, BorderLayout.CENTER);
        search.add(controls, BorderLayout.EAST);

        // Unten bündig wie unter dem Chat: das Branding steht pixelgenau dort, auch wenn die Zeile höher ist.
        branding.setOpaque(false);
        branding.add(new CopyrightFooter(), BorderLayout.SOUTH);

        backButton = small(ComposerIcons.chevronLeft(), "Zurück (Maustaste Zurück)");
        forwardButton = small(ComposerIcons.chevronRight(), "Vor (Maustaste Vor)");
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        navigation.setOpaque(false);
        navigation.add(backButton);
        navigation.add(forwardButton);

        add(tools);
        add(navigation);
        add(search);
        add(trailing);
        add(branding);
        expand(false);
        findButton.setEnabled(false);
    }

    /**
     * Zeigt die Suche des aktiven Reiters ({@code null}: der Reiter hat nichts zum Durchsuchen, die Lupe ist aus).
     * Mit demselben Paar wie zuletzt passiert nichts.
     */
    void bind(ReaderFind find, FindState state) {
        if (find == this.find && state == this.state) {
            return;
        }
        this.find = find;
        this.state = find == null ? null : state;
        findButton.setEnabled(this.state != null);
        syncing = true;
        field.setText(this.state == null ? "" : this.state.query);
        syncing = false;
        expand(this.state != null && this.state.open);
        updateCounter();
    }

    /** Klappt die Suche auf und setzt den Cursor ins Feld (Strg+F). */
    void open() {
        if (state == null) {
            return;
        }
        state.open = true;
        expand(true);
        field.requestFocusInWindow();
        field.selectAll();
    }

    /** Klappt die Suche zu: Markierungen weg, Begriff bleibt im Reiter. */
    void close() {
        if (state == null) {
            return;
        }
        state.open = false;
        state.searched = null;
        find.clear();
        expand(false);
        updateCounter();
    }

    /** Enter: neuer Text sucht neu, derselbe Text springt zum nächsten Treffer. */
    void next() {
        step(true);
    }

    /** Shift+Enter: neuer Text sucht neu, derselbe Text springt zum vorigen Treffer. */
    void previous() {
        step(false);
    }

    /** Sucht {@code query} im aktiven Reiter, als hätte man es eingetippt und Enter gedrückt. */
    int searchFor(String query) {
        if (state == null) {
            return 0;
        }
        if (!state.open) {
            state.open = true;
            expand(true);
        }
        field.setText(query == null ? "" : query);
        state.searched = null;
        next();
        return find.hitCount();
    }

    /**
     * Legt ein weiteres Werkzeug rechts neben die Lupe (gleiche Größe); es tritt zurück, solange die Suche offen ist.
     */
    ComposerButton addTool(javax.swing.Icon icon, String tooltip) {
        ComposerButton tool = small(icon, tooltip);
        tools.add(tool);
        tool.setVisible(state == null || !state.open);
        revalidate();
        return tool;
    }

    /** Puts another control (e.g. the speaking-rate slider) to the right of the tools, at button height. */
    <T extends JComponent> T addTool(T tool) {
        tools.add(tool);
        tool.setVisible(state == null || !state.open);
        revalidate();
        return tool;
    }

    /**
     * Legt ein Werkzeug rechts in die Zeile, unmittelbar links vor das Branding (gleiche Größe wie links); es tritt
     * wie die linken Werkzeuge zurück, solange die Suche offen ist, und verschiebt das Branding nie.
     */
    ComposerButton addTrailingTool(javax.swing.Icon icon, String tooltip) {
        ComposerButton tool = small(icon, tooltip);
        trailing.add(tool);
        tool.setVisible(state == null || !state.open);
        revalidate();
        return tool;
    }

    /** Height of the toolbar buttons, for controls that sit beside them. */
    static int buttonSize() {
        return BUTTON_SIZE;
    }

    /** Client-Property eines Werkzeugs, das gerade nichts zu tun hat (z. B. die Büroklammer ohne Anhänge). */
    private static final String HIDDEN_TOOL = "reader.toolbar.hiddenTool";

    /**
     * Blendet ein Werkzeug aus {@link #addTool} ein oder aus; ein ausgeblendetes kommt auch nach dem Schließen der
     * Suche nicht wieder.
     */
    void showTool(JComponent tool, boolean shown) {
        tool.putClientProperty(HIDDEN_TOOL, shown ? null : Boolean.TRUE);
        tool.setVisible(shown && (state == null || !state.open));
        revalidate();
        repaint();
    }

    /** Was Zurück und Vor auslösen. */
    void setNavigation(Runnable back, Runnable forward) {
        backButton.addActionListener(event -> back.run());
        forwardButton.addActionListener(event -> forward.run());
    }

    /** Graut Zurück/Vor aus, wenn es nichts zum Zurück- oder Vorgehen gibt. */
    void updateNavigation(boolean canBack, boolean canForward) {
        backButton.setEnabled(canBack);
        forwardButton.setEnabled(canForward);
    }

    ComposerButton backButton() {
        return backButton;
    }

    ComposerButton forwardButton() {
        return forwardButton;
    }

    boolean isNavigationVisible() {
        return navigation.isVisible();
    }

    boolean isSearchOpen() {
        return search.isVisible();
    }

    boolean isBrandingVisible() {
        return branding.isVisible();
    }

    String counterText() {
        return counter.getText();
    }

    ComposerButton findButton() {
        return findButton;
    }

    JTextField field() {
        return field;
    }

    JPanel tools() {
        return tools;
    }

    JPanel trailingTools() {
        return trailing;
    }

    JComponent brandingArea() {
        return branding;
    }

    JComponent navigationArea() {
        return navigation;
    }

    // ------------------------------------------------------------------ intern

    private void step(boolean forward) {
        if (state == null) {
            return;
        }
        String query = field.getText();
        if (query.trim().isEmpty()) {
            find.clear();
            state.searched = null;
        } else if (query.equals(state.searched) && find.hitCount() > 0) {
            if (forward) {
                find.next();
            } else {
                find.previous();
            }
        } else {
            find.search(query);
            state.searched = query;
        }
        updateCounter();
    }

    private void remember() {
        if (!syncing && state != null) {
            state.query = field.getText();
        }
    }

    private void expand(boolean searchOpen) {
        search.setVisible(searchOpen);
        branding.setVisible(!searchOpen);
        navigation.setVisible(!searchOpen);
        trailing.setVisible(!searchOpen);
        findButton.setEmphasized(searchOpen);
        // weitere Werkzeuge treten zurück, solange die Suche die Zeile braucht
        java.util.List<java.awt.Component> all = new java.util.ArrayList<java.awt.Component>(
                java.util.Arrays.asList(tools.getComponents()));
        all.addAll(java.util.Arrays.asList(trailing.getComponents()));
        for (java.awt.Component tool : all) {
            if (tool != findButton) {
                tool.setVisible(!searchOpen && !(tool instanceof javax.swing.JComponent
                        && Boolean.TRUE.equals(((javax.swing.JComponent) tool).getClientProperty(HIDDEN_TOOL))));
            }
        }
        revalidate();
        repaint();
    }

    private void updateCounter() {
        boolean searched = state != null && state.searched != null && find != null;
        counter.setText(!searched ? "" : find.hitCount() == 0 ? "0 / 0"
                : (find.currentIndex() + 1) + " / " + find.hitCount());
        previousButton.setEnabled(searched && find.hitCount() > 0);
        nextButton.setEnabled(searched && find.hitCount() > 0);
    }

    private static ComposerButton small(javax.swing.Icon icon, String tooltip) {
        ComposerButton button = ComposerButton.iconButton(icon, tooltip);
        button.setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        return button;
    }

    private static void bind(JComponent component, KeyStroke key, String name, final Runnable action) {
        component.getInputMap(WHEN_FOCUSED).put(key, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    /**
     * Werkzeuge links, Zurück/Vor in der Mitte der ganzen Zeile, Branding rechts (je in voller Zeilenhöhe, das
     * Branding unten bündig), die rechten Werkzeuge unmittelbar links davor; das Branding steht immer an
     * {@code right - Breite}, gleich ob rechte Werkzeuge sichtbar sind. Die offene Suche füllt alles rechts der
     * Werkzeuge.
     */
    private final class RowLayout implements java.awt.LayoutManager {
        private static final int GAP = 4;

        @Override
        public void addLayoutComponent(String name, java.awt.Component comp) {
        }

        @Override
        public void removeLayoutComponent(java.awt.Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container parent) {
            java.awt.Insets insets = parent.getInsets();
            int width = 0;
            int height = 0;
            for (java.awt.Component child : parent.getComponents()) {
                if (child.isVisible()) {
                    Dimension preferred = child.getPreferredSize();
                    width += preferred.width + GAP;
                    height = Math.max(height, preferred.height);
                }
            }
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        private boolean anyVisible(java.awt.Container group) {
            for (java.awt.Component child : group.getComponents()) {
                if (child.isVisible()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Dimension minimumLayoutSize(java.awt.Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(java.awt.Container parent) {
            java.awt.Insets insets = parent.getInsets();
            int left = insets.left;
            int right = parent.getWidth() - insets.right;
            int top = insets.top;
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int toolsWidth = tools.getPreferredSize().width;
            tools.setBounds(left, top, toolsWidth, height);
            int brandingWidth = branding.getPreferredSize().width;
            branding.setBounds(right - brandingWidth, top, brandingWidth, height);
            int start = left + toolsWidth + GAP;
            search.setBounds(start, top, Math.max(0, right - start), height);
            int end = (branding.isVisible() ? right - brandingWidth : right) - GAP;
            int trailingWidth = trailing.isVisible() && anyVisible(trailing) ? trailing.getPreferredSize().width : 0;
            trailing.setBounds(end - trailingWidth, top, trailingWidth, height);
            if (trailingWidth > 0) {
                end -= trailingWidth + GAP;
            }
            int navigationWidth = navigation.getPreferredSize().width;
            int x = (parent.getWidth() - navigationWidth) / 2;
            x = Math.max(start, Math.min(x, end - navigationWidth));
            navigation.setBounds(x, top, navigationWidth, height);
        }
    }

    /** Ein flaches Textfeld, das leer den grauen Platzhalter zeigt. */
    private static final class PlaceholderField extends JTextField {
        private final String placeholder;

        PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                java.awt.Insets insets = getInsets();
                int baseline = insets.top + (getHeight() - insets.top - insets.bottom
                        - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
                g2.drawString(placeholder, insets.left, baseline);
                g2.dispose();
            }
        }
    }
}
