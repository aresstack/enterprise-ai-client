package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.CopyrightFooter;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownTheme;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MermaidImageRenderer;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.paint.SidebarIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Lesebereich statt des Chats, wie der Lese-Tab in MainframeMate: oben die Lese-Reiter, darunter Titel, Fundstelle,
 * ★ (als Favorit merken) und „Anheften“, darunter der Inhalt (das Dokument als Markdown oder die Ressourcen einer
 * Quelle als Liste).
 *
 * <p>Ein einfacher Klick öffnet einen Verweis als <b>Vorschau</b>: Es gibt höchstens einen Vorschau-Reiter (Titel
 * kursiv), den der nächste einfache Klick ersetzt. Ein Doppelklick, „Anheften“ oder ein Doppelklick auf den Reiter
 * heftet ihn fest an; angeheftete Reiter bleiben, bis man sie mit ✕ schließt.</p>
 */
public final class KnowledgeReaderPanel extends JPanel {

    /** Was der Lesebereich nach außen meldet; alle Aufrufe auf dem EDT. */
    public interface Listener extends FavoriteMarker {
        /** Ein Eintrag der Quellenübersicht wurde angeklickt. */
        void linkOpened(ReaderLink link, boolean pin);

        /** Der letzte Reiter ist zu. */
        default void allClosed() {
        }
    }

    static final String EMPTY_TEXT = "Eine Quelle, einen Treffer oder einen Favoriten links anklicken: "
            + "ein Klick zeigt die Vorschau, ein Doppelklick heftet an.";
    static final String LOADING_TEXT = "Lade …";
    static final String PIN_TOOLTIP = "Anheften (bleibt offen, bis du ihn schließt)";
    static final String FAVORITE_ADD = "Als Favorit merken";
    static final String FAVORITE_REMOVE = "Aus den Favoriten entfernen";
    private static final String EMPTY_CARD = "empty";
    private static final int PAGE_PADDING_TOP = 14;

    private final ComicPalette palette;
    private final MarkdownTheme theme;
    private final DesktopLinkOpener linkOpener;
    private final MermaidImageRenderer mermaid;
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JLabel titleLabel = new JLabel();
    private final JLabel locationLabel = new JLabel();
    private final ComposerButton favoriteButton;
    private final ComposerButton pinButton;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final Map<String, Tab> tabs = new LinkedHashMap<String, Tab>();
    private String activeKey;
    private int cardCounter;
    private Listener listener;

    public KnowledgeReaderPanel(ComicPalette palette) {
        this(palette, DesktopLinkOpener.systemDefault(), CachingMermaidImageRenderer.forChat());
    }

