package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.app.ui.sidebar.ChatHistoryRow;
import com.aresstack.enterpriseai.app.ui.sidebar.ChatSidebarPanel;
import com.aresstack.enterpriseai.app.ui.sidebar.ChatSidebarTab;
import com.aresstack.enterpriseai.app.ui.sidebar.SidebarTabRibbon;
import com.aresstack.enterpriseai.ui.comic.control.ComicOverlayPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComicSearchBar;
import com.aresstack.enterpriseai.ui.comic.control.ComicSplitPane;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.control.ResearchIconButton;
import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.control.ResearchPillDropdown;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
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
import java.util.List;
import java.util.Locale;

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
 * „+ Neuer Chat“, der Chat-Suche, den Chat-Zeilen (laufender Chat, Agent, gespeicherte Chats) und dem Zahnrad für die Einstellungen im Fuß, sowie die
 * Seite „Wissensquellen“ ({@link KnowledgeSourcesPanel}: hinzufügen, bearbeiten, an- und abwählen, indexieren). Beide Ansichten sind vollständige, voneinander unabhängige {@link ChatShellPanel}s
 * mit eigenem Model: eine im Hintergrund laufende Agent-Antwort schreibt nicht in den Chat.
 */
public final class ChatWorkspacePanel extends JPanel implements ShellModeModel.Listener {

    public static final String CHATS_TAB = "Chats";
    public static final String KNOWLEDGE_TAB = "Wissensquellen";
    static final String NEW_CHAT_LABEL = "+ Neuer Chat";
    static final String SETTINGS_TOOLTIP = "Einstellungen";
    static final String NEW_CHAT_TITLE = "Neuer Chat";
    static final String AGENT_TITLE = "Agent";

