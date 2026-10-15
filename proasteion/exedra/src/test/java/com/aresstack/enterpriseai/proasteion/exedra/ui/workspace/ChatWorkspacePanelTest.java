package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.knowledge.ReaderChatPages;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.ChatHistoryRow;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import com.aresstack.enterpriseai.ui.comic.control.ResearchIconButton;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.io.File;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Die Arbeitsfläche headless: Modus-Pille statt Reiterleiste, beide Ansichten behalten ihre eigenen Verläufe,
 * Hamburger und Drawer mit Chats- und Wissensquellen-Seite, „+ Neuer Chat“ und Zahnrad gehen an die
 * {@link WorkspaceActions}.
 */
public class ChatWorkspacePanelTest {

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();

    /** Beantwortet jede Nachricht sofort mit einer festen Antwort in seinem eigenen Model. */
    private static final class EchoActions implements ChatShellActions {
        final ChatShellModel model;
        final String answer;

        EchoActions(ChatShellModel model, String answer) {
            this.model = model;
            this.answer = answer;
        }

        @Override
        public void sendRequested(String text, boolean ragEnabled) {
            model.addUserMessage(text);
            model.beginAssistantMessage();
            model.appendAssistantDelta(answer);
            model.completeAssistantMessage();
        }

        @Override
        public void stopRequested() {
        }
    }

    private static final class RecordingActions implements WorkspaceActions {
        final List<ShellMode> newChats = new ArrayList<ShellMode>();
        int settings;

        boolean acceptNewChat = true;

        @Override
        public boolean newChatRequested(ShellMode mode) {
            newChats.add(mode);
            return acceptNewChat;
        }

        @Override
        public void settingsRequested() {
            settings++;
        }
    }

