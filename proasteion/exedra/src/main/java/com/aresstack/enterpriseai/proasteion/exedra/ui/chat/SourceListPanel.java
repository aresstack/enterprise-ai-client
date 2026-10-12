package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthBoundedBubble;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Die ein- und ausklappbare Quellenliste unter einer Assistentenantwort (AP22): eine Comic-Platte mit
 * Petrol-Streifen (Farbe des Assistenten), ein Schalter {@code Quellen (n)} und, aufgeklappt, je Quelle eine
 * Zeile mit Nummer, Titel und Überschrift sowie darunter Ort, Stand, Score und die Ränge beider Suchpfade.
 *
 * <p>Ort und Details stehen in einem markierbaren Textfeld, damit sich eine Adresse kopieren lässt. Das Layout
 * ist bewusst von Hand gesetzt ({@link WidthAwareHeight}): die Höhe hängt von der Breite ab, in der die
 * Transcript-Zeile die Platte setzt, und wird deterministisch für genau diese Breite berechnet (wie bei der
 * Sprechblase). Vorbild ist die Aktionszeile unter einer Assistentenkarte in askai-java8
 * ({@code BubbleTranscriptPanel.appendActionButtons}), hier mit Inhalt statt Knöpfen.
 */
public final class SourceListPanel extends ComicSectionPanel implements WidthBoundedBubble, WidthAwareHeight {

    static final String TOGGLE_PREFIX = "Quellen";
    static final String MENU_OPEN = "Im Lesebereich öffnen";
    static final String MENU_FAVORITE = "Als Favorit speichern";
    static final String MENU_COPY_BOOKMARK = "Bookmark kopieren";
    static final String MENU_COPY_ADDRESS = "Adresse kopieren";
    static final String MENU_OPEN_ALL = "Alle Quellen im Lesebereich öffnen";
    static final String MENU_FAVORITE_ALL = "Alle Quellen als Favoriten speichern";

    /**
     * What the source list may do with its sources: open them in the reader and save them as favorites. The shell
     * wires this to the existing reader and favorites; without it the list only shows and copies.
     */
    public interface Actions {

        /** Return whether the source carries the stable ids the reader needs (older chats may lack them). */
        boolean canOpen(SourceReference source);

        /** Open the source in the internal reader (never a browser). */
        void open(SourceReference source);

        /** Open every openable source in the internal reader. */
        void openAll(List<SourceReference> sources);

        /** Return whether favorites are available at all. */
        boolean canFavorite();

        /** Save one source as a favorite; return a short note for the list. */
        String saveFavorite(SourceReference source);

        /** Save all openable sources into a new favorites folder; return a short note for the list. */
        String saveAllFavorites(List<SourceReference> sources);

        /** Return the bookmark URI of the source ({@code confluence://...}) or empty without a valid resource id. */
        String bookmarkOf(SourceReference source);
    }

    private static final int MINIMUM_WIDTH = 160;
    private static final int NATURAL_WIDTH_CAP = 720;
    private static final int ROW_GAP = 8;
    private static final int LINE_GAP = 2;

    private final List<SourceReference> sources;
    private final ComicToggleButton toggle;
    private final List<JLabel> titles = new ArrayList<JLabel>();
    private final List<JTextArea> details = new ArrayList<JTextArea>();
    private final JLabel note = new JLabel();
    private Actions actions;
    private Consumer<String> clipboard = SourceListPanel::copyToSystemClipboard;
    private boolean expanded;

