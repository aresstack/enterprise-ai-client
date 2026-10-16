package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.document.ExportSaveDialog;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.ChatHistoryRow;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.ChatSidebarPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.ChatSidebarTab;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.SidebarTabRibbon;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.SidebarFooterPill;
import com.aresstack.enterpriseai.ui.comic.control.ComicOverlayPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComicSearchBar;
import com.aresstack.enterpriseai.ui.comic.control.ComicSplitPane;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.control.ResearchIconButton;
import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.control.ResearchPillDropdown;
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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Die Arbeitsfläche nach askai-java8 (arch, {@code ChatWorkspacePanel}):
 *
 * <pre>
 * ┌────────────────────────────────────────────────┐
 * │ ☰ [ Chat ▾ ] ‹Reiter›      Titel          ✕    │  schlanke Kopfzeile (zieht das Fenster)
 * ├────────────┬───────────────────────────────────┤
 * │ Drawer     │ Chat-Ansicht | Agent-Ansicht      │  ComicSplitPane, Drawer eingeklappt
 * │ Chats      │ (CardLayout: genau eine sichtbar) │
 * │ Wissensq.  │                                   │
 * └────────────┴───────────────────────────────────┘
 * </pre>
 *
 * Der Hamburger öffnet Reiterleiste und Drawer beim Überfahren und rastet mit einem Klick ein; ohne Rastung
 * falten beide zusammen weg, sobald der Zeiger den Bereich verlässt. Die Modus-Pille neben dem Hamburger
 * wechselt zwischen Chat und Agent (Navigation, kein Reiterband). Der Drawer trägt die Seite „Chats“ mit
 * „+ Neuer Chat“, der Chat-Suche und den Chat-Zeilen (laufender Chat, Agent, gespeicherte Chats), die
 * Seite „Wissensquellen“ ({@link KnowledgeSourcesPanel}: hinzufügen, bearbeiten, an- und abwählen, indexieren) und
 * die weiteren Reiter; das Zahnrad für die Einstellungen steht einmal im Fuß des Drawers, auf allen Reitern. Beide Ansichten sind vollständige, voneinander unabhängige {@link ChatShellPanel}s
 * mit eigenem Model: eine im Hintergrund laufende Agent-Antwort schreibt nicht in den Chat.
 */
public final class ChatWorkspacePanel extends JPanel implements ShellModeModel.Listener {

    public static final String CHATS_TAB = "Chats";
    public static final String KNOWLEDGE_TAB = "Wissensquellen";
    public static final String FAVORITES_TAB = "Favoriten";
    /** The compass (issue #97): page tree and cross references of the page open in the reader. */
    public static final String NAVIGATION_TAB = "Navigation";
    /** Die Karte des Lesebereichs neben Chat und Agent im Deck. */
    static final String READER_CARD = "READER";
    private final List<ChatSidebarTab> agentTabs = new ArrayList<ChatSidebarTab>();
    static final String NEW_CHAT_LABEL = "+ Neuer Chat";
    static final String SETTINGS_TOOLTIP = "Einstellungen";
    static final String NEW_CHAT_TITLE = "Neuer Chat";
    static final String AGENT_TITLE = "Agent";