    @Test
    public void theModePillSwitchesTheVisibleShellAndKeepsTheTranscriptsApart() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellModel agentModel = new ChatShellModel(() -> 0L);
                EchoActions chat = new EchoActions(chatModel, "Chat-Antwort");
                EchoActions agent = new EchoActions(agentModel, "Agent-Antwort");
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, chat, comic, bubbles);
                ChatShellPanel agentShell = new ChatShellPanel(agentModel, agent, comic, bubbles);
                ShellModeModel modes = new ShellModeModel(true);
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, agentShell, comic);
                workspace.setSize(900, 620);
                workspace.doLayout();

                assertSame(chatShell, workspace.visibleShell());
                assertEquals(0, workspace.modePill().getSelectedIndex());
                chat.sendRequested("Frage an den Chat", false);

                workspace.modePill().select(1);
                assertEquals(ShellMode.AGENT, modes.getMode());
                assertSame(agentShell, workspace.visibleShell());
                assertEquals(1, workspace.modePill().getSelectedIndex());
                assertTrue("die Agent-Karte ist sichtbar", agentShell.isVisible());
                assertFalse("die Chat-Karte ist verdeckt", chatShell.isVisible());
                agent.sendRequested("Auftrag an den Agenten", false);

                workspace.modePill().select(0);
                assertSame(chatShell, workspace.visibleShell());
                assertTrue(chatShell.isVisible());

                assertEquals(2, chatModel.getEntries().size());
                assertEquals("Frage an den Chat", chatModel.getEntries().get(0).getText());
                assertEquals("Chat-Antwort", chatModel.getEntries().get(1).getText());
                assertEquals(2, agentModel.getEntries().size());
                assertEquals("Auftrag an den Agenten", agentModel.getEntries().get(0).getText());
                assertEquals("Agent-Antwort", agentModel.getEntries().get(1).getText());
                paint(workspace);
            }
        });
    }

    @Test
    public void withoutAgentThePillOffersOnlyTheChat() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"), comic,
                        bubbles);
                ShellModeModel modes = new ShellModeModel(false);
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, null, comic);

                workspace.modePill().select(1); // deaktivierter Eintrag: wirkungslos
                assertEquals(ShellMode.CHAT, modes.getMode());
                assertEquals(0, workspace.modePill().getSelectedIndex());
                assertSame(chatShell, workspace.visibleShell());
                assertNull(workspace.agentShell());
            }
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void agentShellNeedsTheAgentMode() throws Throwable {
        try {
            onEdt(new Runnable() {
                @Override
                public void run() {
                    ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                    ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"),
                            comic, bubbles);
                    // Ohne eigene Agent-Ansicht ist der Agent-Modus erlaubt (ein Chat, zwei Modi), umgekehrt nicht.
                    new ChatWorkspacePanel(new ShellModeModel(false), chatShell, chatShell, comic);
                }
            });
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void theDrawerStartsClosedAndTheBurgerLatchesItOpenWithBothPanes() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                workspace.setSize(900, 620);
                workspace.doLayout();
                assertFalse(workspace.isDrawerOpen());
                assertFalse(workspace.ribbon().isOpen());
                assertTrue(workspace.splitPane().isLeftCollapsed());

                workspace.burger().doClick();
                workspace.ribbon().finishAnimation();
                assertTrue(workspace.isDrawerOpen());
                assertTrue(workspace.isDrawerPinned());
                assertTrue(workspace.burger().isEmphasized());
                assertTrue(workspace.ribbon().isOpen());
                assertFalse(workspace.splitPane().isLeftCollapsed());
                assertEquals(Arrays.asList(ChatWorkspacePanel.CHATS_TAB, ChatWorkspacePanel.KNOWLEDGE_TAB,
                        ChatWorkspacePanel.FAVORITES_TAB, ChatWorkspacePanel.NAVIGATION_TAB),
                        workspace.sidebar().tabTitles());
                assertEquals(ChatWorkspacePanel.CHATS_TAB, workspace.sidebar().activeTab());
                workspace.doLayout();
                paint(workspace);

                workspace.burger().doClick();
                workspace.ribbon().finishAnimation();
                assertFalse(workspace.isDrawerOpen());
                assertFalse(workspace.isDrawerPinned());
                assertFalse(workspace.burger().isEmphasized());
                assertFalse(workspace.ribbon().isOpen());
            }
        });
    }

    @Test
    public void openAndCloseDrawerMirrorTheBurger() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                workspace.openDrawer();
                assertTrue(workspace.isDrawerOpen());
                assertTrue(workspace.isDrawerPinned());
                workspace.closeDrawer();
                assertFalse(workspace.isDrawerOpen());
                assertFalse(workspace.isDrawerPinned());
            }
        });
    }

    @Test
    public void leavingTheWindowSchedulesTheCloseOfAHoverOpenedDrawerButAPinnedOneStays() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"), comic,
                        bubbles);
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(new ShellModeModel(false), chatShell, null,
                        comic);
                workspace.setSize(900, 620);
                workspace.doLayout();

                MouseEvent hover = new MouseEvent(workspace.burger(), MouseEvent.MOUSE_ENTERED, 0L, 0, 4, 4, 0,
                        false);
                for (MouseListener listener : workspace.burger().getMouseListeners()) {
                    listener.mouseEntered(hover);
                }
                assertTrue("Überfahren öffnet den Drawer", workspace.isDrawerOpen());
                assertFalse(workspace.isDrawerPinned());
                assertFalse(workspace.isSidebarCloseScheduled());

                // Der Zeiger verlässt das Fenster: MOUSE_EXITED ist das letzte Ereignis, danach kommt nichts mehr.
                MouseEvent exit = new MouseEvent(workspace, MouseEvent.MOUSE_EXITED, 0L, 0, 0, 300, 0, false);
                workspace.watchPointer(exit);
                assertTrue("ohne weiteres Ereignis muss der Timer schließen", workspace.isSidebarCloseScheduled());

                workspace.openDrawer(); // rastet ein
                workspace.watchPointer(exit);
                assertFalse("eingerastet bleibt alles offen", workspace.isSidebarCloseScheduled());
                assertTrue(workspace.isDrawerOpen());
            }
        });
    }

    @Test
    public void newChatAndSettingsReachTheActionsAndNewChatPausesWhileStreaming() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellModel agentModel = new ChatShellModel(() -> 0L);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"), comic,
                        bubbles);
                ChatShellPanel agentShell = new ChatShellPanel(agentModel, new EchoActions(agentModel, "ok"), comic,
                        bubbles);
                ShellModeModel modes = new ShellModeModel(true);
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, agentShell, comic);
                RecordingActions actions = new RecordingActions();
                workspace.setActions(actions);
                workspace.openDrawer();

                assertTrue(workspace.newChatButton().isEnabled());
                workspace.newChatButton().doClick();
                assertEquals(Arrays.asList(ShellMode.CHAT), actions.newChats);
                assertTrue("eingerastet bleibt der Drawer offen", workspace.isDrawerOpen());

                workspace.modePill().select(1);
                workspace.newChatButton().doClick();
                assertEquals(Arrays.asList(ShellMode.CHAT, ShellMode.AGENT), actions.newChats);

                agentModel.addUserMessage("lange Frage");
                agentModel.beginAssistantMessage();
                assertFalse("während einer Antwort kein neuer Chat", workspace.newChatButton().isEnabled());
                agentModel.completeAssistantMessage();
                assertTrue(workspace.newChatButton().isEnabled());

                workspace.settingsButton().doClick();
                assertEquals(1, actions.settings);
            }
        });
    }

    @Test
    public void chatRowsShowTitleCountAndSelectionAndTheFilterNarrowsThem() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellModel agentModel = new ChatShellModel(() -> 0L);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"), comic,
                        bubbles);
                ChatShellPanel agentShell = new ChatShellPanel(agentModel, new EchoActions(agentModel, "ok"), comic,
                        bubbles);
                ShellModeModel modes = new ShellModeModel(true);
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, agentShell, comic);
                workspace.openDrawer();

                List<ChatHistoryRow> rows = workspace.chatRows();
                assertEquals(2, rows.size());
                assertEquals(ChatWorkspacePanel.NEW_CHAT_TITLE, rows.get(0).getTitle());
                assertTrue("die Chat-Zeile ist die aktive", rows.get(0).isSelected());
                assertEquals(ChatWorkspacePanel.AGENT_TITLE, rows.get(1).getTitle());
                assertFalse(rows.get(1).isSelected());

                chatModel.addUserMessage("Wie lange ist die Kündigungsfrist?");
                workspace.refreshChatList();
                rows = workspace.chatRows();
                assertEquals("Wie lange ist die Kündigungsfrist?", rows.get(0).getTitle());

                rows.get(1).open();
                assertEquals(ShellMode.AGENT, modes.getMode());
                rows = workspace.chatRows();
                assertFalse(rows.get(0).isSelected());
                assertTrue(rows.get(1).isSelected());

                workspace.chatFilter().setText("kündigung");
                workspace.refreshChatList();
                rows = workspace.chatRows();
                assertEquals(1, rows.size());
                assertEquals("Wie lange ist die Kündigungsfrist?", rows.get(0).getTitle());
            }
        });
    }

    @Test
    public void knowledgeSourcesAppearOnTheirOwnPane() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                final List<String> calls = new ArrayList<String>();
                workspace.setKnowledgeSourceActions(new KnowledgeSourceActions() {
                    @Override
                    public void enabledChanged(String sourceId, boolean enabled) {
                        calls.add("enabled " + sourceId + " " + enabled);
                    }

                    @Override
                    public void indexRequested(String sourceId) {
                        calls.add("index " + sourceId);
                    }

                    @Override
                    public void editRequested(String sourceId) {
                        calls.add("edit " + sourceId);
                    }

                    @Override
                    public void removeRequested(String sourceId) {
                        calls.add("remove " + sourceId);
                    }

                    @Override
                    public void addRequested() {
                        calls.add("add");
                    }

                    @Override
                    public boolean canAdd() {
                        return true;
                    }
                });
                workspace.setKnowledgeSources(Arrays.asList(
                        new KnowledgeSourceItem("handbuch", "MediaWiki", "Urlaub", true, "12 Seiten im Index",
                                KnowledgeSourceItem.State.IDLE, true, true),
                        new KnowledgeSourceItem("wiki", "Confluence", "IT", false, "Abgewählt",
                                KnowledgeSourceItem.State.IDLE, false, true)));
                KnowledgeSourcesPanel pane = workspace.knowledgeSources();
                assertEquals(2, pane.rows().size());
                assertTrue(pane.rows().get(0).checkBox().isSelected());
                assertFalse(pane.rows().get(1).checkBox().isSelected());
                assertFalse(pane.rows().get(1).indexButton().isEnabled());
                assertEquals("MediaWiki · Urlaub", pane.rows().get(0).item().description());
                pane.rows().get(0).checkBox().doClick();
                pane.rows().get(0).indexButton().doClick();
                pane.rows().get(1).editButton().doClick();
                pane.rows().get(1).removeButton().doClick();
                pane.addButton().doClick();
                assertEquals(Arrays.asList("enabled handbuch false", "index handbuch", "edit wiki", "remove wiki",
                        "add"), calls);
                workspace.openDrawer();
                workspace.ribbon().finishAnimation();
                workspace.sidebar().showTab(ChatWorkspacePanel.KNOWLEDGE_TAB);
                assertEquals(ChatWorkspacePanel.KNOWLEDGE_TAB, workspace.sidebar().activeTab());
                assertSame("„+ Quelle“ steht links im Fuß", pane.addButton(),
                        workspace.sidebar().footerLeadingComponent());
                assertFalse("nicht mehr über der Liste", SwingUtilities.isDescendingFrom(pane.addButton(), pane));
                assertEquals(workspace.newChatButton().getPreferredSize(), pane.addButton().getPreferredSize());
                workspace.sidebar().showTab(ChatWorkspacePanel.FAVORITES_TAB);
                assertNull("Favoriten tragen nichts bei", workspace.sidebar().footerLeadingComponent());
                workspace.sidebar().showTab(ChatWorkspacePanel.CHATS_TAB);
                assertSame(workspace.newChatButton(), workspace.sidebar().footerLeadingComponent());
                workspace.sidebar().showTab(ChatWorkspacePanel.KNOWLEDGE_TAB);
                workspace.setSize(900, 620);
                workspace.doLayout();
                paint(workspace);
            }
        });
    }

    @Test
    public void theDrawerHasOneSettingsGearThatStaysVisibleOnEveryTab() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                RecordingActions actions = new RecordingActions();
                workspace.setActions(actions);
                workspace.openDrawer();
                workspace.ribbon().finishAnimation();
                workspace.setSize(900, 620);
                ResearchIconButton gear = workspace.settingsButton();
                assertEquals("genau ein Zahnrad im Drawer", Collections.singletonList(gear),
                        settingsGears(workspace.sidebar()));
                for (String tab : Arrays.asList(ChatWorkspacePanel.CHATS_TAB, ChatWorkspacePanel.KNOWLEDGE_TAB,
                        ChatWorkspacePanel.FAVORITES_TAB)) {
                    workspace.sidebar().showTab(tab);
                    workspace.validate();
                    assertTrue("Zahnrad sichtbar auf " + tab, visibleUpTo(gear, workspace.sidebar()));
                    assertEquals(Collections.singletonList(gear), settingsGears(workspace.sidebar()));
                }
                gear.doClick();
                assertEquals(1, actions.settings);
            }
        });
    }

    @Test
    public void theKnowledgePaneHasNoIndexSection() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                KnowledgeSourcesPanel pane = new KnowledgeSourcesPanel(comic);
                List<String> texts = new ArrayList<String>();
                collectTexts(pane, texts);
                for (String text : texts) {
                    assertFalse(text, text.contains("Index …") || text.equals("INDEX")
                            || text.contains("beim Start"));
                }
                assertTrue(texts.toString(), texts.contains(KnowledgeSourcesPanel.HINT_TEXT));
            }
        });
    }

    private static List<ResearchIconButton> settingsGears(Container root) {
        List<ResearchIconButton> gears = new ArrayList<ResearchIconButton>();
        for (Component child : root.getComponents()) {
            if (child instanceof ResearchIconButton
                    && ChatWorkspacePanel.SETTINGS_TOOLTIP.equals(((ResearchIconButton) child).getToolTipText())) {
                gears.add((ResearchIconButton) child);
            }
            if (child instanceof Container) {
                gears.addAll(settingsGears((Container) child));
            }
        }
        return gears;
    }

    private static boolean visibleUpTo(Component component, Component root) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (!c.isVisible()) {
                return false;
            }
            if (c == root) {
                return true;
            }
        }
        return false;
    }

    private static void collectTexts(Container root, List<String> texts) {
        for (Component child : root.getComponents()) {
            String text = child instanceof JLabel ? ((JLabel) child).getText()
                    : child instanceof AbstractButton ? ((AbstractButton) child).getText() : null;
            if (text != null) {
                texts.add(text);
            }
            if (child instanceof Container) {
                collectTexts((Container) child, texts);
            }
        }
    }

    @Test
    public void chatMenuOffersRenameExportAndDeleteInThisOrder() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                workspace.setActions(new WorkspaceActions() {
                    @Override
                    public boolean newChatRequested(ShellMode mode) {
                        return true;
                    }

                    @Override
                    public void settingsRequested() {
                    }

                    @Override
                    public boolean canExportChats() {
                        return true;
                    }
                });
                javax.swing.JPopupMenu menu = workspace.chatMenu("chat-1", "Was ist IAR?");
                List<String> labels = new ArrayList<String>();
                for (Component item : menu.getComponents()) {
                    labels.add(((javax.swing.JMenuItem) item).getText());
                }
                assertEquals(Arrays.asList("Umbenennen …", "Chat exportieren …", "Chat löschen"), labels);
                assertTrue(menu.getComponent(1).isEnabled());
            }
        });
    }

    /** #143: projects list their chats below them; a chat in a project is not repeated in the date groups. */
    private static final class ProjectActions implements WorkspaceActions {
        final java.util.Map<String, String> assignment = new java.util.HashMap<String, String>();
        final List<ChatProjectItem> projects = Arrays.asList(
                new ChatProjectItem("p-a", "Vertrieb", null, 0, "", Collections.<String>emptyList()),
                new ChatProjectItem("p-a1", "Nord", "p-a", 1, "", Collections.<String>emptyList()));
        boolean streaming;

        @Override
        public boolean newChatRequested(ShellMode mode) {
            return true;
        }

        @Override
        public void settingsRequested() {
        }

        @Override
        public List<SavedChatItem> savedChats() {
            long now = System.currentTimeMillis();
            return Arrays.asList(new SavedChatItem("c-1", "Angebot Nordwind", now, 2, 0),
                    new SavedChatItem("c-2", "Urlaubsantrag", now, 2, 0));
        }

        @Override
        public List<ChatProjectItem> chatProjects() {
            return projects;
        }

        @Override
        public String projectOfChat(String chatId) {
            return assignment.get(chatId);
        }

        @Override
        public String moveChatRequested(String chatId, String projectId) {
            if (streaming) {
                return "läuft";
            }
            if (projectId == null) {
                assignment.remove(chatId);
            } else {
                assignment.put(chatId, projectId);
            }
            return null;
        }

        @Override
        public boolean canMoveProject(String projectId, String parentId) {
            return !"p-a".equals(projectId) || parentId == null;
        }
    }

    @Test
    public void projectsShowTheirChatsAndChatsMoveBetweenProjects() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                ProjectActions actions = new ProjectActions();
                actions.assignment.put("c-1", "p-a");
                workspace.setActions(actions);
                workspace.openDrawer();
                workspace.refreshChatList();

                assertEquals("collapsed: only the top-level project", 1, workspace.projectRows().size());
                assertTrue(workspace.projectRows().get(0).getTitle().endsWith("Vertrieb"));
                assertFalse("a chat in a project is not in the date groups", titles(workspace).contains("Angebot Nordwind"));
                assertTrue(titles(workspace).contains("Urlaubsantrag"));

                workspace.projectRows().get(0).open(); // expands
                assertEquals(2, workspace.projectRows().size());
                assertTrue(titles(workspace).contains("Angebot Nordwind"));

                javax.swing.JPopupMenu menu = workspace.chatMenu("c-2", "Urlaubsantrag");
                javax.swing.JMenu move = (javax.swing.JMenu) menu.getComponent(1);
                assertEquals(ChatWorkspacePanel.MOVE_CHAT_LABEL, move.getText());
                assertFalse("already without project", move.getItem(0).isEnabled());
                move.getItem(3).doClick(); // "Nord"
                assertEquals("p-a1", actions.assignment.get("c-2"));

                workspace.chatFilter().setText("urlaub");
                workspace.refreshChatList();
                assertEquals("the search shows the project path to a matching chat", 2, workspace.projectRows().size());
                assertEquals(Collections.singletonList("Urlaubsantrag"), titles(workspace));
            }
        });
    }

    @Test
    public void projectMenuOffersNewChatContextAndTreeActions() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                ProjectActions actions = new ProjectActions();
                workspace.setActions(actions);
                List<String> labels = new ArrayList<String>();
                for (Component item : workspace.projectMenu(actions.projects.get(0)).getComponents()) {
                    if (item instanceof javax.swing.JMenuItem) {
                        labels.add(((javax.swing.JMenuItem) item).getText());
                    }
                }
                assertEquals(Arrays.asList(ChatWorkspacePanel.NEW_CHAT_IN_PROJECT_LABEL,
                        ChatWorkspacePanel.PROJECT_CONTEXT_LABEL, ChatWorkspacePanel.NEW_SUBPROJECT_LABEL,
                        ChatWorkspacePanel.RENAME_PROJECT_LABEL, ChatWorkspacePanel.DELETE_PROJECT_LABEL), labels);
            }
        });
    }

    private static List<String> titles(ChatWorkspacePanel workspace) {
        List<String> titles = new ArrayList<String>();
        for (com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.ChatHistoryRow row : workspace.chatRows()) {
            if (!row.isSelected()) {
                titles.add(row.getTitle());
            }
        }
        return titles;
    }

    @Test
    public void exportFileNameFollowsTitleAndTime() {
        long millis = java.time.LocalDateTime.of(2026, 10, 12, 3, 43).atZone(java.time.ZoneId.of("UTC"))
                .toInstant().toEpochMilli();
        // vorgeschlagen wird nur der Basisname, ohne Endung
        assertEquals("Was_ist_IAR_2026-10-12_0343",
                ChatExportFileName.of("Was ist IAR?", millis, java.time.ZoneId.of("UTC")));
        assertEquals("Chat_2026-10-12_0343", ChatExportFileName.of("  ", millis, java.time.ZoneId.of("UTC")));
    }

    @Test
    public void chosenFileTypeAloneDecidesTheExtension() {
        File folder = new File("export");
        File base = new File(folder, "Chat_2026-10-12_1551");
        assertEquals(new File(folder, "Chat_2026-10-12_1551.docx"),
                ChatExportFileName.withExtension(base, ChatExportFormat.DOCX));
        assertEquals(new File(folder, "Chat_2026-10-12_1551.md"),
                ChatExportFileName.withExtension(base, ChatExportFormat.MARKDOWN));
        assertEquals(new File(folder, "Chat_2026-10-12_1551.json"),
                ChatExportFileName.withExtension(base, ChatExportFormat.JSON));
        // eine andere Export-Endung wird ersetzt, auch in anderer Schreibweise
        assertEquals(new File(folder, "foo.docx"),
                ChatExportFileName.withExtension(new File(folder, "foo.json"), ChatExportFormat.DOCX));
        assertEquals(new File(folder, "foo.json"),
                ChatExportFileName.withExtension(new File(folder, "foo.DOCX"), ChatExportFormat.JSON));
        assertEquals(new File(folder, "foo.md"),
                ChatExportFileName.withExtension(new File(folder, "foo.md"), ChatExportFormat.MARKDOWN));
        // eine fremde Endung bleibt Teil des Namens, die gewählte kommt dazu
        assertEquals(new File(folder, "notiz.txt.docx"),
                ChatExportFileName.withExtension(new File(folder, "notiz.txt"), ChatExportFormat.DOCX));
        assertEquals(new File(folder, "Bericht v1.2.md"),
                ChatExportFileName.withExtension(new File(folder, "Bericht v1.2"), ChatExportFormat.MARKDOWN));
        // #132: PDF ist dabei und im Dialog voreingestellt, JSON bleibt
        assertEquals(new File(folder, "foo.pdf"),
                ChatExportFileName.withExtension(new File(folder, "foo.Docx"), ChatExportFormat.PDF));
        assertEquals(ChatExportFormat.PDF, ChatExportFormat.fileTypes().preselected().format());
        assertEquals(Arrays.asList(ChatExportFormat.PDF, ChatExportFormat.DOCX, ChatExportFormat.MARKDOWN,
                ChatExportFormat.JSON), Arrays.asList(ChatExportFormat.values()));
    }

    private ChatWorkspacePanel chatOnlyWorkspace() {
        ChatShellModel chatModel = new ChatShellModel(() -> 0L);
        ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"), comic, bubbles);
        return new ChatWorkspacePanel(new ShellModeModel(false), chatShell, null, comic);
    }

    private static void paint(ChatWorkspacePanel workspace) {
        BufferedImage image = new BufferedImage(workspace.getWidth(), workspace.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            workspace.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        SwingUtilities.invokeAndWait(runnable);
    }

    @Test
    public void chatSourceMarksTheKnowledgeTabWhileAFavoriteKeepsItsOwn() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                assertEquals(ChatWorkspacePanel.CHATS_TAB, workspace.sidebar().activeTab());

                workspace.showKnowledgeArea(); // a source clicked in a chat answer
                assertEquals(ChatWorkspacePanel.KNOWLEDGE_TAB, workspace.sidebar().activeTab());
                assertTrue(workspace.isReaderShowing());
                assertFalse(workspace.isDrawerOpen()); // the drawer stays as it was

                workspace.showChatArea();
                workspace.selectWorkspaceTab(ChatWorkspacePanel.FAVORITES_TAB);
                workspace.showReader(); // a favorite opened from its tree
                assertEquals(ChatWorkspacePanel.FAVORITES_TAB, workspace.sidebar().activeTab());
                assertTrue(workspace.isReaderShowing());
            }
        });
    }

    /** Assert that drawer, ribbon and central card describe the same workspace target. */
    private static void assertTarget(ChatWorkspacePanel workspace, String tab, boolean reader) {
        assertEquals(tab, workspace.sidebar().activeTab());
        assertEquals(tab, workspace.ribbon().activeTitle());
        assertEquals(reader, workspace.isReaderShowing());
    }

    @Test
    public void addingAReaderPageToTheChatLeavesEveryReaderTabForChats() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                for (String readerTab : Arrays.asList(ChatWorkspacePanel.NAVIGATION_TAB,
                        ChatWorkspacePanel.FAVORITES_TAB, ChatWorkspacePanel.KNOWLEDGE_TAB)) {
                    ChatWorkspacePanel workspace = chatOnlyWorkspace();
                    workspace.selectWorkspaceTab(readerTab);
                    workspace.showReader(); // the page opened from that tab
                    assertTarget(workspace, readerTab, true);

                    ReaderChatPages pages = new ReaderChatPages(workspace.chatShell().composer(), workspace);
                    assertTrue(pages.add(new ReaderLink("confluence:https://cf.intern/confluence",
                            "confluence:cf.intern/confluence/page/4242001", "Betriebshandbuch", "", "")));

                    assertTarget(workspace, ChatWorkspacePanel.CHATS_TAB, false);
                    assertEquals(1, workspace.chatShell().composer().pages().size());
                    assertFalse(workspace.isDrawerOpen()); // the drawer stays as it was
                }
            }
        });
    }

    @Test
    public void everyTabSelectionShowsTheCardThatBelongsToIt() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                for (String tab : workspace.sidebar().tabTitles()) {
                    workspace.selectWorkspaceTab(tab);
                    assertEquals(tab, workspace.sidebar().activeTab());
                    assertEquals(tab, workspace.ribbon().activeTitle());
                    if (ChatWorkspacePanel.CHATS_TAB.equals(tab)) {
                        assertFalse(workspace.isReaderShowing());
                    } else if (ChatWorkspacePanel.isReaderTab(tab)) {
                        assertTrue(workspace.isReaderShowing());
                    }
                }
                // a reader opened while "Chats" is marked marks the knowledge area
                workspace.selectWorkspaceTab(ChatWorkspacePanel.CHATS_TAB);
                workspace.showReader();
                assertTarget(workspace, ChatWorkspacePanel.KNOWLEDGE_TAB, true);
                workspace.selectWorkspaceTab(ChatWorkspacePanel.NAVIGATION_TAB);
                workspace.showReader();
                assertTarget(workspace, ChatWorkspacePanel.NAVIGATION_TAB, true);
            }
        });
    }

    @Test
    public void switchingTheModeFromTheReaderMarksChats() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellModel agentModel = new ChatShellModel(() -> 0L);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "a"), comic, bubbles);
                ChatShellPanel agentShell = new ChatShellPanel(agentModel, new EchoActions(agentModel, "b"), comic,
                        bubbles);
                ShellModeModel modes = new ShellModeModel(true);
                ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, agentShell, comic);
                workspace.selectWorkspaceTab(ChatWorkspacePanel.FAVORITES_TAB);
                assertTarget(workspace, ChatWorkspacePanel.FAVORITES_TAB, true);

                assertTrue(modes.select(ShellMode.AGENT));
                assertTarget(workspace, ChatWorkspacePanel.CHATS_TAB, false);
                assertSame(agentShell, workspace.visibleShell());
            }
        });
    }

    @Test
    public void aNewChatWithTheDocumentStartsOneChatAndOnlyThenTakesThePage() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatWorkspacePanel workspace = chatOnlyWorkspace();
                RecordingActions actions = new RecordingActions();
                workspace.setActions(actions);
                ReaderChatPages pages = new ReaderChatPages(workspace.chatShell().composer(), workspace);
                ReaderLink link = new ReaderLink("confluence:https://cf.intern/confluence",
                        "confluence:cf.intern/confluence/page/4242001", "Betriebshandbuch", "", "");

                javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
                pages.contribute(menu, link);
                assertEquals(2, menu.getComponentCount());
                assertEquals("Zum aktuellen Chat hinzufügen", ((javax.swing.JMenuItem) menu.getComponent(0)).getText());
                assertEquals("Neuen Chat mit Dokument starten",
                        ((javax.swing.JMenuItem) menu.getComponent(1)).getText());

                actions.acceptNewChat = false; // e.g. the agent is busy
                workspace.selectWorkspaceTab(ChatWorkspacePanel.NAVIGATION_TAB);
                assertFalse(pages.startNewChatWith(link));
                assertTrue("nothing lands in the old chat", workspace.chatShell().composer().pages().isEmpty());
                assertTarget(workspace, ChatWorkspacePanel.NAVIGATION_TAB, true);

                actions.acceptNewChat = true;
                assertTrue(pages.startNewChatWith(link));
                assertEquals(2, actions.newChats.size());
                assertEquals(1, workspace.chatShell().composer().pages().size());
                assertEquals("Betriebshandbuch", workspace.chatShell().composer().pages().get(0).title());
                assertTrue("nothing is sent", workspace.chatShell().model().getEntries().isEmpty());
                assertTarget(workspace, ChatWorkspacePanel.CHATS_TAB, false);
            }
        });
    }
}