    public SourceListPanel(List<SourceReference> sources, ComicPalette palette, BubblePalette bubblePalette) {
        super(palette);
        if (sources == null || sources.isEmpty() || bubblePalette == null) {
            throw new IllegalArgumentException("sources must not be empty and bubblePalette must not be null");
        }
        this.sources = Collections.unmodifiableList(new ArrayList<SourceReference>(sources));
        this.toggle = new ComicToggleButton(toggleText(sources.size()), palette);
        setLayout(null);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 12));
        setAccentStripe(palette.getAgentPetrol());
        toggle.setToolTipText("Quellen der Antwort ein- oder ausblenden");
        toggle.addActionListener(event -> setExpanded(toggle.isSelected()));
        add(toggle);
        note.setFont(note.getFont().deriveFont(Font.PLAIN, Math.max(11f, note.getFont().getSize2D() - 2f)));
        note.setForeground(bubblePalette.getInfoForeground());
        note.setVisible(false);
        add(note);
        toggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showHeaderMenu(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showHeaderMenu(event);
            }
        });
        for (int index = 0; index < this.sources.size(); index++) {
            SourceReference source = this.sources.get(index);
            JLabel title = new JLabel(titleLine(source));
            title.setFont(title.getFont().deriveFont(Font.BOLD, Math.max(11f, title.getFont().getSize2D() - 1f)));
            title.setForeground(palette.getInk());
            title.setVisible(false);
            titles.add(title);
            add(title);

            JTextArea detail = new JTextArea(detailLine(source));
            detail.setFont(detail.getFont().deriveFont(Font.PLAIN, Math.max(11f, detail.getFont().getSize2D() - 2f)));
            detail.setForeground(bubblePalette.getInfoForeground());
            detail.setOpaque(false);
            detail.setEditable(false);
            detail.setLineWrap(true);
            detail.setWrapStyleWord(true);
            detail.setBorder(BorderFactory.createEmptyBorder());
            detail.setVisible(false);
            details.add(detail);
            add(detail);
            final int row = index;
            title.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event) && isOpenable(row)) {
                        actions.open(SourceListPanel.this.sources.get(row));
                    }
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    underline(row, isOpenable(row));
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    underline(row, false);
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    showSourceMenu(row, event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    showSourceMenu(row, event);
                }
            });
            detail.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    showSourceMenu(row, event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    showSourceMenu(row, event);
                }
            });
        }
    }

    /**
     * Connect the list to the reader and favorites: openable titles get a hand cursor and open on a left click, each
     * source and the header get a context menu. {@code null} turns this off again.
     */
    public void setActions(Actions value) {
        this.actions = value;
        for (int i = 0; i < titles.size(); i++) {
            JLabel title = titles.get(i);
            boolean openable = isOpenable(i);
            title.setCursor(openable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            title.setToolTipText(openable ? MENU_OPEN + " (Rechtsklick: weitere Aktionen)" : null);
            underline(i, false);
        }
    }

    /** Return whether the source at {@code index} opens in the reader on a click. */
    public boolean isOpenable(int index) {
        return actions != null && index >= 0 && index < sources.size() && actions.canOpen(sources.get(index));
    }

    /** Return the clickable title label of the source at {@code index}. */
    JLabel titleLabel(int index) {
        return titles.get(index);
    }

    /** Return the note next to the toggle (result of a save), empty when none. */
    public String note() {
        return note.isVisible() ? note.getText() : "";
    }

    void setClipboard(Consumer<String> value) {
        this.clipboard = value;
    }

    /** Build the context menu of one source: open, save as favorite, copy bookmark, copy address. */
    JPopupMenu sourceMenu(int index) {
        final SourceReference source = sources.get(index);
        JPopupMenu menu = new JPopupMenu();
        boolean openable = isOpenable(index);
        menu.add(item(MENU_OPEN, openable, () -> actions.open(source)));
        menu.add(item(MENU_FAVORITE, openable && actions.canFavorite(),
                () -> showNote(actions.saveFavorite(source))));
        final String bookmark = actions == null ? "" : actions.bookmarkOf(source);
        menu.add(item(MENU_COPY_BOOKMARK, bookmark != null && !bookmark.isEmpty(), () -> clipboard.accept(bookmark)));
        menu.add(item(MENU_COPY_ADDRESS, true, () -> clipboard.accept(source.getLocation())));
        return menu;
    }

    /** Build the context menu of the header {@code Quellen (n)}: open all, save all as favorites. */
    JPopupMenu headerMenu() {
        boolean anyOpenable = false;
        for (int i = 0; i < sources.size() && !anyOpenable; i++) {
            anyOpenable = isOpenable(i);
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(MENU_OPEN_ALL, anyOpenable, () -> actions.openAll(sources)));
        menu.add(item(MENU_FAVORITE_ALL, anyOpenable && actions.canFavorite(),
                () -> showNote(actions.saveAllFavorites(sources))));
        return menu;
    }

    private void showSourceMenu(int index, MouseEvent event) {
        if (event.isPopupTrigger()) {
            sourceMenu(index).show((JComponent) event.getSource(), event.getX(), event.getY());
        }
    }

    private void showHeaderMenu(MouseEvent event) {
        if (event.isPopupTrigger()) {
            headerMenu().show(toggle, event.getX(), event.getY());
        }
    }

    private static JMenuItem item(String text, boolean enabled, final Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void showNote(String text) {
        boolean visible = text != null && !text.trim().isEmpty();
        note.setText(visible ? text.trim() : "");
        note.setVisible(visible);
        revalidate();
        repaint();
    }

    private void underline(int index, boolean on) {
        JLabel title = titles.get(index);
        Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.UNDERLINE, on ? TextAttribute.UNDERLINE_ON : -1);
        title.setFont(title.getFont().deriveFont(attributes));
    }

    private static void copyToSystemClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (HeadlessException | IllegalStateException unavailable) {
            // Leave the clipboard alone when the system holds it or there is none.
        }
    }

    public List<SourceReference> getSources() {
        return sources;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expand) {
        if (expanded == expand) {
            return;
        }
        expanded = expand;
        if (toggle.isSelected() != expand) {
            toggle.setSelected(expand);
        }
        for (JLabel title : titles) {
            title.setVisible(expand);
        }
        for (JTextArea detail : details) {
            detail.setVisible(expand);
        }
        revalidate();
        repaint();
    }

    /** Der Schalter {@code Quellen (n)}. */
    public ComicToggleButton toggle() {
        return toggle;
    }

    /** Erste Zeile einer Quelle: {@code [1] Titel – Überschrift}. */
    public static String titleLine(SourceReference source) {
        String line = "[" + source.getNumber() + "] " + source.getTitle();
        return source.getHeading().isEmpty() ? line : line + " – " + source.getHeading();
    }

    /** Zweite Zeile: Ort, Stand, Score und Ränge, durch Punkte getrennt. */
    public static String detailLine(SourceReference source) {
        StringBuilder line = new StringBuilder("Ort: ").append(source.getLocation());
        if (!source.getRevision().isEmpty()) {
            line.append("  ·  Stand: ").append(source.getRevision());
        }
        line.append("  ·  Score ").append(String.format(Locale.ROOT, "%.3f", source.getScore()));
        if (source.getKeywordRank() > 0) {
            line.append("  ·  Volltext #").append(source.getKeywordRank());
        }
        if (source.getSemanticRank() > 0) {
            line.append("  ·  Semantik #").append(source.getSemanticRank());
        }
        return line.toString();
    }

    static String toggleText(int count) {
        return TOGGLE_PREFIX + " (" + count + ")";
    }

    @Override
    public int preferredWidthWithin(int limit) {
        int natural = naturalWidth();
        int preferred = Math.max(MINIMUM_WIDTH, Math.min(NATURAL_WIDTH_CAP, natural));
        return limit > 0 ? Math.max(Math.min(limit, MINIMUM_WIDTH), Math.min(limit, preferred)) : preferred;
    }

    @Override
    public int preferredHeightForWidth(int width) {
        Insets insets = getInsets();
        int inner = Math.max(24, width - insets.left - insets.right);
        int height = insets.top + toggle.getPreferredSize().height;
        if (expanded) {
            for (int i = 0; i < titles.size(); i++) {
                height += ROW_GAP + titles.get(i).getPreferredSize().height + LINE_GAP + detailHeight(details.get(i), inner);
            }
        }
        return height + insets.bottom;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = preferredWidthWithin(0);
        return new Dimension(width, preferredHeightForWidth(width));
    }

    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int inner = Math.max(24, getWidth() - insets.left - insets.right);
        int y = insets.top;
        Dimension toggleSize = toggle.getPreferredSize();
        int toggleWidth = Math.min(inner, toggleSize.width);
        toggle.setBounds(insets.left, y, toggleWidth, toggleSize.height);
        if (note.isVisible()) {
            int noteX = insets.left + toggleWidth + ROW_GAP;
            note.setBounds(noteX, y, Math.max(0, insets.left + inner - noteX), toggleSize.height);
        } else {
            note.setBounds(0, 0, 0, 0);
        }
        y += toggleSize.height;
        for (int i = 0; i < titles.size(); i++) {
            JLabel title = titles.get(i);
            JTextArea detail = details.get(i);
            if (!expanded) {
                title.setBounds(0, 0, 0, 0);
                detail.setBounds(0, 0, 0, 0);
                continue;
            }
            y += ROW_GAP;
            int titleHeight = title.getPreferredSize().height;
            title.setBounds(insets.left, y, inner, titleHeight);
            y += titleHeight + LINE_GAP;
            int detailHeight = detailHeight(detail, inner);
            detail.setBounds(insets.left, y, inner, detailHeight);
            y += detailHeight;
        }
    }

    private int naturalWidth() {
        Insets insets = getInsets();
        int width = toggle.getPreferredSize().width;
        if (note.isVisible()) {
            width += ROW_GAP + note.getPreferredSize().width;
        }
        if (expanded) {
            for (int i = 0; i < titles.size(); i++) {
                width = Math.max(width, titles.get(i).getPreferredSize().width);
                JTextArea detail = details.get(i);
                FontMetrics metrics = detail.getFontMetrics(detail.getFont());
                width = Math.max(width, metrics.stringWidth(detail.getText()) + 8);
            }
        }
        return width + insets.left + insets.right;
    }

    private static int detailHeight(JTextArea detail, int width) {
        detail.setSize(new Dimension(width, Short.MAX_VALUE));
        return detail.getPreferredSize().height;
    }
}