    static final int SIDEBAR_MIN_WIDTH = 240;
    static final int SIDEBAR_MAX_WIDTH = 560;
    static final int SIDEBAR_DEFAULT_WIDTH = 360; // wie askai arch
    private static final int HOVER_MARGIN_PX = 12;
    private static final int SIDEBAR_CLOSE_DELAY_MS = 300;
    private static final int SIDEBAR_WIDTH_SAVE_DELAY_MS = 300;
    static final String STATE_BURGER_PINNED = "chat.burgerPinned";
    static final String STATE_SIDEBAR_WIDTH = "chat.sidebar.width";
    /** The arrangement of the navigation sections (#114): one global preference, never per page or tab. */
    static final String STATE_NAVIGATION_ORDER = "reader.navigation.sections.order";
    static final String STATE_NAVIGATION_VISIBLE = "reader.navigation.sections.visible";
    static final String STATE_NAVIGATION_SIZES = "reader.navigation.sections.sizes";
    private static final int LIST_REFRESH_DELAY_MS = 150;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY);
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    static final String DELETE_CHAT_LABEL = "Chat löschen";
    static final String RENAME_CHAT_LABEL = "Umbenennen …";
    static final String EXPORT_CHAT_LABEL = "Chat exportieren …";
    static final String RESTORE_CHAT_LABEL = "Wiederherstellen";
    static final String DELETED_META = "Gelöscht";

    private final ShellModeModel modes;
    private final ChatShellPanel chatShell;
    private final ChatShellPanel agentShell;
    private final ComicPalette palette;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    private final JPanel topBar = new JPanel(new BorderLayout(4, 0));
    private final JLabel titleLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final ComposerButton burger;
    private final ResearchPillDropdown modePill;
    private final SidebarTabRibbon ribbon = new SidebarTabRibbon();
    private final ChatSidebarPanel sidebar;
    private final ComicSplitPane sidebarSplit;

    private final ComicSearchBar chatFilter;
    private final JPanel chatListPanel = new JPanel();
    private final ResearchPillButton newChatButton;
    private final ResearchIconButton settingsButton;
    private final KnowledgeSourcesPanel knowledgePane;
    private final KnowledgeReaderPanel reader;
    private final FavoritesPanel favoritesPane;
    private final PageNavigationPanel navigationPane;
    private final List<ChatHistoryRow> chatRows = new ArrayList<ChatHistoryRow>();

    private final Timer sidebarCloseTimer;
    private final Timer listRefreshTimer;
    private AWTEventListener sidebarMouseWatcher;
    private boolean menuLocked;
    private WorkspaceActions actions;
    /** Fasst die Ereignisse eines Ziehens am Teiler zu EINEM Schreibvorgang zusammen. */
    private final Timer sidebarWidthSaveTimer;
    private int pendingSidebarWidth = -1;
    /** Collects the layout changes of a divider drag into ONE write. */
    private final Timer navigationLayoutSaveTimer;
    private NavigationSectionLayout pendingNavigationLayout;
    private ApplicationStateService applicationState;

    /**
     * @param agentShell eine eigene Agent-Ansicht oder {@code null}: dann schaltet der Agent-Modus nur Fähigkeiten und
     *                   Erscheinung desselben Chats um (askai arch)
     */
    public ChatWorkspacePanel(ShellModeModel modes, ChatShellPanel chatShell, ChatShellPanel agentShell,
                              ComicPalette palette) {
        super(new BorderLayout());
        if (modes == null || chatShell == null || palette == null) {
            throw new IllegalArgumentException("modes, chatShell and palette must not be null");
        }
        if (agentShell != null && !modes.isAgentAvailable()) {
            throw new IllegalArgumentException("agentShell needs the agent mode");
        }
        this.modes = modes;
        this.chatShell = chatShell;
        this.agentShell = agentShell;
        this.palette = palette;
        this.burger = ComposerButton.sidebarToggle("Chats und Wissensquellen");
        this.modePill = buildModePill();
        this.chatFilter = new ComicSearchBar("Chats durchsuchen…", "Chats nach Titel filtern", palette);
        this.newChatButton = SidebarFooterPill.create(NEW_CHAT_LABEL, "Neuen Chat beginnen");
        this.settingsButton = new ResearchIconButton(ComposerIcons.gear(), SETTINGS_TOOLTIP);
        this.knowledgePane = new KnowledgeSourcesPanel(palette);
        this.reader = new KnowledgeReaderPanel(palette);
        this.favoritesPane = new FavoritesPanel(palette);
        this.navigationPane = new PageNavigationPanel(palette);
        this.sidebar = new ChatSidebarPanel(CHATS_TAB, buildChatsTab(), palette);
        sidebar.setDefaultTabIcon(SidebarIcons.scroll());
        sidebar.setDefaultTabFooterLeadingComponent(newChatButton);
        // Das Zahnrad öffnet globale Einstellungen: einmal im Fuß des Drawers, auf allen Reitern sichtbar.
        settingsButton.addActionListener(event -> {
            if (actions != null) {
                actions.settingsRequested();
            }
        });
        sidebar.setFooterTrailingComponent(settingsButton);
        this.sidebarSplit = new ComicSplitPane(sidebar, deck, SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH, palette);
        this.sidebarCloseTimer = new Timer(SIDEBAR_CLOSE_DELAY_MS, event -> onPointerLeftSidebarArea());
        sidebarCloseTimer.setRepeats(false);
        this.listRefreshTimer = new Timer(LIST_REFRESH_DELAY_MS, event -> refreshChatList());
        listRefreshTimer.setRepeats(false);
        this.sidebarWidthSaveTimer = new Timer(SIDEBAR_WIDTH_SAVE_DELAY_MS, event -> saveSidebarWidthNow());
        sidebarWidthSaveTimer.setRepeats(false);
        this.navigationLayoutSaveTimer = new Timer(SIDEBAR_WIDTH_SAVE_DELAY_MS, event -> saveNavigationLayoutNow());
        navigationLayoutSaveTimer.setRepeats(false);
        navigationPane.setSectionLayoutListener(layout -> {
            pendingNavigationLayout = layout;
            navigationLayoutSaveTimer.restart();
        });
        sidebarSplit.setLeftWidthListener(width -> {
            pendingSidebarWidth = width;
            sidebarWidthSaveTimer.restart();
        });

        setBackground(palette.getSurface());
        deck.setBackground(palette.getSurface());
        deck.add(chatShell, ShellMode.CHAT.name());
        if (agentShell != null) {
            deck.add(agentShell, ShellMode.AGENT.name());
        }
        deck.add(reader, READER_CARD);
        buildTopLevelLayout();
        watchModels();
        modes.addListener(this);
        modeChanged(modes.getMode());
    }

    // ------------------------------------------------------------------ Anschluss nach außen

    /** Neuer Chat und Einstellungen; ohne Aktionen sind die Knöpfe wirkungslos. */
    public void setActions(WorkspaceActions actions) {
        this.actions = actions;
    }

    /**
     * Bindet den Anwendungszustand, damit Drawer-Breite und eingerasteter Drawer einen Neustart überleben, und
     * stellt beides sofort wieder her (wie askai arch). Einmal nach dem Aufbau aufrufen.
     */
    public void setApplicationState(ApplicationStateService applicationState) {
        this.applicationState = applicationState;
        if (applicationState == null) {
            return;
        }
        // Erst die Breite, dann öffnet ein eingerasteter Drawer schon in ihr.
        int width = applicationState.getInt(STATE_SIDEBAR_WIDTH, -1);
        if (width > 0) {
            sidebarSplit.setPreferredLeftWidth(width);
        }
        if (applicationState.getBoolean(STATE_BURGER_PINNED, false)) {
            openDrawer();
        }
        navigationPane.setSectionLayout(NavigationSectionLayout.parse(
                applicationState.get(STATE_NAVIGATION_ORDER, null),
                applicationState.get(STATE_NAVIGATION_VISIBLE, null),
                applicationState.get(STATE_NAVIGATION_SIZES, null)));
    }

    /** Write the last arrangement of the navigation sections (no effect while no state is bound). */
    void saveNavigationLayoutNow() {
        navigationLayoutSaveTimer.stop();
        if (applicationState != null && pendingNavigationLayout != null) {
            Map<String, String> entries = new LinkedHashMap<String, String>();
            entries.put(STATE_NAVIGATION_ORDER, pendingNavigationLayout.encodeOrder());
            entries.put(STATE_NAVIGATION_VISIBLE, pendingNavigationLayout.encodeVisible());
            entries.put(STATE_NAVIGATION_SIZES, pendingNavigationLayout.encodeSizes());
            applicationState.putAllAndSave(entries);
            pendingNavigationLayout = null;
        }
    }

    /** Merkt, ob der Drawer eingerastet ist (wirkungslos, solange kein Zustand gebunden ist). */
    private void persistBurgerPinned() {
        if (applicationState != null) {
            applicationState.putAndSave(STATE_BURGER_PINNED, Boolean.toString(menuLocked));
        }
    }

    /** Schreibt die zuletzt gezogene Drawer-Breite (wirkungslos, solange kein Zustand gebunden ist). */
    private void saveSidebarWidthNow() {
        sidebarWidthSaveTimer.stop();
        if (applicationState != null && pendingSidebarWidth > 0) {
            applicationState.putAndSave(STATE_SIDEBAR_WIDTH, Integer.toString(pendingSidebarWidth));
        }
    }

    /** Der stille Titel in der Kopfzeile (der Fenstertitel aus der Konfiguration). */
    public void setWindowTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
    }

    /** Die Fensterknöpfe (das ✕) ganz rechts in der Kopfzeile; {@code null} entfernt sie. */
    public void setWindowControls(JComponent controls) {
        windowControls.removeAll();
        if (controls != null) {
            windowControls.add(controls);
        }
        windowControls.revalidate();
        windowControls.repaint();
    }

    /** Die Wissensquellen für die Drawer-Seite „Wissensquellen“ (auf dem EDT). */
    public void setKnowledgeSources(List<KnowledgeSourceItem> sources) {
        knowledgePane.setItems(sources);
    }

    /** Was Häkchen, „Jetzt indexieren“, Bearbeiten und Hinzufügen der Seite „Wissensquellen“ tun. */
    public void setKnowledgeSourceActions(KnowledgeSourceActions sourceActions) {
        knowledgePane.setActions(sourceActions);
    }

    /** Die Drawer-Seite „Wissensquellen“. */
    public KnowledgeSourcesPanel knowledgeSources() {
        return knowledgePane;
    }

    /** Ein Drawer-Reiter, der nur im Agent-Modus erscheint (askai: Artefakt-Ansicht des Agenten). */
    public void addAgentTab(final String title, final JComponent component) {
        agentTabs.add(new ChatSidebarTab() {
            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public JComponent getComponent() {
                return component;
            }
        });
        sidebar.rebuildTabs();
        refreshRibbonTabs();
    }

    /** Die Drawer-Seite „Favoriten“. */
    public FavoritesPanel favorites() {
        return favoritesPane;
    }

    /** The drawer page "Navigation" (compass). */
    public PageNavigationPanel navigation() {
        return navigationPane;
    }

    /** Der Lesebereich (Karte statt des Chats). */
    public KnowledgeReaderPanel reader() {
        return reader;
    }

    /**
     * Navigate to a workspace tab exactly like a click on it in the ribbon: mark it in drawer and ribbon and show the
     * card that belongs to it ("Chats" the chat or agent view, the reader tabs the reader). Other tabs (agent
     * artifacts) keep the current card. Leave the drawer open, closed or pinned as it is.
     */
    public void selectWorkspaceTab(String title) {
        sidebar.showTab(title);
        refreshRibbonTabs();
        if (CHATS_TAB.equals(title)) {
            showConversationCard();
        } else if (isReaderTab(title)) {
            showReaderCard();
        }
    }

    /** Whether the tab turns the chat area into the reader (sources, favorites, navigation). */
    public static boolean isReaderTab(String title) {
        return KNOWLEDGE_TAB.equals(title) || FAVORITES_TAB.equals(title) || NAVIGATION_TAB.equals(title);
    }

    /**
     * Start a new chat through the same path as "+ Neuer Chat" and show it (#113). Leave everything as it is when no
     * new chat can be started, so nothing meant for the new chat lands in the old one.
     *
     * @return {@code true} if the new chat is the current one now
     */
    public boolean startNewChat() {
        if (actions == null || !actions.newChatRequested(modes.getMode())) {
            return false;
        }
        showChatArea();
        refreshChatList();
        return true;
    }

    /**
     * Show the reader. Keep the active tab if it is a reader tab (a favorite opened from its tree stays on
     * "Favoriten"); otherwise mark "Wissensquellen" so that card and tab never disagree.
     */
    public void showReader() {
        selectWorkspaceTab(isReaderTab(sidebar.activeTab()) ? sidebar.activeTab() : KNOWLEDGE_TAB);
    }

    /** Show the reader as the knowledge area and mark "Wissensquellen" (like a click on the flask). */
    public void showKnowledgeArea() {
        selectWorkspaceTab(KNOWLEDGE_TAB);
    }

    /**
     * Back from the reader to the chat or agent view. Leave a reader tab for "Chats" so that card and tab never
     * disagree; keep any other tab (agent artifacts belong to the conversation).
     */
    public void showChatArea() {
        if (isReaderTab(sidebar.activeTab())) {
            selectWorkspaceTab(CHATS_TAB);
        } else {
            showConversationCard();
        }
    }

    /** Switch only the central card to the reader; navigate through {@link #selectWorkspaceTab}. */
    private void showReaderCard() {
        readerShowing = true;
        cards.show(deck, READER_CARD);
    }

    /** Switch only the central card to the chat or agent view; navigate through {@link #selectWorkspaceTab}. */
    private void showConversationCard() {
        readerShowing = false;
        ShellMode mode = modes.getMode();
        cards.show(deck, mode == ShellMode.AGENT && agentShell != null ? mode.name() : ShellMode.CHAT.name());
    }

    public boolean isReaderShowing() {
        return readerShowing;
    }

    private boolean readerShowing;

    @Override
    public void modeChanged(ShellMode mode) {
        sidebar.rebuildTabs();
        refreshRibbonTabs();
        showChatArea();
        int index = Arrays.asList(ShellMode.values()).indexOf(mode);
        if (modePill.getSelectedIndex() != index) {
            modePill.setSelectedIndex(index);
        }
        refreshChatList();
    }

    /** Die gerade sichtbare Ansicht. */
    public ChatShellPanel visibleShell() {
        return modes.getMode() == ShellMode.AGENT && agentShell != null ? agentShell : chatShell;
    }

    public ChatShellPanel chatShell() {
        return chatShell;
    }

    /** Die Agent-Ansicht oder {@code null} ohne Agent. */
    public ChatShellPanel agentShell() {
        return agentShell;
    }

    public ShellModeModel modes() {
        return modes;
    }

    /** Die schlanke Kopfzeile — im rahmenlosen Fenster zugleich die Zieh-Fläche. */
    public JComponent topBar() {
        return topBar;
    }

    public ComposerButton burger() {
        return burger;
    }

    public ResearchPillDropdown modePill() {
        return modePill;
    }

    public SidebarTabRibbon ribbon() {
        return ribbon;
    }

    public ChatSidebarPanel sidebar() {
        return sidebar;
    }

    public ComicSplitPane splitPane() {
        return sidebarSplit;
    }

    public ResearchPillButton newChatButton() {
        return newChatButton;
    }

    public ResearchIconButton settingsButton() {
        return settingsButton;
    }

    public ComicSearchBar chatFilter() {
        return chatFilter;
    }

    /** Die Chat-Zeilen des Drawers in Anzeigereihenfolge (für Tests). */
    public List<ChatHistoryRow> chatRows() {
        return Collections.unmodifiableList(new ArrayList<ChatHistoryRow>(chatRows));
    }

    public boolean isDrawerOpen() {
        return sidebar.isVisible();
    }

    /** Ob der Drawer per Klick eingerastet ist (bleibt offen, bis erneut geklickt wird). */
    public boolean isDrawerPinned() {
        return menuLocked;
    }

    /** Öffnet Reiterleiste und Drawer eingerastet — wie ein Klick auf den Hamburger. */
    public void openDrawer() {
        if (!menuLocked) {
            menuLocked = true;
            burger.setEmphasized(true);
            openMenuAndSidebar();
            persistBurgerPinned();
        }
    }

    /** Schließt Reiterleiste und Drawer und löst die Rastung. */
    public void closeDrawer() {
        collapseMenuAndSidebar();
    }

    // ------------------------------------------------------------------ Aufbau

    private ResearchPillDropdown buildModePill() {
        ResearchPillDropdown pill = new ResearchPillDropdown(28, ResearchUiMetrics.RADIUS_CONTROL, 0, 12, 12);
        pill.setFont(ResearchUiTypography.regular(12.5f));
        pill.setToolTipText("Modus: Chat mit dem KI-Modell oder Aufträge an den Agenten");
        List<ResearchPillDropdown.Item> items = new ArrayList<ResearchPillDropdown.Item>();
        for (ShellMode mode : ShellMode.values()) {
            boolean enabled = mode != ShellMode.AGENT || modes.isAgentAvailable();
            String tooltip = mode == ShellMode.CHAT ? "Normaler Chat mit dem KI-Modell"
                    : enabled ? "Aufträge an den externen Agenten (ACP) schicken" : "Kein Agent konfiguriert";
            items.add(new ResearchPillDropdown.Item(mode.label(), null, enabled, tooltip));
        }
        pill.setItems(items);
        pill.setSelectionListener(index -> {
            ShellMode requested = ShellMode.values()[index];
            if (!modes.select(requested)) {
                modeChanged(modes.getMode()); // abgelehnt: Anzeige zurücksetzen
            }
        });
        return pill;
    }

    private void buildTopLevelLayout() {
        burger.addActionListener(event -> {
            if (menuLocked) {
                collapseMenuAndSidebar();
            } else {
                menuLocked = true;
                burger.setEmphasized(true);
                openMenuAndSidebar();
                persistBurgerPinned();
            }
        });
        burger.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (!sidebar.isVisible() && !ribbon.isOpen()) {
                    openMenuAndSidebar();
                }
            }
        });
        ribbon.setListener(title -> {
            if (!sidebar.isVisible()) {
                showSidebar();
            }
            selectWorkspaceTab(title);
        });

        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        JPanel topLeft = new JPanel();
        topLeft.setLayout(new BoxLayout(topLeft, BoxLayout.X_AXIS));
        topLeft.setOpaque(false);
        topLeft.add(burger);
        topLeft.add(Box.createHorizontalStrut(6));
        topLeft.add(modePill);
        topLeft.add(Box.createHorizontalStrut(6));
        topLeft.add(ribbon);
        topBar.add(topLeft, BorderLayout.WEST);
        titleLabel.setFont(ResearchUiTypography.regular(12f));
        titleLabel.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        topBar.add(titleLabel, BorderLayout.CENTER);
        windowControls.setOpaque(false);
        topBar.add(windowControls, BorderLayout.EAST);

        sidebar.setVisible(false);
        sidebar.setExtraTabsSupplier(() -> {
            List<ChatSidebarTab> tabs = new ArrayList<ChatSidebarTab>();
            tabs.add(new ChatSidebarTab() {
                @Override
                public String getTitle() {
                    return KNOWLEDGE_TAB;
                }

                @Override
                public JComponent getComponent() {
                    return knowledgePane;
                }

                @Override
                public javax.swing.Icon getIcon() {
                    return SidebarIcons.flask();
                }

                @Override
                public JComponent getFooterLeadingComponent() {
                    return knowledgePane.addButton();
                }
            });
            tabs.add(new ChatSidebarTab() {
                @Override
                public String getTitle() {
                    return FAVORITES_TAB;
                }

                @Override
                public JComponent getComponent() {
                    return favoritesPane;
                }

                @Override
                public javax.swing.Icon getIcon() {
                    return SidebarIcons.star(true);
                }
            });
            tabs.add(new ChatSidebarTab() {
                @Override
                public String getTitle() {
                    return NAVIGATION_TAB;
                }

                @Override
                public JComponent getComponent() {
                    return navigationPane;
                }

                @Override
                public javax.swing.Icon getIcon() {
                    return SidebarIcons.compass();
                }

                @Override
                public JComponent getFooterLeadingComponent() {
                    return navigationPane.sectionToggles();
                }
            });
            // askai AgentArtifactTabs: die Artefakt-Ansichten des Agenten nur, solange der Agent-Modus aktiv ist.
            if (modes.getMode() == ShellMode.AGENT) {
                tabs.addAll(agentTabs);
            }
            return tabs;
        });
        chatFilter.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshChatList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshChatList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshChatList();
            }
        });
        sidebar.setHeaderComponent(chatFilter);
        sidebar.rebuildTabs();
        refreshRibbonTabs();

        sidebarSplit.setPreferredLeftWidth(SIDEBAR_DEFAULT_WIDTH);
        sidebarSplit.collapseLeft();
        add(topBar, BorderLayout.NORTH);
        add(sidebarSplit, BorderLayout.CENTER);
    }

    private JComponent buildChatsTab() {
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListPanel.setOpaque(false);
        JScrollPane scroll = new ComicScrollPane(chatListPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        newChatButton.addActionListener(event -> {
            if (actions != null) {
                actions.newChatRequested(modes.getMode());
            }
            showChatArea();
            if (menuLocked) {
                refreshChatList();
            } else {
                collapseMenuAndSidebar();
            }
        });
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);
        tab.add(scroll, BorderLayout.CENTER);
        return tab;
    }

    private JLabel groupHeader(String text) {
        JLabel header = new JLabel(text);
        header.setFont(ResearchUiTypography.semiBold(11f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(14, 10, 4, 8));
        header.setAlignmentX(LEFT_ALIGNMENT);
        return header;
    }

    // ------------------------------------------------------------------ Chat-Zeilen

    private void watchModels() {
        ChatShellModelListener refresh = new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
                scheduleListRefresh();
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
                scheduleListRefresh();
            }

            @Override
            public void stateChanged() {
                scheduleListRefresh();
            }

            @Override
            public void entriesCleared() {
                scheduleListRefresh();
            }
        };
        chatShell.model().addListener(refresh);
        if (agentShell != null) {
            agentShell.model().addListener(refresh);
        }
    }

    private void scheduleListRefresh() {
        newChatButton.setEnabled(!visibleShell().model().isStreaming());
        if (sidebar.isVisible()) {
            listRefreshTimer.restart();
        }
    }

    /**
     * Baut die Chat-Zeilen neu, gefiltert nach der Chat-Suche: unter AKTIV je Ansicht eine Zeile (der laufende
     * Chat und der Agent), darunter die gespeicherten Chats nach Alter (HEUTE, GESTERN, LETZTE 7 TAGE, ÄLTER)
     * wie in askai-java8 arch. Ein Klick öffnet einen gespeicherten Chat, das {@code …}-Menü löscht ihn.
     */
    void refreshChatList() {
        listRefreshTimer.stop();
        chatListPanel.removeAll();
        chatRows.clear();
        projectRows.clear();
        newChatButton.setEnabled(!visibleShell().model().isStreaming());
        String filter = chatFilter.getText().trim().toLowerCase(Locale.ROOT);
        chatListPanel.add(groupHeader("AKTIV"));
        addChatRow(ShellMode.CHAT, chatShell.model(), NEW_CHAT_TITLE, "Chat", filter);
        if (agentShell != null) {
            addChatRow(ShellMode.AGENT, agentShell.model(), AGENT_TITLE, "Agent", filter);
        }
        addProjects(filter);
        addSavedChats(filter);
        if (chatRows.isEmpty() && projectRows.isEmpty() && renamingChatId == null) {
            JLabel none = new JLabel(filter.isEmpty() ? "Keine Chats" : "Keine passenden Chats"); // wie arch
            none.setEnabled(false);
            none.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            none.setAlignmentX(LEFT_ALIGNMENT);
            chatListPanel.add(none);
        }
        chatListPanel.add(Box.createVerticalGlue());
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    private void addChatRow(final ShellMode mode, ChatShellModel model, String fallbackTitle, String kind,
                            String filter) {
        String title = fallbackTitle;
        long lastMillis = 0L;
        int count = 0;
        for (TranscriptEntry entry : model.getEntries()) {
            if (entry.getAuthor() == TranscriptEntry.Author.USER && title.equals(fallbackTitle)
                    && !entry.getText().trim().isEmpty()) {
                title = entry.getText().trim();
            }
            if (entry.getAuthor() != TranscriptEntry.Author.NOTICE
                    && entry.getAuthor() != TranscriptEntry.Author.ACTIVITY) {
                count++;
            }
            lastMillis = Math.max(lastMillis, entry.getCreatedAtMillis());
        }
        String renamed = mode == ShellMode.CHAT && actions != null ? actions.currentChatTitle() : null;
        if (renamed != null && count > 0) {
            title = renamed;
        }
        if (!filter.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(filter)) {
            return;
        }
        String meta = kind + " · " + (count == 0 ? "noch keine Nachrichten"
                : count == 1 ? "1 Nachricht" : count + " Nachrichten");
        String currentProject = mode == ShellMode.CHAT ? currentProjectName() : null;
        if (currentProject != null) {
            meta = meta + " · " + currentProject;
        }
        String time = lastMillis == 0L ? "" : TIME.format(Instant.ofEpochMilli(lastMillis).atZone(ZoneId.systemDefault()));
        final String currentId = actions == null ? null : actions.currentChatId();
        ChatHistoryRow.MenuSupplier menu = null;
        if (mode == ShellMode.CHAT && currentId != null && count > 0) {
            final String rowTitle = title;
            menu = () -> chatMenu(currentId, rowTitle);
        }
        if (menu != null && currentId.equals(renamingChatId)) {
            chatListPanel.add(renameRow(currentId, title));
            return;
        }
        ChatHistoryRow row = new ChatHistoryRow(title, meta, time, model.isStreaming(), modes.getMode() == mode,
                () -> {
                    modes.select(mode);
                    showChatArea();
                }, menu, palette);
        row.setAlignmentX(LEFT_ALIGNMENT);
        if (menu != null) {
            makeDraggable(row, ChatProjectTransfer.CHAT + currentId, null);
        }
        chatRows.add(row);
        chatListPanel.add(row);
    }

    /** Die gespeicherten Chats ohne den laufenden, nach Alter gruppiert; leere Gruppen fehlen. */
    private void addSavedChats(String filter) {
        if (actions == null) {
            return;
        }
        String currentId = actions.currentChatId();
        long startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<SavedChatItem> today = new ArrayList<SavedChatItem>();
        List<SavedChatItem> yesterday = new ArrayList<SavedChatItem>();
        List<SavedChatItem> lastWeek = new ArrayList<SavedChatItem>();
        List<SavedChatItem> older = new ArrayList<SavedChatItem>();
        Set<String> projectIds = projectIds();
        for (SavedChatItem chat : actions.savedChats()) {
            if (chat.id().equals(currentId)
                    || !filter.isEmpty() && !chat.title().toLowerCase(Locale.ROOT).contains(filter)
                    || projectIds.contains(actions.projectOfChat(chat.id()))) {
                continue; // chats in a project are listed below their project
            }
            long at = chat.modifiedAtMillis();
            if (at >= startOfToday) {
                today.add(chat);
            } else if (at >= startOfToday - DAY_MILLIS) {
                yesterday.add(chat);
            } else if (at >= startOfToday - 6 * DAY_MILLIS) {
                lastWeek.add(chat);
            } else {
                older.add(chat);
            }
        }
        addSavedGroup("HEUTE", today, startOfToday);
        addSavedGroup("GESTERN", yesterday, startOfToday);
        addSavedGroup("LETZTE 7 TAGE", lastWeek, startOfToday);
        addSavedGroup("ÄLTER", older, startOfToday);
    }

    private void addSavedGroup(String title, List<SavedChatItem> group, long startOfToday) {
        if (group.isEmpty()) {
            return;
        }
        JLabel header = groupHeader(title);
        if (!projectIds().isEmpty()) {
            // Dropping a chat onto a date group takes it out of its project (#143).
            header.setTransferHandler(new ChatProjectTransfer(null, ChatProjectTransfer.Target.noProject(),
                    projectDrops));
            header.setToolTipText(NO_PROJECT_DROP_TOOLTIP);
        }
        chatListPanel.add(header);
        for (final SavedChatItem chat : group) {
            chatListPanel.add(savedChatRow(chat, startOfToday, 0));
        }
    }

    /** One saved chat as a row (or its rename field), indented by {@code depth} project levels. */
    private JComponent savedChatRow(final SavedChatItem chat, long startOfToday, int depth) {
        {
            if (chat.id().equals(renamingChatId)) {
                return indented(renameRow(chat.id(), chat.title()), depth);
            }
            ZonedDateTime at = Instant.ofEpochMilli(chat.modifiedAtMillis()).atZone(ZoneId.systemDefault());
            String time = chat.modifiedAtMillis() >= startOfToday ? TIME.format(at) : DAY.format(at);
            String meta = "Chat · " + (chat.messageCount() == 1 ? "1 Nachricht" : chat.messageCount() + " Nachrichten")
                    + (chat.attachmentCount() == 0 ? ""
                    : chat.attachmentCount() == 1 ? " · 1 Anhang" : " · " + chat.attachmentCount() + " Anhänge");
            boolean deleted = deletedChatIds.contains(chat.id());
            ChatHistoryRow row = deleted
                    ? new ChatHistoryRow(chat.title(), DELETED_META, time, false, false, true, null,
                    () -> restoreMenu(chat.id()), () -> restoreChat(chat.id()), palette)
                    : new ChatHistoryRow(chat.title(), meta, time, false, false,
                    () -> openSavedChat(chat.id()), () -> chatMenu(chat.id(), chat.title()), palette);
            row.setAlignmentX(LEFT_ALIGNMENT);
            if (!deleted) {
                makeDraggable(row, ChatProjectTransfer.CHAT + chat.id(), null);
            }
            chatRows.add(row);
            return indented(row, depth);
        }
    }

    private void openSavedChat(String chatId) {
        if (actions == null || chatShell.model().isStreaming()) {
            return;
        }
        modes.select(ShellMode.CHAT);
        showChatArea();
        actions.openSavedChatRequested(chatId);
        if (menuLocked) {
            refreshChatList();
        } else {
            collapseMenuAndSidebar();
        }
    }

    /** Der Chat, dessen Zeile gerade ein Umbenennen-Feld ist, oder {@code null}. */
    private String renamingChatId;

    /**
     * Umbenennen wie askai arch ({@code buildRenameRow}): die Zeile wird zu [Feld | ✕]; Enter übernimmt, Escape oder
     * ✕ bricht ab. Ein leerer Titel gilt als Abbruch.
     */
    private JComponent renameRow(final String chatId, String title) {
        final JTextField field = new JTextField(title);
        field.setFont(ResearchUiTypography.regular(13f));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.5f)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        final Runnable cancel = () -> {
            renamingChatId = null;
            refreshChatList();
        };
        field.addActionListener(event -> {
            String value = field.getText().trim();
            renamingChatId = null;
            if (!value.isEmpty() && actions != null) {
                actions.renameChatRequested(chatId, value);
            }
            refreshChatList();
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel.run();
                }
            }
        });
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        row.add(field, BorderLayout.CENTER);
        ComicOverlayPanel.CloseButton close = new ComicOverlayPanel.CloseButton(palette, cancel);
        close.setToolTipText("Abbrechen");
        JPanel closeWrap = new JPanel(new GridBagLayout());
        closeWrap.setOpaque(false);
        closeWrap.add(close);
        row.add(closeWrap, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        row.setAlignmentX(LEFT_ALIGNMENT);
        SwingUtilities.invokeLater(() -> {
            field.requestFocusInWindow();
            field.selectAll();
        });
        return row;
    }

    /**
     * Soft Delete wie askai arch: der Chat bleibt in diesem Lauf als „Gelöscht“ mit ↩ in der Liste; endgültig weg ist
     * er erst nach dem Beenden. Ersetzt die frühere Rückfrage.
     */
    private final Set<String> deletedChatIds = new LinkedHashSet<String>();

    private void deleteChat(String chatId) {
        if (actions == null) {
            return;
        }
        actions.deleteSavedChatRequested(chatId);
        if (!chatId.equals(actions.currentChatId())) { // sonst ließ sich der laufende Chat gerade nicht verlassen
            deletedChatIds.add(chatId);
        }
        refreshChatList();
    }

    private void restoreChat(String chatId) {
        if (deletedChatIds.remove(chatId) && actions != null) {
            actions.restoreSavedChatRequested(chatId);
        }
        refreshChatList();
    }

    private JPopupMenu restoreMenu(final String chatId) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem restore = new JMenuItem(RESTORE_CHAT_LABEL);
        restore.addActionListener(event -> restoreChat(chatId));
        menu.add(restore);
        return menu;
    }

    /** Das Menü einer Chat-Zeile: Umbenennen, Exportieren und Löschen (ohne Rückfrage, mit ↩ wie arch). */
    JPopupMenu chatMenu(final String chatId, final String title) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem(RENAME_CHAT_LABEL);
        rename.addActionListener(event -> {
            renamingChatId = chatId; // die Zeile verwandelt sich in ein Eingabefeld (arch), kein Dialog
            refreshChatList();
        });
        menu.add(rename);
        List<ChatProjectItem> projects = actions == null ? Collections.<ChatProjectItem>emptyList()
                : actions.chatProjects();
        if (!projects.isEmpty()) {
            menu.add(moveMenu(chatId, projects));
        }
        JMenuItem export = new JMenuItem(EXPORT_CHAT_LABEL);
        export.setEnabled(actions != null && actions.canExportChats());
        export.addActionListener(event -> exportChat(chatId, title));
        menu.add(export);
        JMenuItem delete = new JMenuItem(DELETE_CHAT_LABEL);
        delete.setEnabled(!chatShell.model().isStreaming());
        delete.addActionListener(event -> deleteChat(chatId));
        menu.add(delete);
        return menu;
    }

    /**
     * „Chat exportieren …“: der gemeinsame Speichern-Dialog aller Exporte (#132) mit PDF (voreingestellt), Word,
     * Markdown und JSON (maßgeblich); der Export kommt aus dem gespeicherten Chat, auch wenn er gerade nicht offen
     * ist, und verändert ihn nicht.
     */
    private void exportChat(String chatId, String title) {
        if (actions == null) {
            return;
        }
        ExportSaveDialog.Choice<ChatExportFormat> choice = ExportSaveDialog.show(this, "Chat exportieren",
                ChatExportFileName.of(title, System.currentTimeMillis(), ZoneId.systemDefault()),
                ChatExportFormat.fileTypes());
        if (choice == null) {
            return;
        }
        String problem = actions.exportChatRequested(chatId, choice.file(), choice.format());
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, "Export fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ Chat-Projekte (#143)

    static final String PROJECTS_HEADER = "PROJEKTE";
    static final String NEW_PROJECT_LABEL = "Neues Projekt …";
    static final String NO_PROJECT_LABEL = "Chats ohne Projekt";
    static final String MOVE_CHAT_LABEL = "Verschieben nach";
    static final String NEW_CHAT_IN_PROJECT_LABEL = "Neuer Chat in diesem Projekt";
    static final String PROJECT_CONTEXT_LABEL = "Projektanweisungen …";
    static final String NEW_SUBPROJECT_LABEL = "Unterprojekt anlegen …";
    static final String RENAME_PROJECT_LABEL = "Umbenennen …";
    static final String DELETE_PROJECT_LABEL = "Löschen …";
    static final String NO_PROJECT_DROP_TOOLTIP = "Hierher ziehen: Chat aus dem Projekt nehmen";
    private static final int PROJECT_INDENT_PX = 14;

    /** Project rows of the last refresh (tests and keyboard-free checks). */
    private final List<ChatHistoryRow> projectRows = new ArrayList<ChatHistoryRow>();
    /** Expanded projects, for this run. */
    private final Set<String> expandedProjects = new LinkedHashSet<String>();

    private final ChatProjectTransfer.Drops projectDrops = new ChatProjectTransfer.Drops() {
        @Override
        public boolean accepts(String payload, ChatProjectTransfer.Target target) {
            if (actions == null) {
                return false;
            }
            if (payload.startsWith(ChatProjectTransfer.CHAT)) {
                return !target.topLevel;
            }
            String projectId = payload.substring(ChatProjectTransfer.PROJECT.length());
            return target.projectId != null ? actions.canMoveProject(projectId, target.projectId)
                    : target.topLevel && actions.canMoveProject(projectId, null);
        }

        @Override
        public boolean drop(String payload, ChatProjectTransfer.Target target) {
            boolean done;
            if (payload.startsWith(ChatProjectTransfer.CHAT)) {
                done = moveChat(payload.substring(ChatProjectTransfer.CHAT.length()), target.projectId);
            } else {
                done = actions.moveProjectRequested(payload.substring(ChatProjectTransfer.PROJECT.length()),
                        target.projectId);
            }
            if (done && target.projectId != null) {
                expandedProjects.add(target.projectId);
            }
            SwingUtilities.invokeLater(ChatWorkspacePanel.this::refreshChatList); // not while the drop runs
            return done;
        }
    };

    /** The project rows of the last refresh, in display order. */
    List<ChatHistoryRow> projectRows() {
        return Collections.unmodifiableList(projectRows);
    }

    private Set<String> projectIds() {
        Set<String> ids = new LinkedHashSet<String>();
        if (actions != null) {
            for (ChatProjectItem project : actions.chatProjects()) {
                ids.add(project.id());
            }
        }
        return ids;
    }

    private String currentProjectName() {
        if (actions == null || actions.currentChatId() == null) {
            return null;
        }
        String projectId = actions.projectOfChat(actions.currentChatId());
        for (ChatProjectItem project : actions.chatProjects()) {
            if (project.id().equals(projectId)) {
                return project.name();
            }
        }
        return null;
    }

    /**
     * The section PROJEKTE: a header with "+" (new project), then the project tree, each project with its chats when
     * expanded (or, while the chat search filters, with its matching chats). Projects and chats can be dragged onto a
     * project; the header takes projects to the top level, the date groups take chats out of their project.
     */
    private void addProjects(String filter) {
        if (actions == null) {
            return;
        }
        List<ChatProjectItem> projects = actions.chatProjects();
        chatListPanel.add(projectsHeader());
        if (projects.isEmpty()) {
            return;
        }
        String currentId = actions.currentChatId();
        Map<String, List<SavedChatItem>> chatsByProject = new LinkedHashMap<String, List<SavedChatItem>>();
        for (SavedChatItem chat : actions.savedChats()) {
            String projectId = actions.projectOfChat(chat.id());
            if (projectId == null || chat.id().equals(currentId)
                    || !filter.isEmpty() && !chat.title().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            List<SavedChatItem> chats = chatsByProject.get(projectId);
            if (chats == null) {
                chats = new ArrayList<SavedChatItem>();
                chatsByProject.put(projectId, chats);
            }
            chats.add(chat);
        }
        Set<String> visible = visibleProjects(projects, chatsByProject, filter);
        Set<String> collapsed = new LinkedHashSet<String>();
        long startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (ChatProjectItem project : projects) {
            if (!visible.contains(project.id()) || project.parentId() != null && collapsed.contains(project.parentId())) {
                collapsed.add(project.id());
                continue;
            }
            boolean expanded = !filter.isEmpty() || expandedProjects.contains(project.id());
            if (!expanded) {
                collapsed.add(project.id());
            }
            List<SavedChatItem> chats = chatsByProject.containsKey(project.id())
                    ? chatsByProject.get(project.id()) : Collections.<SavedChatItem>emptyList();
            chatListPanel.add(projectRow(project, expanded, chats.size()));
            if (expanded) {
                for (SavedChatItem chat : chats) {
                    chatListPanel.add(savedChatRow(chat, startOfToday, project.depth() + 1));
                }
            }
        }
    }

    /** Without filter every project; with filter the matching ones, those with matching chats, and their parents. */
    private static Set<String> visibleProjects(List<ChatProjectItem> projects,
                                               Map<String, List<SavedChatItem>> chatsByProject, String filter) {
        Set<String> visible = new LinkedHashSet<String>();
        Map<String, ChatProjectItem> byId = new LinkedHashMap<String, ChatProjectItem>();
        for (ChatProjectItem project : projects) {
            byId.put(project.id(), project);
        }
        for (ChatProjectItem project : projects) {
            if (filter.isEmpty() || project.name().toLowerCase(Locale.ROOT).contains(filter)
                    || chatsByProject.containsKey(project.id())) {
                for (ChatProjectItem at = project; at != null && visible.add(at.id()); ) {
                    at = at.parentId() == null ? null : byId.get(at.parentId());
                }
            }
        }
        return visible;
    }

    private JComponent projectsHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        JLabel label = groupHeader(PROJECTS_HEADER);
        label.setTransferHandler(new ChatProjectTransfer(null, ChatProjectTransfer.Target.topLevel(), projectDrops));
        header.add(label, BorderLayout.CENTER);
        javax.swing.JButton add = new javax.swing.JButton("+");
        add.setToolTipText(NEW_PROJECT_LABEL);
        add.setFocusable(false);
        add.setBorderPainted(false);
        add.setContentAreaFilled(false);
        add.setFont(ResearchUiTypography.semiBold(13f));
        add.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        add.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        add.addActionListener(event -> createProject(null));
        header.add(add, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        return header;
    }

    private JComponent projectRow(final ChatProjectItem project, final boolean expanded, int chatCount) {
        String meta = "Projekt · " + (chatCount == 1 ? "1 Chat" : chatCount + " Chats");
        ChatHistoryRow row = new ChatHistoryRow((expanded ? "▾ " : "▸ ") + project.name(), meta, "", false, false,
                () -> {
                    if (!expandedProjects.remove(project.id())) {
                        expandedProjects.add(project.id());
                    }
                    refreshChatList();
                }, () -> projectMenu(project), palette);
        row.setAlignmentX(LEFT_ALIGNMENT);
        makeDraggable(row, ChatProjectTransfer.PROJECT + project.id(),
                ChatProjectTransfer.Target.project(project.id()));
        projectRows.add(row);
        return indented(row, project.depth());
    }

    private void makeDraggable(final ChatHistoryRow row, String payload, ChatProjectTransfer.Target target) {
        if (actions == null) {
            return;
        }
        final ChatProjectTransfer transfer = new ChatProjectTransfer(payload, target, projectDrops);
        row.setTransferHandler(transfer);
        row.setDragStart(event -> transfer.exportAsDrag(row, event, javax.swing.TransferHandler.MOVE));
    }

    private static JComponent indented(JComponent row, int depth) {
        if (depth <= 0) {
            return row;
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, depth * PROJECT_INDENT_PX, 0, 0));
        wrap.add(row, BorderLayout.CENTER);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return wrap;
    }

    /** "Verschieben nach": "Chats ohne Projekt" and every project; the current place is disabled. */
    private javax.swing.JMenu moveMenu(final String chatId, List<ChatProjectItem> projects) {
        javax.swing.JMenu move = new javax.swing.JMenu(MOVE_CHAT_LABEL);
        String current = actions.projectOfChat(chatId);
        JMenuItem none = new JMenuItem(NO_PROJECT_LABEL);
        none.setEnabled(current != null);
        none.addActionListener(event -> {
            moveChat(chatId, null);
            refreshChatList();
        });
        move.add(none);
        move.addSeparator();
        for (final ChatProjectItem project : projects) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < project.depth(); i++) {
                indent.append("   ");
            }
            JMenuItem item = new JMenuItem(indent + project.name());
            item.setEnabled(!project.id().equals(current));
            item.addActionListener(event -> {
                if (moveChat(chatId, project.id())) {
                    expandedProjects.add(project.id());
                }
                refreshChatList();
            });
            move.add(item);
        }
        return move;
    }

    private boolean moveChat(String chatId, String projectId) {
        String problem = actions.moveChatRequested(chatId, projectId);
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, "Chat nicht verschoben", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    JPopupMenu projectMenu(final ChatProjectItem project) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem newChat = new JMenuItem(NEW_CHAT_IN_PROJECT_LABEL);
        newChat.setEnabled(!chatShell.model().isStreaming());
        newChat.addActionListener(event -> {
            modes.select(ShellMode.CHAT);
            if (actions.newChatInProjectRequested(project.id())) {
                expandedProjects.add(project.id());
                showChatArea();
            }
            refreshChatList();
        });
        menu.add(newChat);
        JMenuItem context = new JMenuItem(PROJECT_CONTEXT_LABEL);
        context.addActionListener(event -> editProjectContext(project));
        menu.add(context);
        menu.addSeparator();
        JMenuItem sub = new JMenuItem(NEW_SUBPROJECT_LABEL);
        sub.addActionListener(event -> createProject(project.id()));
        menu.add(sub);
        JMenuItem rename = new JMenuItem(RENAME_PROJECT_LABEL);
        rename.addActionListener(event -> {
            String name = (String) JOptionPane.showInputDialog(this, "Neuer Name des Projekts:", "Projekt umbenennen",
                    JOptionPane.PLAIN_MESSAGE, null, null, project.name());
            if (name != null && !name.trim().isEmpty()) {
                actions.renameProjectRequested(project.id(), name.trim());
                refreshChatList();
            }
        });
        menu.add(rename);
        JMenuItem delete = new JMenuItem(DELETE_PROJECT_LABEL);
        delete.addActionListener(event -> deleteProject(project));
        menu.add(delete);
        return menu;
    }

    private void createProject(String parentId) {
        if (actions == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name des Projekts:",
                parentId == null ? "Neues Projekt" : "Neues Unterprojekt", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String id = actions.createProjectRequested(name.trim(), parentId);
        if (id != null && parentId != null) {
            expandedProjects.add(parentId);
        }
        refreshChatList();
    }

    /** Deleting never deletes chats: the dialog names how many move to "Chats ohne Projekt". */
    private void deleteProject(ChatProjectItem project) {
        int chats = actions.chatsInProject(project.id());
        String message = "Projekt \"" + project.name() + "\" mit allen Unterprojekten löschen?"
                + (chats == 0 ? "" : "\n" + (chats == 1 ? "Ein Chat kommt" : chats + " Chats kommen")
                + " nach „" + NO_PROJECT_LABEL + "“; gelöscht wird kein Chat.");
        if (JOptionPane.showConfirmDialog(this, message, "Projekt löschen", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            actions.deleteProjectRequested(project.id());
            expandedProjects.remove(project.id());
            refreshChatList();
        }
    }

    /** The project instructions (free text); they apply from the next question on. */
    private void editProjectContext(ChatProjectItem project) {
        javax.swing.JTextArea instructions = new javax.swing.JTextArea(project.instructions(), 10, 48);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        JPanel form = new JPanel(new BorderLayout(0, 4));
        form.add(new JLabel("Projektanweisungen (gelten für jede neue Frage in diesem Projekt):"), BorderLayout.NORTH);
        form.add(new JScrollPane(instructions), BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, form, "Projekt „" + project.name() + "“",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            actions.updateProjectInstructionsRequested(project.id(), instructions.getText());
        }
    }

    // ------------------------------------------------------------------ Drawer-Verhalten (arch)

    private void refreshRibbonTabs() {
        ribbon.setTabs(sidebar.tabTitles(), sidebar.tabIcons(), sidebar.activeTab());
    }

    private void openMenuAndSidebar() {
        showSidebar();
        ribbon.open();
    }

    private void collapseMenuAndSidebar() {
        sidebarCloseTimer.stop();
        boolean wasPinned = menuLocked;
        menuLocked = false;
        burger.setEmphasized(false);
        ribbon.close();
        hideSidebar();
        if (wasPinned) {
            persistBurgerPinned();
        }
    }

    private void showSidebar() {
        refreshChatList();
        sidebar.rebuildTabs();
        refreshRibbonTabs();
        sidebar.setVisible(true);
        sidebarSplit.openLeft();
        updateMouseWatcher();
        revalidate();
        repaint();
    }

    private void hideSidebar() {
        sidebar.setVisible(false);
        sidebarSplit.collapseLeft();
        updateMouseWatcher();
        revalidate();
        repaint();
    }

    /** Reiterleiste und Drawer falten nach dem Verlassen gemeinsam weg — außer sie sind eingerastet. */
    private void onPointerLeftSidebarArea() {
        if (!menuLocked) {
            ribbon.close();
            hideSidebar();
        }
        updateMouseWatcher();
    }

    private void updateMouseWatcher() {
        boolean needed = sidebar.isVisible() || ribbon.isOpen();
        if (needed) {
            installSidebarMouseWatcher();
        } else {
            removeSidebarMouseWatcher();
        }
    }

    /**
     * Solange Drawer oder Reiterleiste offen sind, verfolgt ein globaler Beobachter den Zeiger: innerhalb von
     * Hamburger ∪ Reiterleiste ∪ Drawer passiert nichts; verlässt er den Bereich, faltet nach kurzer Verzögerung
     * (die den Weg Hamburger → Drawer überbrückt) alles weg, was nicht eingerastet ist.
     */
    private void installSidebarMouseWatcher() {
        if (sidebarMouseWatcher != null) {
            return;
        }
        sidebarMouseWatcher = new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent && isShowing()) {
                    watchPointer((MouseEvent) event);
                }
            }
        };
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(sidebarMouseWatcher,
                    AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        } catch (SecurityException restricted) {
            sidebarMouseWatcher = null; // Schließen beim Verlassen entfällt; Klick und Rastung bleiben
        }
    }

    /**
     * Ein Zeigerereignis des Beobachters. Beim Verlassen des Fensters ist {@code MOUSE_EXITED} das letzte, was
     * AWT liefert; es zählt darum als „draußen“ und startet den Timer. Bleibt der Zeiger im Fenster, folgt sofort
     * die nächste Bewegung, die den Timer innerhalb des Bereichs wieder anhält.
     */
    void watchPointer(MouseEvent mouse) {
        if (menuLocked) {
            sidebarCloseTimer.stop();
            return;
        }
        int id = mouse.getID();
        if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_ENTERED && id != MouseEvent.MOUSE_DRAGGED
                && id != MouseEvent.MOUSE_EXITED) {
            return;
        }
        Point onScreen = new Point(mouse.getXOnScreen(), mouse.getYOnScreen());
        boolean inside = id != MouseEvent.MOUSE_EXITED
                && (screenBounds(burger, HOVER_MARGIN_PX).contains(onScreen)
                || (ribbon.isOpen() && screenBounds(ribbon, HOVER_MARGIN_PX).contains(onScreen))
                || (sidebar.isVisible() && screenBounds(sidebar, HOVER_MARGIN_PX).contains(onScreen)));
        if (inside) {
            sidebarCloseTimer.stop();
        } else if (!sidebarCloseTimer.isRunning()) {
            sidebarCloseTimer.restart();
        }
    }

    /** Ob der Beobachter das Wegfalten von Reiterleiste und Drawer eingeplant hat (für Tests). */
    boolean isSidebarCloseScheduled() {
        return sidebarCloseTimer.isRunning();
    }

    private void removeSidebarMouseWatcher() {
        if (sidebarMouseWatcher != null) {
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(sidebarMouseWatcher);
            } catch (SecurityException ignore) {
                // nichts zu tun
            }
            sidebarMouseWatcher = null;
        }
    }

    private static Rectangle screenBounds(Component component, int margin) {
        if (!component.isShowing()) {
            return new Rectangle(0, 0, 0, 0);
        }
        Point location = component.getLocationOnScreen();
        return new Rectangle(location.x - margin, location.y - margin,
                component.getWidth() + 2 * margin, component.getHeight() + 2 * margin);
    }
}