    static final int SIDEBAR_MIN_WIDTH = 240;
    static final int SIDEBAR_MAX_WIDTH = 560;
    static final int SIDEBAR_DEFAULT_WIDTH = 360; // wie askai arch
    private static final int HOVER_MARGIN_PX = 12;
    private static final int SIDEBAR_CLOSE_DELAY_MS = 300;
    private static final int LIST_REFRESH_DELAY_MS = 150;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY);
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    static final String DELETE_CHAT_LABEL = "Chat löschen";
    static final String RENAME_CHAT_LABEL = "Umbenennen …";

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
    private final List<ChatHistoryRow> chatRows = new ArrayList<ChatHistoryRow>();

    private final Timer sidebarCloseTimer;
    private final Timer listRefreshTimer;
    private AWTEventListener sidebarMouseWatcher;
    private boolean menuLocked;
    private WorkspaceActions actions;

    /** @param agentShell die Agent-Ansicht oder {@code null}, wenn kein Agent konfiguriert ist */
    public ChatWorkspacePanel(ShellModeModel modes, ChatShellPanel chatShell, ChatShellPanel agentShell,
                              ComicPalette palette) {
        super(new BorderLayout());
        if (modes == null || chatShell == null || palette == null) {
            throw new IllegalArgumentException("modes, chatShell and palette must not be null");
        }
        if (modes.isAgentAvailable() != (agentShell != null)) {
            throw new IllegalArgumentException("agentShell must be given exactly when the agent mode is available");
        }
        this.modes = modes;
        this.chatShell = chatShell;
        this.agentShell = agentShell;
        this.palette = palette;
        this.burger = ComposerButton.sidebarToggle("Chats und Wissensquellen");
        this.modePill = buildModePill();
        this.chatFilter = new ComicSearchBar("Chats durchsuchen…", "Chats nach Titel filtern", palette);
        this.newChatButton = new ResearchPillButton(NEW_CHAT_LABEL, ResearchUiMetrics.NEW_CHAT_HEIGHT,
                ResearchUiMetrics.RADIUS_CONTROL, ResearchUiMetrics.NEW_CHAT_PADDING_H);
        this.settingsButton = new ResearchIconButton(ComposerIcons.gear(), SETTINGS_TOOLTIP);
        this.knowledgePane = new KnowledgeSourcesPanel(palette);
        this.sidebar = new ChatSidebarPanel(CHATS_TAB, buildChatsTab(), palette);
        this.sidebarSplit = new ComicSplitPane(sidebar, deck, SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH, palette);
        this.sidebarCloseTimer = new Timer(SIDEBAR_CLOSE_DELAY_MS, event -> onPointerLeftSidebarArea());
        sidebarCloseTimer.setRepeats(false);
        this.listRefreshTimer = new Timer(LIST_REFRESH_DELAY_MS, event -> refreshChatList());
        listRefreshTimer.setRepeats(false);

        setBackground(palette.getSurface());
        deck.setBackground(palette.getSurface());
        deck.add(chatShell, ShellMode.CHAT.name());
        if (agentShell != null) {
            deck.add(agentShell, ShellMode.AGENT.name());
        }
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

    @Override
    public void modeChanged(ShellMode mode) {
        cards.show(deck, mode.name());
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
            sidebar.showTab(title);
            refreshRibbonTabs();
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
        sidebar.setExtraTabsSupplier(() -> Collections.<ChatSidebarTab>singletonList(new ChatSidebarTab() {
            @Override
            public String getTitle() {
                return KNOWLEDGE_TAB;
            }

            @Override
            public JComponent getComponent() {
                return knowledgePane;
            }
        }));
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

        newChatButton.setFont(ResearchUiTypography.semiBold(13f));
        newChatButton.setToolTipText("Neuen Chat beginnen");
        newChatButton.addActionListener(event -> {
            if (actions != null) {
                actions.newChatRequested(modes.getMode());
            }
            if (menuLocked) {
                refreshChatList();
            } else {
                collapseMenuAndSidebar();
            }
        });
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        north.setOpaque(false);
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        north.add(newChatButton);

        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);
        tab.add(north, BorderLayout.NORTH);
        tab.add(scroll, BorderLayout.CENTER);
        tab.add(buildChatsFooter(), BorderLayout.SOUTH);
        return tab;
    }

    /** Der Fuß der Chats-Seite: links Platz für Beiträge, rechts das Zahnrad für die Einstellungen. */
    private JComponent buildChatsFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(
                ResearchUiMetrics.FOOTER_PADDING_V, ResearchUiMetrics.FOOTER_PADDING_H,
                ResearchUiMetrics.FOOTER_PADDING_V, ResearchUiMetrics.FOOTER_PADDING_H));
        footer.setPreferredSize(new Dimension(10, ResearchUiMetrics.FOOTER_HEIGHT));
        settingsButton.addActionListener(event -> {
            if (actions != null) {
                actions.settingsRequested();
            }
        });
        JPanel gearWrap = new JPanel(new GridBagLayout());
        gearWrap.setOpaque(false);
        gearWrap.add(settingsButton);
        footer.add(gearWrap, BorderLayout.EAST);
        return footer;
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
        newChatButton.setEnabled(!visibleShell().model().isStreaming());
        String filter = chatFilter.getText().trim().toLowerCase(Locale.ROOT);
        chatListPanel.add(groupHeader("AKTIV"));
        addChatRow(ShellMode.CHAT, chatShell.model(), NEW_CHAT_TITLE, "Chat", filter);
        if (agentShell != null) {
            addChatRow(ShellMode.AGENT, agentShell.model(), AGENT_TITLE, "Agent", filter);
        }
        addSavedChats(filter);
        if (chatRows.isEmpty() && renamingChatId == null) {
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
            if (entry.getAuthor() != TranscriptEntry.Author.NOTICE) {
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
        String time = lastMillis == 0L ? "" : TIME.format(Instant.ofEpochMilli(lastMillis).atZone(ZoneId.systemDefault()));
        final String currentId = actions == null ? null : actions.currentChatId();
        ChatHistoryRow.MenuSupplier menu = null;
        if (mode == ShellMode.CHAT && currentId != null && count > 0) {
            final String rowTitle = title;
            menu = () -> deleteMenu(currentId, rowTitle);
        }
        if (menu != null && currentId.equals(renamingChatId)) {
            chatListPanel.add(renameRow(currentId, title));
            return;
        }
        ChatHistoryRow row = new ChatHistoryRow(title, meta, time, model.isStreaming(), modes.getMode() == mode,
                () -> modes.select(mode), menu, palette);
        row.setAlignmentX(LEFT_ALIGNMENT);
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
        for (SavedChatItem chat : actions.savedChats()) {
            if (chat.id().equals(currentId)
                    || !filter.isEmpty() && !chat.title().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
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
        chatListPanel.add(groupHeader(title));
        for (final SavedChatItem chat : group) {
            if (chat.id().equals(renamingChatId)) {
                chatListPanel.add(renameRow(chat.id(), chat.title()));
                continue;
            }
            ZonedDateTime at = Instant.ofEpochMilli(chat.modifiedAtMillis()).atZone(ZoneId.systemDefault());
            String time = chat.modifiedAtMillis() >= startOfToday ? TIME.format(at) : DAY.format(at);
            String meta = "Chat · " + (chat.messageCount() == 1 ? "1 Nachricht" : chat.messageCount() + " Nachrichten")
                    + (chat.attachmentCount() == 0 ? ""
                    : chat.attachmentCount() == 1 ? " · 1 Anhang" : " · " + chat.attachmentCount() + " Anhänge");
            ChatHistoryRow row = new ChatHistoryRow(chat.title(), meta, time, false, false,
                    () -> openSavedChat(chat.id()), () -> deleteMenu(chat.id(), chat.title()), palette);
            row.setAlignmentX(LEFT_ALIGNMENT);
            chatRows.add(row);
            chatListPanel.add(row);
        }
    }

    private void openSavedChat(String chatId) {
        if (actions == null || chatShell.model().isStreaming()) {
            return;
        }
        modes.select(ShellMode.CHAT);
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

    /** Das Menü einer Chat-Zeile: Umbenennen und Löschen mit Rückfrage (Nachrichten und Anhänge gehen verloren). */
    private JPopupMenu deleteMenu(final String chatId, final String title) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem(RENAME_CHAT_LABEL);
        rename.addActionListener(event -> {
            renamingChatId = chatId; // die Zeile verwandelt sich in ein Eingabefeld (arch), kein Dialog
            refreshChatList();
        });
        menu.add(rename);
        JMenuItem delete = new JMenuItem(DELETE_CHAT_LABEL);
        delete.setEnabled(!chatShell.model().isStreaming());
        delete.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Chat „" + title + "“ mit allen Nachrichten und Anhängen löschen?", DELETE_CHAT_LABEL,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.OK_OPTION && actions != null) {
                actions.deleteSavedChatRequested(chatId);
                refreshChatList();
            }
        });
        menu.add(delete);
        return menu;
    }

    // ------------------------------------------------------------------ Drawer-Verhalten (arch)

    /** Zeigt einen Drawer-Reiter samt Markierung in der Reiterleiste (wie ein Klick auf den Reiter). */
    public void showSidebarTab(String title) {
        sidebar.showTab(title);
        refreshRibbonTabs();
    }

    private void refreshRibbonTabs() {
        ribbon.setTabs(sidebar.tabTitles(), sidebar.activeTab());
    }

    private void openMenuAndSidebar() {
        showSidebar();
        ribbon.open();
    }

    private void collapseMenuAndSidebar() {
        sidebarCloseTimer.stop();
        menuLocked = false;
        burger.setEmphasized(false);
        ribbon.close();
        hideSidebar();
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