    public KnowledgeReaderPanel(ComicPalette palette, DesktopLinkOpener linkOpener, MermaidImageRenderer mermaid) {
        super(new BorderLayout());
        if (palette == null || linkOpener == null || mermaid == null) {
            throw new IllegalArgumentException("palette, linkOpener and mermaid must not be null");
        }
        this.palette = palette;
        this.linkOpener = linkOpener;
        this.mermaid = mermaid;
        this.theme = MarkdownTheme.forBubble(ResearchUiPalette.LIGHT_CONTROL_BG, palette.getInk());
        setBackground(palette.getSurface());

        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));

        favoriteButton = ComposerButton.iconButton(SidebarIcons.star(false), FAVORITE_ADD);
        favoriteButton.addActionListener(event -> {
            Tab tab = tabs.get(activeKey);
            if (tab != null && listener != null) {
                listener.favoriteToggled(tab.link);
                refreshHeader();
            }
        });
        pinButton = new ComposerButton(null, "Anheften", false, PIN_TOOLTIP);
        pinButton.setFont(ResearchUiTypography.semiBold(12f));
        pinButton.addActionListener(event -> pin(activeKey));

        titleLabel.setFont(ResearchUiTypography.semiBold(16f));
        titleLabel.setForeground(palette.getInk());
        locationLabel.setFont(ResearchUiTypography.regular(11f));
        locationLabel.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(titleLabel);
        titles.add(locationLabel);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(pinButton);
        buttons.add(favoriteButton);
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 18, 8, 12));
        header.add(titles, BorderLayout.CENTER);
        header.add(buttons, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(strip, BorderLayout.NORTH);
        north.add(header, BorderLayout.CENTER);

        deck.setOpaque(false);
        deck.add(message(EMPTY_TEXT), EMPTY_CARD);
        add(north, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);
        add(CopyrightFooter.placedLikeChat(), BorderLayout.SOUTH);
        refresh();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        refreshHeader();
    }

    // ------------------------------------------------------------------ Reiter

    /**
     * Öffnet den Verweis: ein schon offener Reiter wird gezeigt (und mit {@code pin} angeheftet); sonst ersetzt eine
     * Vorschau die bisherige Vorschau, angeheftet entsteht ein neuer Reiter. Der Inhalt zeigt „Lade …“, bis
     * {@link #showMarkdown}, {@link #showLinks} oder {@link #showProblem} ihn setzt.
     *
     * @return {@code true}, wenn der Inhalt noch geladen werden muss
     */
    public boolean open(ReaderLink link, boolean pin) {
        if (link == null) {
            throw new IllegalArgumentException("link must not be null");
        }
        Tab existing = tabs.get(link.key());
        if (existing != null) {
            if (pin) {
                existing.pinned = true;
            }
            activeKey = link.key();
            refresh();
            return false;
        }
        if (!pin) {
            Tab preview = previewTab();
            if (preview != null) {
                remove(preview);
            }
        }
        Tab tab = new Tab(link, pin, "c" + (cardCounter++));
        tabs.put(link.key(), tab);
        JLabel loading = message(LOADING_TEXT);
        loading.setName(tab.card);
        tab.content = loading;
        deck.add(loading, tab.card);
        activeKey = link.key();
        refresh();
        return true;
    }

    /** Das Dokument als Markdown. */
    public void showMarkdown(String key, String markdown) {
        showDocument(key, markdown, null);
    }

    /**
     * Das Dokument als Markdown mit dem Navigationsbanner darüber (FA-07): Kapitel als Wolkenreihe, aufklappbar.
     * {@code navigation} {@code null} oder leer: kein Banner. Der angezeigte Text ist genau {@code markdown}; die
     * Quellpositionen der Navigation beziehen sich auf ihn.
     */
    public void showDocument(String key, String markdown, DocumentNavigation navigation) {
        Tab tab = tabs.get(key);
        if (tab == null) {
            return;
        }
        MarkdownMessageView view = new MarkdownMessageView(theme, linkOpener, mermaid);
        view.setMarkdown(markdown == null ? "" : markdown);
        WidthTracking page = new WidthTracking(new BorderLayout());
        page.setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        page.setOpaque(true);
        page.setBorder(BorderFactory.createEmptyBorder(PAGE_PADDING_TOP, 20, 20, 20));
        page.add(view, BorderLayout.NORTH);
        NavigableDocumentPage document = new NavigableDocumentPage(scroll(page), page, view, PAGE_PADDING_TOP);
        document.setNavigation(navigation);
        tab.document = document;
        replace(tab, document);
    }

    /**
     * Zieht die Navigation eines offenen Dokuments nach (z. B. sobald die semantische Analyse fertig ist), ohne das
     * Dokument neu zu rendern oder die Leseposition zu verlieren. Nur für dieselbe Fassung ({@code captureId}).
     */
    public void updateNavigation(String key, DocumentNavigation navigation) {
        Tab tab = tabs.get(key);
        if (tab == null || tab.document == null || navigation == null) {
            return;
        }
        DocumentNavigation current = tab.document.sky().navigation();
        if (current != null && !current.captureId().equals(navigation.captureId())) {
            return; // inzwischen eine andere Fassung angezeigt
        }
        tab.document.setNavigation(navigation);
    }

    /** Der Navigationsbanner des Reiters oder {@code null} (kein Dokument). */
    DocumentNavigationSky navigationSky(String key) {
        Tab tab = tabs.get(key);
        return tab == null || tab.document == null ? null : tab.document.sky();
    }

    NavigableDocumentPage documentPage(String key) {
        Tab tab = tabs.get(key);
        return tab == null ? null : tab.document;
    }

    /** Die Ressourcen einer Quelle; ein Klick öffnet sie als Vorschau, ein Doppelklick heftet an. */
    public void showLinks(String key, List<ReaderLink> links, String emptyText) {
        Tab tab = tabs.get(key);
        if (tab == null) {
            return;
        }
        if (links == null || links.isEmpty()) {
            replace(tab, message(emptyText == null ? "Keine Einträge" : emptyText));
            return;
        }
        WidthTracking list = new WidthTracking(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        tab.stars.clear();
        for (ReaderLink link : links) {
            // rechtsbündig je Eintrag der Stern: direkt in den gewählten Favoritenordner übernehmen
            FavoriteStarButton star = FavoriteStarButton.forLink(link, listener);
            if (star != null) {
                tab.stars.add(star);
            }
            ReaderLinkRow row = new ReaderLinkRow(link, (opened, pin) -> {
                if (listener != null) {
                    listener.linkOpened(opened, pin);
                }
            }, star == null ? null : star.component(), palette);
            row.setAlignmentX(LEFT_ALIGNMENT);
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        replace(tab, scroll(list));
    }

    /** Eine Meldung statt des Inhalts (z. B. Quelle nicht erreichbar). */
    public void showProblem(String key, String text) {
        Tab tab = tabs.get(key);
        if (tab != null) {
            JLabel label = message(text);
            label.setForeground(ResearchUiPalette.DANGER_RED);
            replace(tab, label);
        }
    }

    /** Heftet den Reiter an (aus der Vorschau wird ein fester Reiter). */
    public void pin(String key) {
        Tab tab = key == null ? null : tabs.get(key);
        if (tab != null && !tab.pinned) {
            tab.pinned = true;
            refresh();
        }
    }

    /** Schließt den Reiter; danach ist der rechte (sonst der linke) Nachbar aktiv. */
    public void close(String key) {
        Tab tab = tabs.get(key);
        if (tab == null) {
            return;
        }
        List<String> keys = new ArrayList<String>(tabs.keySet());
        int index = keys.indexOf(key);
        remove(tab);
        if (key.equals(activeKey)) {
            keys.remove(index);
            activeKey = keys.isEmpty() ? null : keys.get(Math.min(index, keys.size() - 1));
        }
        refresh();
        if (tabs.isEmpty() && listener != null) {
            listener.allClosed();
        }
    }

    public void select(String key) {
        if (tabs.containsKey(key)) {
            activeKey = key;
            refresh();
        }
    }

    /** Nach einer Änderung der Favoriten von außen: ★ neu zeichnen. */
    public void refreshFavorite() {
        refreshHeader();
        for (Tab tab : tabs.values()) {
            FavoriteStarButton.refreshAll(tab.stars);
        }
    }

    /** Die Sterne der Ressourcenliste im aktiven Reiter (für Tests). */
    public List<FavoriteStarButton> listStars() {
        Tab active = tabs.get(activeKey);
        return active == null ? Collections.<FavoriteStarButton>emptyList()
                : Collections.unmodifiableList(new ArrayList<FavoriteStarButton>(active.stars));
    }

    public boolean hasTabs() {
        return !tabs.isEmpty();
    }

    public List<String> tabKeys() {
        return Collections.unmodifiableList(new ArrayList<String>(tabs.keySet()));
    }

    public String activeKey() {
        return activeKey;
    }

    public boolean isPinned(String key) {
        Tab tab = tabs.get(key);
        return tab != null && tab.pinned;
    }

    /** Der Schlüssel des Vorschau-Reiters oder {@code null}. */
    public String previewKey() {
        Tab preview = previewTab();
        return preview == null ? null : preview.link.key();
    }

    public ComposerButton favoriteButton() {
        return favoriteButton;
    }

    public ComposerButton pinButton() {
        return pinButton;
    }

    // ------------------------------------------------------------------ intern

    private Tab previewTab() {
        for (Tab tab : tabs.values()) {
            if (!tab.pinned) {
                return tab;
            }
        }
        return null;
    }

    private void remove(Tab tab) {
        tabs.remove(tab.link.key());
        if (tab.content != null) {
            deck.remove(tab.content);
        }
        for (java.awt.Component component : deck.getComponents()) {
            if (tab.card.equals(component.getName())) {
                deck.remove(component);
            }
        }
    }

    private void replace(Tab tab, JComponent content) {
        for (java.awt.Component component : deck.getComponents()) {
            if (tab.card.equals(component.getName())) {
                deck.remove(component);
            }
        }
        content.setName(tab.card);
        tab.content = content;
        if (content != tab.document) {
            tab.document = null;
        }
        deck.add(content, tab.card);
        refresh();
    }

    private void refresh() {
        strip.removeAll();
        for (Tab tab : tabs.values()) {
            strip.add(new TabChip(tab, tab.link.key().equals(activeKey)));
        }
        strip.setVisible(!tabs.isEmpty());
        Tab active = tabs.get(activeKey);
        cards.show(deck, active == null ? EMPTY_CARD : active.card);
        refreshHeader();
        strip.revalidate();
        strip.repaint();
        deck.revalidate();
        deck.repaint();
    }

    private void refreshHeader() {
        Tab active = tabs.get(activeKey);
        titleLabel.setText(active == null ? "Lesebereich" : active.link.title());
        Font titleFont = ResearchUiTypography.semiBold(16f);
        titleLabel.setFont(active != null && !active.pinned
                ? titleFont.deriveFont(titleFont.getStyle() | Font.ITALIC) : titleFont);
        locationLabel.setText(active == null ? " " : active.link.location().isEmpty() ? " " : active.link.location());
        pinButton.setVisible(active != null && !active.pinned);
        boolean favorite = active != null && listener != null && listener.isFavorite(active.link);
        favoriteButton.setVisible(active != null && listener != null && listener.canFavorite());
        favoriteButton.setIcon(SidebarIcons.star(favorite));
        favoriteButton.setToolTipText(favorite ? FAVORITE_REMOVE : FAVORITE_ADD);
    }

    private JLabel message(String text) {
        JLabel label = new JLabel("<html><div style='text-align:center'>" + text + "</div></html>",
                SwingConstants.CENTER);
        label.setFont(ResearchUiTypography.regular(13f));
        label.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        return label;
    }

    private JScrollPane scroll(JComponent view) {
        JScrollPane scroll = new ComicScrollPane(view, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    /** Ein Lese-Reiter. */
    private static final class Tab {
        final ReaderLink link;
        final String card;
        boolean pinned;
        JComponent content;
        final List<FavoriteStarButton> stars = new ArrayList<FavoriteStarButton>();
        NavigableDocumentPage document;

        Tab(ReaderLink link, boolean pinned, String card) {
            this.link = link;
            this.pinned = pinned;
            this.card = card;
        }
    }

    /** Ein Reiter oben: Titel (kursiv als Vorschau) und ✕; Klick wählt, Doppelklick heftet an. */
    private final class TabChip extends JPanel {
        private final boolean active;

        TabChip(final Tab tab, boolean active) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            this.active = active;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tab.pinned ? tab.link.location() : "Vorschau: Doppelklick heftet an");
            JLabel title = new JLabel(shorten(tab.link.title()));
            Font font = ResearchUiTypography.regular(12f);
            title.setFont(tab.pinned ? font : font.deriveFont(Font.ITALIC));
            title.setForeground(active ? palette.getInk() : ResearchUiPalette.LIGHT_CONTROL_TEXT);
            add(title);
            ComposerButton close = ComposerButton.iconButton(ComposerIcons.close(), "Schließen");
            close.setPreferredSize(new Dimension(22, 22));
            close.addActionListener(event -> close(tab.link.key()));
            add(close);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) {
                        return;
                    }
                    if (event.getClickCount() >= 2) {
                        tab.pinned = true;
                    }
                    activeKey = tab.link.key();
                    refresh();
                }
            };
            addMouseListener(mouse);
            title.addMouseListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                Color fill = active ? ResearchUiPalette.LIGHT_CONTROL_BG
                        : ResearchUiPainter.mix(ResearchUiPalette.LIGHT_CONTROL_BG, palette.getSurface(), 0.5f);
                ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), ResearchUiMetrics.RADIUS_CONTROL, fill);
                g2.setColor(active ? ResearchUiPalette.ACCENT_BLUE : ResearchUiPalette.LIGHT_CONTROL_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ResearchUiMetrics.RADIUS_CONTROL * 2,
                        ResearchUiMetrics.RADIUS_CONTROL * 2);
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static String shorten(String title) {
        return title.length() <= 28 ? title : title.substring(0, 27).trim() + "…";
    }

    /** Folgt der Breite des Sichtbereichs, damit lange Zeilen umbrechen statt horizontal zu rollen. */
    private static final class WidthTracking extends JPanel implements Scrollable {
        WidthTracking(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredSize() {
            WidthAwareHeight view = widthAwareView();
            if (view != null && getParent() != null && getParent().getWidth() > 0) {
                Insets insets = getInsets();
                int inner = getParent().getWidth() - insets.left - insets.right;
                return new Dimension(getParent().getWidth(),
                        view.preferredHeightForWidth(inner) + insets.top + insets.bottom);
            }
            return super.getPreferredSize();
        }

        /**
         * Gibt dem Dokument die Höhe, die es bei der aktuellen Breite braucht. Das BorderLayout nähme dafür dessen
         * Wunschhöhe, die noch bei der vorigen Breite gemessen ist: Wird der Lesebereich schmaler (Fenster, Drawer),
         * brechen die Zeilen öfter um, das Dokument bliebe aber so hoch wie vorher und wäre unten abgeschnitten.
         */
        @Override
        public void doLayout() {
            WidthAwareHeight view = widthAwareView();
            if (view == null) {
                super.doLayout();
                return;
            }
            Insets insets = getInsets();
            int inner = Math.max(0, getWidth() - insets.left - insets.right);
            getComponent(0).setBounds(insets.left, insets.top, inner, view.preferredHeightForWidth(inner));
        }

        private WidthAwareHeight widthAwareView() {
            if (getComponentCount() == 1 && getLayout() instanceof BorderLayout
                    && getComponent(0) instanceof WidthAwareHeight) {
                return (WidthAwareHeight) getComponent(0);
            }
            return null;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
