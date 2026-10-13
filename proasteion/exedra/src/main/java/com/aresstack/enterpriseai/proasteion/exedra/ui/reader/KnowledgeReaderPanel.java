package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechText;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DiagramOverlayHost;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownSpeech;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownTheme;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MermaidImageRenderer;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MermaidViewerPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicOverlayPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.paint.CountBadgeIcon;
import com.aresstack.enterpriseai.ui.comic.paint.SidebarIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Der Lesebereich statt des Chats, wie der Lese-Tab in MainframeMate: oben die Lese-Reiter, darunter der Inhalt (das
 * Dokument als Markdown oder die Ressourcen einer Quelle als Liste). Jeder Reiter trägt seine Aktionen selbst:
 * {@code Titel | Stecknadel | Stern | Schließen} (Stern wie in MainframeMates {@code TabbedPaneManager}); eine
 * Kopfzeile mit Titel und Fundstelle gibt es nicht mehr.
 *
 * <p>Ein einfacher Klick öffnet einen Verweis als <b>Vorschau</b>: Es gibt höchstens einen Vorschau-Reiter (Titel
 * kursiv), den der nächste einfache Klick ersetzt. Ein Doppelklick, die Stecknadel (nur am Vorschau-Reiter) oder ein
 * Doppelklick auf den Reiter heftet ihn fest an; angeheftete Reiter bleiben, bis man sie schließt. Der Tooltip des
 * Reiters zeigt den ungekürzten Titel.</p>
 *
 * <p>Unten liegt eine einzige Zeile ({@link ReaderToolbar}): links die Lupe und daneben das Info-Symbol
 * ({@link ReaderPageInfo}: vollständige Adresse, kopieren, im Browser öffnen) und der Play-Knopf zum Vorlesen
 * ({@link #setReadAloud}), rechts das Branding an derselben Stelle
 * wie unter dem Chat. Lupe oder Strg+F klappt die Suche in derselben Zeile auf; gesucht wird wie in MainframeMates
 * {@code ConfluenceReaderTab} nur im aktiven Reiter (alle Fundstellen gelb, die aktuelle orange), Begriff und
 * Auf/Zu merkt sich jeder Reiter.</p>
 *
 * <p>Neue Enterprise-Funktion (kein Port): Reiter lassen sich per Drag and Drop umsortieren und wie in Firefox zu
 * farbigen Gruppen mit optionalem Namen zusammenfassen ({@link ReaderTabGroup}); Vorschau/Angeheftet bleibt dabei am
 * Reiter. Rechtsklick auf einen Reiter öffnet sein Kontextmenü, das andere Teile über
 * {@link #addTabMenuContributor} ergänzen.</p>
 *
 * <p>Seiten mit Original-HTML ({@link #showHtml}) zeigen ihre Bilder inline; ein Klick auf ein Bild oder „Bilder (n)“
 * im Kontextmenü des Reiters öffnet die Diashow aller Bilder der Seite als Comic-Overlay über dem Inhalt (wie der Mermaid-Betrachter).</p>
 */
public final class KnowledgeReaderPanel extends JPanel implements DiagramOverlayHost {

    /** Was der Lesebereich nach außen meldet; alle Aufrufe auf dem EDT. */
    public interface Listener extends FavoriteMarker {
        /** Ein Eintrag der Quellenübersicht wurde angeklickt. */
        void linkOpened(ReaderLink link, boolean pin);

        /** Der letzte Reiter ist zu. */
        default void allClosed() {
        }
    }

    /** Ergänzt das Kontextmenü eines Reiters (z. B. „Zum aktuellen Chat hinzufügen“); Aufruf auf dem EDT. */
    public interface TabMenuContributor {
        void contribute(JPopupMenu menu, ReaderLink link);
    }

    static final String MENU_PIN = "Anheften";
    static final String MENU_CLOSE = "Schließen";
    static final String MENU_NEW_GROUP = "Neue Gruppe\u2026";
    static final String MENU_ADD_TO_GROUP = "Zu Gruppe hinzufügen";
    static final String MENU_REMOVE_FROM_GROUP = "Aus Gruppe entfernen";
    static final String MENU_EDIT_GROUP = "Gruppe bearbeiten\u2026";
    static final String MENU_GROUP_COLOR = "Farbe";
    static final String MENU_COLLAPSE_GROUP = "Gruppe einklappen";
    static final String MENU_EXPAND_GROUP = "Gruppe ausklappen";
    static final String MENU_DISSOLVE_GROUP = "Gruppe auflösen";
    static final String MENU_CLOSE_GROUP = "Gruppe schließen";
    static final String EMPTY_TEXT = "Eine Quelle, einen Treffer oder einen Favoriten links anklicken: "
            + "ein Klick zeigt die Vorschau, ein Doppelklick heftet an.";
    static final String LOADING_TEXT = "Lade …";
    static final String PIN_TOOLTIP = "Anheften (bleibt offen, bis du ihn schließt)";
    static final String PREVIEW_HINT = "Vorschau – mit der Stecknadel anheften";
    static final String FAVORITE_ADD = "Als Favorit merken";
    static final String FAVORITE_REMOVE = "Aus den Favoriten entfernen";
    static final String CLOSE_TOOLTIP = "Schließen";
    static final String READ_ALOUD_PLAY_TOOLTIP = "Seite vorlesen";
    static final String READ_ALOUD_STOP_TOOLTIP = "Vorlesen beenden";
    static final String READ_ALOUD_NO_TEXT_TOOLTIP = "Für diesen Reiter gibt es (noch) keinen vorlesbaren Text";
    static final String READ_ALOUD_UNAVAILABLE_TEXT = "Die Seite kann nicht vorgelesen werden: Es ist keine "
            + "Sprachausgabe eingerichtet (TTS-Modell und Stimme in den Einstellungen wählen).";
    private static final int TAB_BUTTON_SIZE = 22;
    private static final String EMPTY_CARD = "empty";
    /** MainframeMate {@code TabbedPaneManager}: Maustasten Zurück und Vor. */
    static final int MOUSE_BACK_BUTTON = 4;
    static final int MOUSE_FORWARD_BUTTON = 5;
    private static final int PAGE_PADDING_TOP = 14;
    private static final String CLOSE_OVERLAY_ACTION = "reader.closeOverlay";

    private final ComicPalette palette;
    private final MarkdownTheme theme;
    private final DesktopLinkOpener linkOpener;
    private final MermaidImageRenderer mermaid;
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
        @Override
        protected void paintChildren(Graphics graphics) {
            super.paintChildren(graphics);
            if (dropX >= 0) {
                Graphics2D g2 = ResearchUiPainter.prepare(graphics);
                try {
                    // In der Gruppenfarbe, wenn der Reiter beim Loslassen der Gruppe beitritt.
                    g2.setColor(dropTint != null ? dropTint : ResearchUiPalette.ACCENT_BLUE);
                    g2.fillRoundRect(dropX - 1, 0, 3, getHeight(), 3, 3);
                } finally {
                    g2.dispose();
                }
            }
        }
    };
    private final List<TabMenuContributor> menuContributors = new ArrayList<TabMenuContributor>();
    private int groupCounter;
    private String dragKey;
    private Point dragStart;
    private boolean dragging;
    private int dropX = -1;
    private Color dropTint;
    private final ComposerButton infoButton;
    /** Büroklammer mit der Zahl der Anhänge des aktiven Reiters; ohne Anhänge ausgeblendet. */
    private final ComposerButton attachmentsButton;
    private java.util.function.Consumer<JPopupMenu> attachmentMenuShower = this::showAboveButton;
    private ReaderFileOpener fileOpener = ReaderFileOpener.system();
    /** Zeichen-Thread des Anhang-Overlays für PDF-Seiten; {@code null}: der gemeinsame. */
    private java.util.concurrent.Executor attachmentRenderer;
    private java.util.function.Consumer<String> clipboard = ReaderPageInfo.SYSTEM_CLIPBOARD;
    /** Play/Stopp der Sprachausgabe; ausgeblendet, bis {@link #setReadAloud} sie anschließt. */
    private final ComposerButton readAloudButton;
    private ReadAloudControl readAloud;
    /** Der Reiter, dessen Text gerade vorgelesen wird; {@code null}: keiner. */
    private Tab speakingTab;
    /** Kennung des laufenden Vorlesens; das Ende eines abgelösten Laufs setzt den Knopf nicht zurück. */
    private Object speakingRun;
    private java.util.function.Consumer<String> readAloudNotice = text -> JOptionPane.showMessageDialog(this, text,
            "Vorlesen", JOptionPane.WARNING_MESSAGE);
    private final JLayeredPane layers = new JLayeredPane();
    private ComicOverlayPanel overlay;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final Map<String, Tab> tabs = new LinkedHashMap<String, Tab>();
    private String activeKey;
    private int cardCounter;
    private Listener listener;
    private final ReaderToolbar toolbar = new ReaderToolbar();
    /** Verlauf der Reiter wie MainframeMates {@code TabbedPaneManager}: zuvor aktive Reiter, per Zurück verlassene. */
    private final Deque<Tab> backStack = new ArrayDeque<Tab>();
    private final Deque<Tab> forwardStack = new ArrayDeque<Tab>();
    private Tab lastActive;
    private boolean suppressHistory;
    private final AWTEventListener mouseNavigation = event -> {
        if (event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_RELEASED) {
            MouseEvent mouse = (MouseEvent) event;
            mouseButton(mouse.getButton(), mouse.getComponent());
        } else if (event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_CLICKED) {
            MouseEvent mouse = (MouseEvent) event;
            if (mouse.getButton() == MouseEvent.BUTTON1 && mouse.getClickCount() == 2) {
                readAloudFrom(mouse.getComponent(), mouse.getPoint());
            }
        }
    };

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

        strip.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        infoButton = toolbar.addTool(ComposerIcons.info(), ReaderPageInfo.TOOLTIP);
        infoButton.addActionListener(event -> {
            JPopupMenu info = pageInfoMenu();
            if (info != null) {
                info.show(infoButton, 0, -info.getPreferredSize().height);
            }
        });
        // Neben der Info: die Seite vorlesen, mit derselben Sprachausgabe wie der Chat.
        readAloudButton = toolbar.addTool(ComposerIcons.play(), READ_ALOUD_PLAY_TOOLTIP);
        toolbar.showTool(readAloudButton, false);
        readAloudButton.addActionListener(event -> toggleReadAloud());
        // Rechts neben Lupe und Info: Anhänge der Seite (unabhängig davon, ob die Quelle sie indexiert).
        attachmentsButton = toolbar.addTool(attachmentIcon(0), ReaderAttachmentList.tooltip(0));
        toolbar.showTool(attachmentsButton, false);
        attachmentsButton.addActionListener(event -> {
            JPopupMenu list = attachmentMenu();
            if (list != null) {
                attachmentMenuShower.accept(list);
            }
        });

        deck.setOpaque(false);
        deck.add(message(EMPTY_TEXT), EMPTY_CARD);
        add(strip, BorderLayout.NORTH);
        layers.setLayout(new FillLayout());
        layers.add(deck, JLayeredPane.DEFAULT_LAYER);
        add(layers, BorderLayout.CENTER);
        add(toolbar, BorderLayout.SOUTH);
        toolbar.setNavigation(this::back, this::forward);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                "reader.find");
        getActionMap().put("reader.find", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (isShowing()) {
                    toolbar.open();
                }
            }
        });
        refresh();
    }

    // ------------------------------------------------------------------ Zurück/Vor

    /**
     * Zurück wie {@code TabbedPaneManager.navigateTabBack()} in MainframeMate: der aktuelle Reiter geht zu (Vor öffnet
     * ihn wieder), der zuvor aktive wird gezeigt. Eine durch einen Klick ersetzte Vorschau kommt dabei zurück
     * (eigene Ergänzung, MainframeMate hatte keine Vorschau).
     *
     * @return ob zurückgegangen wurde
     */
    public boolean back() {
        Tab previous = null;
        while (!backStack.isEmpty()) {
            Tab candidate = backStack.pop();
            if (isOpen(candidate) || candidate.replaced && !tabs.containsKey(candidate.link.key())) {
                previous = candidate;
                break;
            }
        }
        if (previous == null) {
            return false;
        }
        Tab current = tabs.get(activeKey);
        suppressHistory = true;
        try {
            if (current != null && current != previous) {
                remove(current);
                forwardStack.push(current);
            }
            showTab(previous);
        } finally {
            suppressHistory = false;
        }
        return true;
    }

    /**
     * Vor wie {@code TabbedPaneManager.navigateTabForward()}: öffnet den zuletzt per Zurück verlassenen Reiter wieder.
     *
     * @return ob vorgegangen wurde
     */
    public boolean forward() {
        if (forwardStack.isEmpty()) {
            return false;
        }
        Tab next = forwardStack.pop();
        Tab current = tabs.get(activeKey);
        suppressHistory = true;
        try {
            if (current != null && current != next) {
                backStack.push(current);
            }
            showTab(next);
        } finally {
            suppressHistory = false;
        }
        return true;
    }

    public boolean canGoBack() {
        for (Tab tab : backStack) {
            if (isOpen(tab) || tab.replaced && !tabs.containsKey(tab.link.key())) {
                return true;
            }
        }
        return false;
    }

    public boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    /**
     * Die Maustasten Zurück (4) und Vor (5) irgendwo im Lesebereich, wie MainframeMates globaler Listener beim
     * Loslassen.
     *
     * @return ob die Taste behandelt wurde
     */
    boolean mouseButton(int button, java.awt.Component source) {
        if (source == null || !SwingUtilities.isDescendingFrom(source, this)
                || button != MOUSE_BACK_BUTTON && button != MOUSE_FORWARD_BUTTON) {
            return false;
        }
        if (button == MOUSE_BACK_BUTTON) {
            back();
        } else {
            forward();
        }
        return true;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Toolkit.getDefaultToolkit().addAWTEventListener(mouseNavigation, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(mouseNavigation);
        super.removeNotify();
    }

    private boolean isOpen(Tab tab) {
        return tabs.get(tab.link.key()) == tab;
    }

    /** Zeigt den Reiter; ist er zu, kommt er wieder dazu (eine andere Vorschau weicht ihm). */
    private void showTab(Tab tab) {
        activeKey = tab.link.key();
        lastActive = tab;
        if (isOpen(tab)) {
            refresh();
            return;
        }
        if (tabs.containsKey(tab.link.key())) {
            refresh(); // dieselbe Seite ist inzwischen wieder offen
            return;
        }
        tab.replaced = false;
        if (!tab.pinned) {
            Tab preview = previewTab();
            if (preview != null) {
                preview.replaced = true;
                remove(preview);
            }
        }
        tabs.put(tab.link.key(), tab);
        if (tab.content != null) {
            tab.content.setName(tab.card);
            deck.add(tab.content, tab.card);
        }
        reorder(new ArrayList<Tab>(tabs.values()));
    }

    /** Klappt die Suche des aktiven Reiters auf und setzt den Cursor ins Feld (Strg+F). */
    public void focusFindBar() {
        toolbar.open();
    }

    /** Sucht im aktiven Reiter, als hätte man {@code query} eingetippt und Enter gedrückt. */
    public int searchFor(String query) {
        return toolbar.searchFor(query);
    }

    /** Die Leiste unter dem Lesebereich (Lupe, Suche, Branding). */
    ReaderToolbar toolbar() {
        return toolbar;
    }

    /** Die Komponente, in der der Reiter {@code key} gerade Fundstelle {@code index} markiert hat (für Tests). */
    java.awt.Component findHit(String key, int index) {
        Tab tab = tabs.get(key);
        return tab == null || tab.find == null || index >= tab.find.hitCount() ? null : tab.find.hitComponent(index);
    }

    /** Anzahl der markierten Fundstellen im Reiter {@code key}. */
    public int findHitCount(String key) {
        Tab tab = tabs.get(key);
        return tab == null || tab.find == null ? 0 : tab.find.hitCount();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        refresh();
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
                preview.replaced = true; // Zurück holt sie wieder
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
        replace(tab, searchable(tab, document, page));
        tab.speechText = markdown; // der angezeigte Text ist der Seitentext
        styleReadAloud();
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

    /**
     * Die Seite als Original-HTML mit ihren Bildern an ihrer Stelle; {@code images} sind alle Bilder der Seite in
     * Dokumentreihenfolge (die Liste der Diashow), {@code loader} holt deren Bytes und Beschreibungen.
     */
    public void showHtml(final String key, String bodyHtml, String baseUri, List<ReaderImage> images,
                         ReaderImageLoader loader) {
        final Tab tab = tabs.get(key);
        if (tab == null) {
            return;
        }
        final ReaderImageStore store = new ReaderImageStore(images, loader);
        ReaderHtmlView view = new ReaderHtmlView(bodyHtml, baseUri, store, linkOpener,
                image -> openSlideshow(key, store.images().indexOf(image)));
        // Wie showDocument: Seite mit Innenabstand, Höhe je Breite, Navigationsbanner darüber ({@link
        // #updateNavigation} setzt ihn, sobald das Dokument aufbereitet ist).
        WidthTracking page = new WidthTracking(new BorderLayout());
        page.setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        page.setOpaque(true);
        page.setBorder(BorderFactory.createEmptyBorder(PAGE_PADDING_TOP, 20, 20, 20));
        page.add(view, BorderLayout.NORTH);
        NavigableDocumentPage document = new NavigableDocumentPage(scroll(page), page, view, PAGE_PADDING_TOP);
        tab.document = document;
        replace(tab, searchable(tab, document, page), store);
    }

    /**
     * Die Dateianhänge der Seite im Reiter {@code key} (alle, gleich welchen Typs): die Büroklammer unten zeigt ihre
     * Zahl, die Liste dahinter öffnet jeden im Anhang-Overlay; {@code loader} holt Originaldatei und Vorschau.
     */
    public void showAttachments(String key, List<ReaderAttachment> attachments, ReaderAttachmentLoader loader) {
        Tab tab = tabs.get(key);
        if (tab == null) {
            return;
        }
        tab.attachments = attachments == null || loader == null ? Collections.<ReaderAttachment>emptyList()
                : Collections.unmodifiableList(new ArrayList<ReaderAttachment>(attachments));
        tab.attachmentLoader = loader;
        refresh();
    }

    // ------------------------------------------------------------------ Vorlesen

    /**
     * Schließt die Sprachausgabe an (dieselbe wie im Chat: Modell, Stimme, Stückelung, Fehlermeldung) und zeigt den
     * Play-Knopf unten neben der Info. Play liest den Text des aktiven Reiters ohne Bilder, Grafiken und Diagramme
     * vor, ein zweiter Klick beendet es, ein Doppelklick auf eine Textstelle liest ab dort neu; Reiterwechsel, Schließen und neuer Inhalt beenden es ebenfalls. Es gibt
     * immer nur eine Ausgabe: der Chat und der Lesebereich lösen sich gegenseitig ab.
     */
    public void setReadAloud(ReadAloudControl control) {
        if (control == null || readAloud != null) {
            return;
        }
        readAloud = control;
        control.addListener(() -> {
            if (!readAloud.isAvailable()) {
                stopReadAloud();
            }
            styleReadAloud();
        });
        toolbar.showTool(readAloudButton, true);
        styleReadAloud();
    }

    /**
     * Der Seitentext des Reiters zum Vorlesen, wenn die Anzeige ihn nicht selbst ist (Lese-Ansicht als HTML): die
     * kanonische Textfassung der Seite ({@code KnowledgeDocument.text()}), nie die Darstellung.
     */
    public void setSpeechText(String key, String text) {
        Tab tab = tabs.get(key);
        if (tab == null) {
            return;
        }
        tab.speechText = text;
        styleReadAloud();
    }

    /** Ob gerade eine Seite vorgelesen wird. */
    public boolean isReadingAloud() {
        return speakingTab != null;
    }

    ComposerButton readAloudButton() {
        return readAloudButton;
    }

    void setReadAloudNotice(java.util.function.Consumer<String> notice) {
        this.readAloudNotice = notice;
    }

    private void toggleReadAloud() {
        if (readAloud == null) {
            return;
        }
        if (speakingTab != null) {
            stopReadAloud();
            return;
        }
        if (!readAloud.isAvailable()) {
            readAloudNotice.accept(READ_ALOUD_UNAVAILABLE_TEXT + "\n\n" + readAloud.description());
            return;
        }
        Tab active = tabs.get(activeKey);
        String text = active == null ? "" : SpeechText.withoutVisuals(active.speechText);
        if (text.isEmpty()) {
            return;
        }
        speak(active, text);
    }

    /**
     * Doppelklick während des Vorlesens: trifft er eine vorlesbare Textstelle der vorgelesenen Seite, beginnt das
     * Vorlesen sofort ab dem angeklickten Wort neu; daneben (Bild, Leerraum, anderer Bereich) ändert sich nichts.
     *
     * @return ob neu begonnen wurde
     */
    boolean readAloudFrom(java.awt.Component component, Point point) {
        Tab active = tabs.get(activeKey);
        if (readAloud == null || speakingTab == null || speakingTab != active || component == null
                || active.content == null || !SwingUtilities.isDescendingFrom(component, active.content)) {
            return false;
        }
        String paragraph = null;
        int offset = -1;
        if (component instanceof javax.swing.text.JTextComponent) {
            javax.swing.text.JTextComponent text = (javax.swing.text.JTextComponent) component;
            int position = text.viewToModel(point);
            if (position < 0) {
                return false;
            }
            javax.swing.text.Element element = javax.swing.text.Utilities.getParagraphElement(text, position);
            try {
                paragraph = text.getDocument().getText(element.getStartOffset(),
                        element.getEndOffset() - element.getStartOffset());
            } catch (javax.swing.text.BadLocationException e) {
                return false;
            }
            offset = position - element.getStartOffset();
        } else if (component instanceof javax.swing.JTable) {
            javax.swing.JTable table = (javax.swing.JTable) component;
            int row = table.rowAtPoint(point);
            int column = table.columnAtPoint(point);
            Object value = row < 0 || column < 0 ? null : table.getValueAt(row, column);
            paragraph = value == null ? null : value.toString();
            offset = 0;
        }
        String spoken = MarkdownSpeech.spokenText(SpeechText.withoutVisuals(active.speechText));
        int start = ReadAloudStart.find(spoken, paragraph, offset);
        if (start < 0) {
            return false;
        }
        speak(active, spoken.substring(start));
        return true;
    }

    /** Liest {@code text} für den Reiter vor; ein laufendes Vorlesen endet vorher (dieselbe Sprachausgabe). */
    private void speak(Tab tab, String text) {
        final Object run = new Object();
        speakingTab = tab;
        speakingRun = run;
        styleReadAloud();
        readAloud.speak(text, () -> {
            if (speakingRun == run) {
                speakingTab = null;
                speakingRun = null;
                styleReadAloud();
            }
        });
    }

    /** Beendet das Vorlesen der Seite; idempotent. */
    private void stopReadAloud() {
        if (speakingTab == null) {
            return;
        }
        speakingTab = null;
        speakingRun = null;
        if (readAloud != null) {
            readAloud.stop();
        }
        styleReadAloud();
    }

    private void styleReadAloud() {
        if (readAloud == null) {
            return;
        }
        boolean speaking = speakingTab != null;
        Tab active = tabs.get(activeKey);
        boolean hasText = active != null && !SpeechText.withoutVisuals(active.speechText).isEmpty();
        readAloudButton.setIcon(speaking ? ComposerIcons.stop() : ComposerIcons.play());
        readAloudButton.setEmphasized(speaking);
        readAloudButton.setEnabled(speaking || hasText);
        String tooltip = speaking ? READ_ALOUD_STOP_TOOLTIP : !hasText ? READ_ALOUD_NO_TEXT_TOOLTIP
                : READ_ALOUD_PLAY_TOOLTIP;
        readAloudButton.setToolTipText(tooltip + " (" + readAloud.description() + ")");
    }

    /** Die Anhänge des Reiters (leer ohne). */
    public List<ReaderAttachment> attachments(String key) {
        Tab tab = tabs.get(key);
        return tab == null ? Collections.<ReaderAttachment>emptyList() : tab.attachments;
    }

    /** Die Anhangsliste des aktiven Reiters oder {@code null} ohne Anhänge. */
    JPopupMenu attachmentMenu() {
        final Tab tab = tabs.get(activeKey);
        if (tab == null || tab.attachments.isEmpty()) {
            return null;
        }
        final String key = tab.link.key();
        return ReaderAttachmentList.menu(tab.attachments, palette, index -> openAttachment(key, index));
    }

    /** Öffnet Anhang {@code index} des Reiters im Overlay (dasselbe Comic-Overlay wie die Diashow). */
    public void openAttachment(String key, int index) {
        Tab tab = key == null ? null : tabs.get(key);
        if (tab == null || index < 0 || index >= tab.attachments.size() || tab.attachmentLoader == null) {
            return;
        }
        ReaderAttachment attachment = tab.attachments.get(index);
        showTranscriptOverlay(new ImageSlideshowPanel(attachment, tab.attachmentLoader, fileOpener,
                attachmentRenderer), attachment.fileName());
    }

    /** Wie die Anhangsliste nach dem Klick auf die Büroklammer erscheint (Tests: ohne Bildschirm abfangen). */
    void setAttachmentMenuShower(java.util.function.Consumer<JPopupMenu> shower) {
        this.attachmentMenuShower = shower == null ? this::showAboveButton : shower;
    }

    private void showAboveButton(JPopupMenu list) {
        list.show(attachmentsButton, 0, -list.getPreferredSize().height);
    }

    /** Womit „Öffnen“ im Anhang-Overlay die Datei öffnet (Tests). */
    void setFileOpener(ReaderFileOpener opener) {
        this.fileOpener = opener == null ? ReaderFileOpener.system() : opener;
    }

    /** Der Thread, auf dem das Anhang-Overlay PDF-Seiten zeichnet (Tests: direkt). */
    void setAttachmentRenderer(java.util.concurrent.Executor renderer) {
        this.attachmentRenderer = renderer;
    }

    ComposerButton attachmentsButton() {
        return attachmentsButton;
    }

    private Icon attachmentIcon(int count) {
        return new CountBadgeIcon(ComposerIcons.paperclip(), count, palette.getAccentOrange(), palette.getInk());
    }

    /** Öffnet die Diashow des Reiters bei Bild {@code index} (ohne Bilder nichts). */
    public void openSlideshow(String key, int index) {
        Tab tab = key == null ? null : tabs.get(key);
        if (tab == null || tab.images == null || tab.images.images().isEmpty()) {
            return;
        }
        showTranscriptOverlay(new ImageSlideshowPanel(tab.images, Math.max(0, index)),
                "Bilder · " + tab.link.title());
    }

    /** Die offene Diashow oder {@code null}. */
    public ImageSlideshowPanel slideshow() {
        if (overlay == null) {
            return null;
        }
        java.awt.Component found = find(overlay, ImageSlideshowPanel.class);
        return found == null ? null : (ImageSlideshowPanel) found;
    }

    /** Die HTML-Ansicht des Reiters oder {@code null} (Markdown, Liste, Meldung). */
    public ReaderHtmlView htmlView(String key) {
        Tab tab = tabs.get(key);
        java.awt.Component found = tab == null || tab.content == null ? null : find(tab.content, ReaderHtmlView.class);
        return found == null ? null : (ReaderHtmlView) found;
    }

    // ------------------------------------------------------------------ Overlay (wie über dem Chatverlauf)

    @Override
    public void showTranscriptOverlay(JComponent content, String title) {
        closeTranscriptOverlay();
        overlay = new ComicOverlayPanel(title, content, this::closeTranscriptOverlay, palette);
        overlay.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE_OVERLAY_ACTION);
        overlay.getActionMap().put(CLOSE_OVERLAY_ACTION, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                closeTranscriptOverlay();
            }
        });
        layers.add(overlay, JLayeredPane.MODAL_LAYER);
        layers.revalidate();
        layers.repaint();
    }

    @Override
    public void showDiagramOverlay(String mermaidSource, String title) {
        showTranscriptOverlay(new MermaidViewerPanel(mermaidSource, mermaid), title);
    }

    @Override
    public void closeTranscriptOverlay() {
        if (overlay != null) {
            layers.remove(overlay);
            overlay = null;
            layers.revalidate();
            layers.repaint();
        }
    }

    private static java.awt.Component find(java.awt.Component root, Class<?> type) {
        if (type.isInstance(root)) {
            return root;
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                java.awt.Component found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        replace(tab, searchable(tab, scroll(list), list));
    }

    /** Eine Meldung statt des Inhalts (z. B. Quelle nicht erreichbar). */
    public void showProblem(String key, String text) {
        Tab tab = tabs.get(key);
        if (tab != null) {
            JLabel label = message(text);
            label.setForeground(ResearchUiPalette.DANGER_RED);
            tab.find = null;
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
        // wie MainframeMate: ein von Hand geschlossener Reiter fällt aus dem Verlauf
        while (backStack.remove(tab)) {
            // alle Vorkommen
        }
        while (forwardStack.remove(tab)) {
            // alle Vorkommen
        }
        if (lastActive == tab) {
            lastActive = null;
        }
        if (key.equals(activeKey)) {
            keys.remove(index);
            activeKey = keys.isEmpty() ? null : keys.get(Math.min(index, keys.size() - 1));
        }
        refresh();
        if (tabs.isEmpty() && listener != null) {
            listener.allClosed();
        }
    }

    // ------------------------------------------------------------------ Reihenfolge und Gruppen

    /**
     * Verschiebt den Reiter an Position {@code index} der Reihenfolge (gezählt ohne ihn selbst); Vorschau oder
     * Angeheftet bleiben erhalten. Landet er zwischen zwei Reitern derselben Gruppe, gehört er danach zu ihr; zieht man
     * ihn aus seiner Gruppe heraus, verlässt er sie. Die Reiter einer Gruppe bleiben nebeneinander.
     */
    public void moveTab(String key, int index) {
        moveTab(key, index, null);
    }

    /**
     * Wie {@link #moveTab(String, int)}; mit {@code join} tritt der Reiter dieser Gruppe bei, egal wo die Gruppe
     * gerade steht (Ablegen auf einem Gruppenreiter oder auf der Gruppenmarke, wie in Firefox).
     */
    private void moveTab(String key, int index, ReaderTabGroup join) {
        Tab moved = tabs.get(key);
        if (moved == null) {
            return;
        }
        List<Tab> order = new ArrayList<Tab>(tabs.values());
        order.remove(moved);
        int target = Math.max(0, Math.min(index, order.size()));
        order.add(target, moved);
        ReaderTabGroup left = target > 0 ? order.get(target - 1).group : null;
        ReaderTabGroup right = target + 1 < order.size() ? order.get(target + 1).group : null;
        if (join != null) {
            moved.group = join;
        } else if (left != null && left == right) {
            moved.group = left;
        } else if (moved.group != null && moved.group != left && moved.group != right) {
            moved.group = null;
        }
        reorder(order);
    }

    /** Legt eine neue Gruppe mit diesem Reiter an (Name darf leer sein). */
    public ReaderTabGroup newGroup(String key, String name, ReaderTabGroup.Tint tint) {
        Tab tab = tabs.get(key);
        if (tab == null) {
            return null;
        }
        ReaderTabGroup group = new ReaderTabGroup("g" + (groupCounter++), name, tint == null ? nextTint() : tint);
        tab.group = group;
        reorder(new ArrayList<Tab>(tabs.values()));
        return group;
    }

    /** Hängt den Reiter an das Ende der Gruppe. */
    public void addToGroup(String key, String groupId) {
        Tab tab = tabs.get(key);
        ReaderTabGroup group = group(groupId);
        if (tab == null || group == null || tab.group == group) {
            return;
        }
        tab.group = null;
        List<Tab> order = new ArrayList<Tab>(tabs.values());
        order.remove(tab);
        int last = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).group == group) {
                last = i;
            }
        }
        order.add(last + 1, tab);
        tab.group = group;
        reorder(order);
    }

    /** Nimmt den Reiter aus seiner Gruppe; er steht danach rechts neben ihr. */
    public void removeFromGroup(String key) {
        Tab tab = tabs.get(key);
        if (tab == null || tab.group == null) {
            return;
        }
        ReaderTabGroup group = tab.group;
        List<Tab> order = new ArrayList<Tab>(tabs.values());
        int position = order.indexOf(tab);
        order.remove(tab);
        int last = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).group == group) {
                last = i;
            }
        }
        order.add(last < 0 ? position : last + 1, tab);
        tab.group = null;
        reorder(order);
    }

    public void renameGroup(String groupId, String name) {
        ReaderTabGroup group = group(groupId);
        if (group != null) {
            group.rename(name);
            refresh();
        }
    }

    public void recolorGroup(String groupId, ReaderTabGroup.Tint tint) {
        ReaderTabGroup group = group(groupId);
        if (group != null) {
            group.recolor(tint);
            refresh();
        }
    }

    /** Klappt die Gruppe ein oder aus; eingeklappt bleibt nur ein aktiver Reiter der Gruppe sichtbar. */
    public void setGroupCollapsed(String groupId, boolean collapsed) {
        ReaderTabGroup group = group(groupId);
        if (group != null) {
            group.setCollapsed(collapsed);
            refresh();
        }
    }

    /** Löst die Gruppe auf; ihre Reiter bleiben offen. */
    public void dissolveGroup(String groupId) {
        for (Tab tab : tabs.values()) {
            if (tab.group != null && tab.group.id().equals(groupId)) {
                tab.group = null;
            }
        }
        refresh();
    }

    /** Schließt alle Reiter der Gruppe. */
    public void closeGroup(String groupId) {
        for (String key : tabKeys()) {
            Tab tab = tabs.get(key);
            if (tab != null && tab.group != null && tab.group.id().equals(groupId)) {
                close(key);
            }
        }
    }

    /** Die Gruppe des Reiters oder {@code null}. */
    public ReaderTabGroup groupOf(String key) {
        Tab tab = tabs.get(key);
        return tab == null ? null : tab.group;
    }

    /** Alle Gruppen in Reihenfolge der Reiter. */
    public List<ReaderTabGroup> groups() {
        Set<ReaderTabGroup> groups = new LinkedHashSet<ReaderTabGroup>();
        for (Tab tab : tabs.values()) {
            if (tab.group != null) {
                groups.add(tab.group);
            }
        }
        return Collections.unmodifiableList(new ArrayList<ReaderTabGroup>(groups));
    }

    /** Ergänzt das Kontextmenü jedes Reiters. */
    public void addTabMenuContributor(TabMenuContributor contributor) {
        if (contributor != null) {
            menuContributors.add(contributor);
        }
    }

    /** Das Kontextmenü des Reiters, wie es ein Rechtsklick zeigt. */
    public JPopupMenu tabMenu(String key) {
        final Tab tab = tabs.get(key);
        JPopupMenu menu = new JPopupMenu();
        if (tab == null) {
            return menu;
        }
        if (!tab.pinned) {
            menu.add(item(MENU_PIN, () -> pin(tab.link.key())));
        }
        for (TabMenuContributor contributor : menuContributors) {
            contributor.contribute(menu, tab.link);
        }
        if (tab.images != null && !tab.images.images().isEmpty()) {
            menu.add(item("Bilder (" + tab.images.images().size() + ")", () -> {
                select(tab.link.key());
                openSlideshow(tab.link.key(), 0);
            }));
        }
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        menu.add(item(MENU_NEW_GROUP, () -> askNewGroup(tab.link.key())));
        List<ReaderTabGroup> others = new ArrayList<ReaderTabGroup>(groups());
        others.remove(tab.group);
        if (!others.isEmpty()) {
            JMenu join = new JMenu(MENU_ADD_TO_GROUP);
            for (final ReaderTabGroup group : others) {
                JMenuItem entry = item(groupLabel(group), () -> addToGroup(tab.link.key(), group.id()));
                entry.setIcon(new Swatch(group.tint().color()));
                join.add(entry);
            }
            menu.add(join);
        }
        if (tab.group != null) {
            menu.add(item(MENU_REMOVE_FROM_GROUP, () -> removeFromGroup(tab.link.key())));
        }
        menu.addSeparator();
        menu.add(item(MENU_CLOSE, () -> close(tab.link.key())));
        return menu;
    }

    /** Das Kontextmenü einer Gruppe (Rechtsklick auf ihre Marke). */
    public JPopupMenu groupMenu(String groupId) {
        final ReaderTabGroup group = group(groupId);
        JPopupMenu menu = new JPopupMenu();
        if (group == null) {
            return menu;
        }
        menu.add(item(MENU_EDIT_GROUP, () -> {
            ReaderTabGroupDialog.Result answer = ReaderTabGroupDialog.show(this, ReaderTabGroupDialog.EDIT_TITLE,
                    group.name(), group.tint(), palette);
            if (answer != null) {
                group.recolor(answer.tint());
                renameGroup(group.id(), answer.name());
            }
        }));
        JMenu colors = new JMenu(MENU_GROUP_COLOR);
        for (final ReaderTabGroup.Tint tint : ReaderTabGroup.Tint.values()) {
            JMenuItem entry = item(tint.label(), () -> recolorGroup(group.id(), tint));
            entry.setIcon(new Swatch(tint.color()));
            colors.add(entry);
        }
        menu.add(colors);
        menu.add(item(group.isCollapsed() ? MENU_EXPAND_GROUP : MENU_COLLAPSE_GROUP,
                () -> setGroupCollapsed(group.id(), !group.isCollapsed())));
        menu.addSeparator();
        menu.add(item(MENU_DISSOLVE_GROUP, () -> dissolveGroup(group.id())));
        menu.add(item(MENU_CLOSE_GROUP, () -> closeGroup(group.id())));
        return menu;
    }

    public void select(String key) {
        if (tabs.containsKey(key)) {
            activeKey = key;
            refresh();
        }
    }

    /** Nach einer Änderung der Favoriten von außen: die Sterne neu zeichnen. */
    public void refreshFavorite() {
        refresh();
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

    /** Das Info-Symbol unten. */
    ComposerButton infoButton() {
        return infoButton;
    }

    /** Das Popup des Info-Symbols für den aktiven Reiter oder {@code null} (kein Reiter). */
    JPopupMenu pageInfoMenu() {
        Tab active = tabs.get(activeKey);
        return active == null ? null : ReaderPageInfo.menu(active.link, linkOpener, clipboard);
    }

    /** Ersetzt die Zwischenablage des Systems (für Tests). */
    void setClipboard(java.util.function.Consumer<String> clipboard) {
        this.clipboard = clipboard == null ? ReaderPageInfo.SYSTEM_CLIPBOARD : clipboard;
    }

    /** Die Kopfzeile des Reiters {@code key} (Titel, Stecknadel, Stern, Schließen) oder {@code null}. */
    JComponent tabChip(String key) {
        for (java.awt.Component component : strip.getComponents()) {
            if (component instanceof TabChip && ((TabChip) component).key.equals(key)) {
                return (TabChip) component;
            }
        }
        return null;
    }

    /** Die Marke der Gruppe {@code groupId} in der Reiterleiste oder {@code null}. */
    JComponent groupChip(String groupId) {
        for (java.awt.Component component : strip.getComponents()) {
            if (component instanceof GroupChip && ((GroupChip) component).group.id().equals(groupId)) {
                return (GroupChip) component;
            }
        }
        return null;
    }

    /** Stecknadel im Reiter {@code key}, {@code null} bei angehefteten Reitern. */
    ComposerButton tabPinButton(String key) {
        JComponent chip = tabChip(key);
        return chip == null ? null : ((TabChip) chip).pin;
    }

    /** Stern im Reiter {@code key}, {@code null} ohne Favoriten. */
    ComposerButton tabFavoriteButton(String key) {
        JComponent chip = tabChip(key);
        return chip == null ? null : ((TabChip) chip).favorite;
    }

    /** Schließen im Reiter {@code key}. */
    ComposerButton tabCloseButton(String key) {
        JComponent chip = tabChip(key);
        return chip == null ? null : ((TabChip) chip).close;
    }

    // ------------------------------------------------------------------ intern

    private ReaderTabGroup group(String groupId) {
        for (ReaderTabGroup group : groups()) {
            if (group.id().equals(groupId)) {
                return group;
            }
        }
        return null;
    }

    /** Übernimmt die Reihenfolge und rückt die Reiter jeder Gruppe hinter ihren ersten Reiter zusammen. */
    private void reorder(List<Tab> order) {
        List<Tab> normalized = new ArrayList<Tab>(order.size());
        for (Tab tab : order) {
            if (normalized.contains(tab)) {
                continue;
            }
            normalized.add(tab);
            if (tab.group != null) {
                for (Tab member : order) {
                    if (member != tab && member.group == tab.group && !normalized.contains(member)) {
                        normalized.add(member);
                    }
                }
            }
        }
        tabs.clear();
        for (Tab tab : normalized) {
            tabs.put(tab.link.key(), tab);
        }
        refresh();
    }

    private ReaderTabGroup.Tint nextTint() {
        Set<ReaderTabGroup.Tint> used = new LinkedHashSet<ReaderTabGroup.Tint>();
        for (ReaderTabGroup group : groups()) {
            used.add(group.tint());
        }
        for (ReaderTabGroup.Tint tint : ReaderTabGroup.Tint.values()) {
            if (!used.contains(tint)) {
                return tint;
            }
        }
        return ReaderTabGroup.Tint.values()[groupCounter % ReaderTabGroup.Tint.values().length];
    }

    /** Fragt Name (optional) und Farbe der neuen Gruppe ab, wie das Gruppenfeld in Firefox. */
    private void askNewGroup(String key) {
        ReaderTabGroupDialog.Result answer = ReaderTabGroupDialog.show(this, ReaderTabGroupDialog.NEW_TITLE, "",
                nextTint(), palette);
        if (answer != null) {
            newGroup(key, answer.name(), answer.tint());
        }
    }

    private static JMenuItem item(String text, final Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private static String groupLabel(ReaderTabGroup group) {
        return group.name().isEmpty() ? "Gruppe (" + group.tint().label() + ")" : group.name();
    }

    // ------------------------------------------------------------------ Drag and Drop

    private void dragStarted(String key, MouseEvent event) {
        dragKey = key;
        dragStart = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), strip);
        dragging = false;
    }

    private void dragMoved(MouseEvent event) {
        if (dragKey == null) {
            return;
        }
        Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), strip);
        if (!dragging && dragStart != null && Math.abs(point.x - dragStart.x) < 6) {
            return;
        }
        dragging = true;
        strip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        Drop drop = dropSlot(point.x);
        dropX = drop.x;
        dropTint = drop.group == null ? null : drop.group.tint().color();
        strip.repaint();
    }

    private void dragEnded(MouseEvent event) {
        String key = dragKey;
        Drop drop = null;
        if (key != null && dragging) {
            drop = dropSlot(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), strip).x);
        }
        dragKey = null;
        dragging = false;
        dropX = -1;
        dropTint = null;
        strip.setCursor(Cursor.getDefaultCursor());
        strip.repaint();
        if (drop != null) {
            moveTab(key, drop.index, drop.group);
        }
    }

    /**
     * Wohin ein gezogener Reiter fällt. Über der Marke einer Gruppe oder über einem ihrer Reiter tritt er der Gruppe
     * bei (vor bzw. hinter dem Reiter je nach Hälfte); sonst landet er vor dem ersten sichtbaren Reiter, dessen Mitte
     * rechts vom Zeiger liegt, oder am Ende, und die Nachbarn entscheiden wie bisher über die Gruppe.
     */
    private Drop dropSlot(int x) {
        List<String> order = new ArrayList<String>(tabs.keySet());
        int own = order.indexOf(dragKey);
        order.remove(dragKey);
        int lastRight = 0;
        for (java.awt.Component component : strip.getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
            int left = component.getX();
            int right = left + component.getWidth();
            if (component instanceof GroupChip) {
                ReaderTabGroup group = ((GroupChip) component).group;
                if (x >= left && x < right) {
                    int first = -1;
                    for (Tab tab : tabs.values()) {
                        if (tab.group == group && !tab.link.key().equals(dragKey)) {
                            first = order.indexOf(tab.link.key());
                            break;
                        }
                    }
                    return new Drop(first < 0 ? Math.max(0, own) : first, right + 1, group);
                }
                if (left > x) {
                    return new Drop(firstTabAfter(component, order), left - 3, null);
                }
                lastRight = right;
                continue;
            }
            if (!(component instanceof TabChip)) {
                continue;
            }
            TabChip chip = (TabChip) component;
            if (chip.key.equals(dragKey)) {
                lastRight = right;
                continue;
            }
            int index = order.indexOf(chip.key);
            boolean after = x >= left + component.getWidth() / 2;
            if (chip.group != null && x >= left && x < right) {
                return new Drop(after ? index + 1 : index, after ? right + 1 : left - 3, chip.group);
            }
            if (!after) {
                return new Drop(index, left - 3, null);
            }
            lastRight = right;
        }
        return new Drop(order.size(), lastRight + 1, null);
    }

    /** Index (ohne den gezogenen Reiter) des ersten Reiters rechts von {@code marker} in der Leiste. */
    private int firstTabAfter(java.awt.Component marker, List<String> order) {
        boolean seen = false;
        for (java.awt.Component component : strip.getComponents()) {
            if (component == marker) {
                seen = true;
            } else if (seen && component instanceof TabChip && !((TabChip) component).key.equals(dragKey)) {
                return order.indexOf(((TabChip) component).key);
            }
        }
        return order.size();
    }

    /** Ziel eines Ziehens: Index ohne den gezogenen Reiter, x der Einfügemarke, beizutretende Gruppe oder null. */
    private static final class Drop {
        final int index;
        final int x;
        final ReaderTabGroup group;

        Drop(int index, int x, ReaderTabGroup group) {
            this.index = index;
            this.x = x;
            this.group = group;
        }
    }

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
        replace(tab, content, null);
    }

    private void replace(Tab tab, JComponent content, ReaderImageStore images) {
        tab.images = images;
        // neuer Inhalt: der alte Text verstummt, den neuen setzt showDocument oder der Controller (setSpeechText)
        tab.speechText = null;
        if (tab == speakingTab) {
            stopReadAloud();
        }
        // neuer Inhalt: die Anhänge setzt der Controller danach neu (showAttachments)
        tab.attachments = Collections.<ReaderAttachment>emptyList();
        tab.attachmentLoader = null;
        for (java.awt.Component component : deck.getComponents()) {
            if (tab.card.equals(component.getName())) {
                deck.remove(component);
            }
        }
        content.setName(tab.card);
        tab.content = content;
        if (tab.document != null && !SwingUtilities.isDescendingFrom(tab.document, content)) {
            tab.document = null; // etwas anderes als das Dokument (Liste, Meldung)
        }
        deck.add(content, tab.card);
        refresh();
    }

    private void refresh() {
        strip.removeAll();
        ReaderTabGroup previous = null;
        for (Tab tab : tabs.values()) {
            if (tab.group != null && tab.group != previous) {
                strip.add(new GroupChip(tab.group));
            }
            previous = tab.group;
            boolean active = tab.link.key().equals(activeKey);
            TabChip chip = new TabChip(tab, active);
            chip.setVisible(tab.group == null || !tab.group.isCollapsed() || active);
            strip.add(chip);
        }
        strip.setVisible(!tabs.isEmpty());
        Tab active = tabs.get(activeKey);
        cards.show(deck, active == null ? EMPTY_CARD : active.card);
        if (!suppressHistory && lastActive != null && active != null && lastActive != active) {
            if (isOpen(lastActive) || lastActive.replaced) {
                backStack.push(lastActive);
            }
            forwardStack.clear();
        }
        if (active != null) {
            lastActive = active;
        }
        toolbar.updateNavigation(canGoBack(), canGoForward());
        toolbar.bind(active == null ? null : active.find, active == null ? null : active.findState);
        infoButton.setEnabled(active != null);
        int attached = active == null ? 0 : active.attachments.size();
        attachmentsButton.setIcon(attachmentIcon(attached));
        attachmentsButton.setToolTipText(ReaderAttachmentList.tooltip(attached));
        toolbar.showTool(attachmentsButton, attached > 0);
        if (speakingTab != null && speakingTab != active) {
            stopReadAloud(); // Reiterwechsel oder Schließen: die alte Seite liest nicht weiter
        }
        styleReadAloud();
        strip.revalidate();
        strip.repaint();
        deck.revalidate();
        deck.repaint();
    }

    private JLabel message(String text) {
        JLabel label = new JLabel("<html><div style='text-align:center'>" + text + "</div></html>",
                SwingConstants.CENTER);
        label.setFont(ResearchUiTypography.regular(13f));
        label.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        return label;
    }

    /** Der Inhalt; die Suche der Leiste unten durchsucht nur diesen Reiter. */
    private JComponent searchable(Tab tab, JComponent content, final JComponent view) {
        final ReaderFind find = new ReaderFind(view);
        if (content instanceof NavigableDocumentPage) {
            // Treffer unter dem Wolkenbanner der Dokumentnavigation anzeigen, nicht dahinter
            final NavigableDocumentPage document = (NavigableDocumentPage) content;
            find.setCoveredTop(() -> document.sky().coveredHeight());
        }
        tab.find = find;
        tab.findState.searched = null; // neuer Inhalt, noch nichts markiert
        return content;
    }

    private JScrollPane scroll(JComponent view) {
        // Waagrecht nur, wenn etwas nicht umbrechen kann (breite Tabelle): rollen statt rechts abschneiden.
        JScrollPane scroll = new ComicScrollPane(view, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
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
        ReaderFind find;
        final ReaderToolbar.FindState findState = new ReaderToolbar.FindState();
        ReaderTabGroup group;
        /** Durch einen Klick als Vorschau ersetzt; Zurück holt sie wieder. */
        boolean replaced;
        /** Die Bilder der Seite, nur bei {@link #showHtml}. */
        ReaderImageStore images;
        /** Der vorlesbare Seitentext (Markdown wie {@code KnowledgeDocument.text()}); {@code null}: keiner. */
        String speechText;
        /** Die Dateianhänge der Seite (alle Typen), nur bei Quellen mit Lese-Ansicht. */
        List<ReaderAttachment> attachments = Collections.<ReaderAttachment>emptyList();
        ReaderAttachmentLoader attachmentLoader;

        Tab(ReaderLink link, boolean pinned, String card) {
            this.link = link;
            this.pinned = pinned;
            this.card = card;
        }
    }

    /**
     * Ein Reiter oben: Titel (kursiv als Vorschau), Stecknadel (nur Vorschau), Stern (Favorit), Schließen ganz rechts;
     * Klick wählt, Doppelklick heftet an.
     */
    private final class TabChip extends JPanel {
        private final boolean active;
        private final String key;
        private final ReaderTabGroup group;
        private final ComposerButton pin;
        private final ComposerButton favorite;
        private final ComposerButton close;

        TabChip(final Tab tab, boolean active) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            this.active = active;
            this.key = tab.link.key();
            this.group = tab.group;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tabTooltip(tab));
            JLabel title = new JLabel(shorten(tab.link.title()));
            Font font = ResearchUiTypography.regular(12f);
            title.setFont(tab.pinned ? font : font.deriveFont(Font.ITALIC));
            title.setForeground(active ? palette.getInk() : ResearchUiPalette.LIGHT_CONTROL_TEXT);
            title.setToolTipText(getToolTipText());
            add(title);
            if (tab.pinned) {
                pin = null;
            } else {
                pin = tabButton(ComposerIcons.pin(), PIN_TOOLTIP);
                pin.addActionListener(event -> pin(tab.link.key()));
                add(pin);
            }
            if (listener != null && listener.canFavorite()) {
                boolean isFavorite = listener.isFavorite(tab.link);
                favorite = tabButton(SidebarIcons.star(isFavorite), isFavorite ? FAVORITE_REMOVE : FAVORITE_ADD);
                favorite.addActionListener(event -> {
                    if (listener != null) {
                        listener.favoriteToggled(tab.link);
                        refresh();
                    }
                });
                add(favorite);
            } else {
                favorite = null;
            }
            close = tabButton(ComposerIcons.close(), CLOSE_TOOLTIP);
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

                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.isPopupTrigger()) {
                        showMenu(event);
                    } else if (SwingUtilities.isLeftMouseButton(event)) {
                        dragStarted(tab.link.key(), event);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) {
                        showMenu(event);
                    } else if (SwingUtilities.isLeftMouseButton(event)) {
                        dragEnded(event);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    dragMoved(event);
                }

                private void showMenu(MouseEvent event) {
                    tabMenu(tab.link.key()).show(event.getComponent(), event.getX(), event.getY());
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            title.addMouseListener(mouse);
            title.addMouseMotionListener(mouse);
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
                if (group != null) {
                    // Wie Firefox: eine Linie in der Gruppenfarbe unter jedem Reiter der Gruppe.
                    g2.setColor(group.tint().color());
                    g2.fillRoundRect(6, getHeight() - 3, getWidth() - 12, 3, 3, 3);
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /** Die Marke einer Gruppe vor ihren Reitern: Farbe und Name; Klick klappt ein/aus, Rechtsklick öffnet ihr Menü. */
    private final class GroupChip extends JLabel {
        private final ReaderTabGroup group;

        GroupChip(final ReaderTabGroup group) {
            super(group.name().isEmpty() ? " " : shorten(group.name()));
            this.group = group;
            setFont(ResearchUiTypography.semiBold(12f));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(5, group.name().isEmpty() ? 5 : 10, 5,
                    group.name().isEmpty() ? 5 : 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText((group.name().isEmpty() ? "Gruppe" : group.name())
                    + (group.isCollapsed() ? " (eingeklappt): Klick klappt aus" : ": Klick klappt ein"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        setGroupCollapsed(group.id(), !group.isCollapsed());
                    }
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    showMenu(event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    showMenu(event);
                }

                private void showMenu(MouseEvent event) {
                    if (event.isPopupTrigger()) {
                        groupMenu(group.id()).show(event.getComponent(), event.getX(), event.getY());
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                Color tint = group.tint().color();
                ResearchUiPainter.fillRound(g2, 0, 2, getWidth(), getHeight() - 4, ResearchUiMetrics.RADIUS_CONTROL,
                        group.isCollapsed() ? ResearchUiPainter.mix(tint, Color.WHITE, 0.35f) : tint);
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /** Ein gezeichnetes Farbfeld für die Gruppenfarben in Menüs und im Dialog. */
    private static final class Swatch implements Icon {
        private final Color color;

        Swatch(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                g2.setColor(color);
                g2.fillRoundRect(x + 1, y + 1, 12, 12, 6, 6);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    private static ComposerButton tabButton(Icon icon, String tooltip) {
        ComposerButton button = ComposerButton.iconButton(icon, tooltip);
        button.setPreferredSize(new Dimension(TAB_BUTTON_SIZE, TAB_BUTTON_SIZE));
        return button;
    }

    /** Ungekürzter Titel; beim Vorschau-Reiter mit dem Hinweis auf die Stecknadel. */
    static String tabTooltip(String title, boolean pinned) {
        String escaped = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return pinned ? "<html>" + escaped + "</html>" : "<html>" + escaped + "<br><i>" + PREVIEW_HINT + "</i></html>";
    }

    private static String tabTooltip(Tab tab) {
        return tabTooltip(tab.link.title(), tab.pinned);
    }

    private static String shorten(String title) {
        return title.length() <= 28 ? title : title.substring(0, 27).trim() + "…";
    }

    /** Jede Ebene (Inhalt, Overlay) füllt den Lesebereich. */
    private static final class FillLayout implements java.awt.LayoutManager {
        @Override
        public void addLayoutComponent(String name, java.awt.Component comp) {
        }

        @Override
        public void removeLayoutComponent(java.awt.Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container parent) {
            Dimension size = new Dimension();
            for (java.awt.Component child : parent.getComponents()) {
                Dimension preferred = child.getPreferredSize();
                size.width = Math.max(size.width, preferred.width);
                size.height = Math.max(size.height, preferred.height);
            }
            return size;
        }

        @Override
        public Dimension minimumLayoutSize(java.awt.Container parent) {
            return new Dimension();
        }

        @Override
        public void layoutContainer(java.awt.Container parent) {
            for (java.awt.Component child : parent.getComponents()) {
                child.setBounds(0, 0, parent.getWidth(), parent.getHeight());
            }
        }
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
                int width = Math.max(getParent().getWidth(), minimumWidth());
                int inner = width - insets.left - insets.right;
                return new Dimension(width, view.preferredHeightForWidth(inner) + insets.top + insets.bottom);
            }
            return super.getPreferredSize();
        }

        /** Breiter als das, was nicht umbrechen kann (z. B. eine Tabelle mit festen Spalten), wird die Seite nie. */
        private int minimumWidth() {
            Insets insets = getInsets();
            return getComponentCount() == 0 ? 0
                    : getComponent(0).getMinimumSize().width + insets.left + insets.right;
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

        /** Folgt der Breite, solange alles hineinpasst; sonst rollt der Lesebereich waagrecht statt abzuschneiden. */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return widthAwareView() == null || getParent() == null || getParent().getWidth() >= minimumWidth();
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
