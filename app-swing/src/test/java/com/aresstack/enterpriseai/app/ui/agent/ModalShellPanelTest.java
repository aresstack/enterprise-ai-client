package com.aresstack.enterpriseai.app.ui.agent;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** AP21: Modus-Umschaltung headless; beide Modi behalten ihre eigenen Verläufe. */
public class ModalShellPanelTest {

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();

    /** Beantwortet jede Nachricht sofort mit einer festen Antwort in seinem eigenen Model. */
    private static final class EchoActions implements ChatShellActions {
        final ChatShellModel model;
        final String answer;
        final List<String> sent = new ArrayList<String>();

        EchoActions(ChatShellModel model, String answer) {
            this.model = model;
            this.answer = answer;
        }

        @Override
        public void sendRequested(String text, boolean ragEnabled) {
            sent.add(text);
            model.addUserMessage(text);
            model.beginAssistantMessage();
            model.appendAssistantDelta(answer);
            model.completeAssistantMessage();
        }

        @Override
        public void stopRequested() {
        }
    }

    @Test
    public void modelStartsInChatAndOffersAgentOnlyWhenConfigured() {
        ShellModeModel withoutAgent = new ShellModeModel(false);
        assertEquals(ShellMode.CHAT, withoutAgent.getMode());
        assertFalse(withoutAgent.select(ShellMode.AGENT));
        assertEquals(ShellMode.CHAT, withoutAgent.getMode());

        ShellModeModel withAgent = new ShellModeModel(true);
        final List<ShellMode> seen = new ArrayList<ShellMode>();
        withAgent.addListener(new ShellModeModel.Listener() {
            @Override
            public void modeChanged(ShellMode mode) {
                seen.add(mode);
            }
        });
        assertTrue(withAgent.select(ShellMode.AGENT));
        assertTrue(withAgent.select(ShellMode.AGENT)); // kein zweites Ereignis
        assertTrue(withAgent.select(ShellMode.CHAT));
        assertEquals(java.util.Arrays.asList(ShellMode.AGENT, ShellMode.CHAT), seen);
    }

    @Test
    public void switchingShowsOneShellAndKeepsTheTranscriptsApart() throws Exception {
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
                ModalShellPanel shell = new ModalShellPanel(modes, chatShell, agentShell, comic);
                shell.setSize(700, 520);
                shell.doLayout();

                assertSame(chatShell, shell.visibleShell());
                assertTrue(shell.switchBar().button(ShellMode.CHAT).isSelected());
                chat.sendRequested("Frage an den Chat", false);

                shell.switchBar().button(ShellMode.AGENT).doClick();
                assertEquals(ShellMode.AGENT, modes.getMode());
                assertSame(agentShell, shell.visibleShell());
                assertFalse(shell.switchBar().button(ShellMode.CHAT).isSelected());
                agent.sendRequested("Auftrag an den Agenten", false);

                shell.switchBar().button(ShellMode.CHAT).doClick();
                assertSame(chatShell, shell.visibleShell());

                assertEquals(2, chatModel.getEntries().size());
                assertEquals("Frage an den Chat", chatModel.getEntries().get(0).getText());
                assertEquals("Chat-Antwort", chatModel.getEntries().get(1).getText());
                assertEquals(2, agentModel.getEntries().size());
                assertEquals("Auftrag an den Agenten", agentModel.getEntries().get(0).getText());
                assertEquals("Agent-Antwort", agentModel.getEntries().get(1).getText());
                paint(shell);
            }
        });
    }

    @Test
    public void withoutAgentOnlyTheChatIsOffered() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                ChatShellModel chatModel = new ChatShellModel(() -> 0L);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new EchoActions(chatModel, "ok"), comic,
                        bubbles);
                ShellModeModel modes = new ShellModeModel(false);
                ModalShellPanel shell = new ModalShellPanel(modes, chatShell, null, comic);

                assertFalse(shell.switchBar().button(ShellMode.AGENT).isEnabled());
                shell.switchBar().button(ShellMode.AGENT).doClick(); // deaktiviert: wirkungslos
                assertEquals(ShellMode.CHAT, modes.getMode());
                assertSame(chatShell, shell.visibleShell());
                assertEquals(null, shell.agentShell());
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
                    new ModalShellPanel(new ShellModeModel(true), chatShell, null, comic);
                }
            });
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void paint(ModalShellPanel shell) {
        BufferedImage image = new BufferedImage(shell.getWidth(), shell.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            shell.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        SwingUtilities.invokeAndWait(runnable);
    }
}
