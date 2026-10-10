package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.sidebar.ChatHistoryRow;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
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

        @Override
        public void newChatRequested(ShellMode mode) {
            newChats.add(mode);
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
    public void agentShellMustMatchAvailability() throws Throwable {
        try {
            onEdt(new Runnable() {
                @Override
                public void run() {
                    ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                    ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"),
                            comic, bubbles);
                    new ChatWorkspacePanel(new ShellModeModel(true), chatShell, null, comic);
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
                assertEquals(Arrays.asList(ChatWorkspacePanel.CHATS_TAB, ChatWorkspacePanel.KNOWLEDGE_TAB),
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
                workspace.setSize(900, 620);
                workspace.doLayout();
                paint(workspace);
            }
        });
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
}
