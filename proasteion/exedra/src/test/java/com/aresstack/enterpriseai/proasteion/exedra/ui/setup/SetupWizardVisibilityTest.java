package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ToolOverview;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Regression zum gemeldeten Fehler „Einstellungen → Einrichtungsassistent → Settings schließen, sonst nichts“: Der
 * Knopf im Einstellungen-Panel öffnet den Assistenten über dem ganzen Hauptfenster (Modal-Ebene der Root-Pane), egal
 * ob Chat, Agent oder Lesebereich vorn liegt. Geprüft wird die echte Komponentenhierarchie bis zur Root-Pane, nicht
 * nur der Aufruf: jede Ebene sichtbar, das Overlay deckt das Fenster, und in der Fenstermitte liegt der Assistent
 * oben. Mit Display zusätzlich im echten Hauptfenster ({@link JFrame#isShowing()}).
 */
public class SetupWizardVisibilityTest {

    private static final int WIDTH = 1040;
    private static final int HEIGHT = 720;

    private final ComicPalette palette = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();
    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private enum View { CHAT, AGENT, READER }

    private static final class Silent implements ChatShellActions {
        @Override
        public void sendRequested(String text, boolean ragEnabled) {
        }

        @Override
        public void stopRequested() {
        }
    }

    private ChatShellPanel shell() {
        return new ChatShellPanel(new ChatShellModel(() -> 0L), new Silent(), palette, bubbles);
    }

    /** Wie die Anwendung: eigene Agent-Karte oder (produktiv) dieselbe Chat-Ansicht für beide Modi. */
    private ChatWorkspacePanel workspace(boolean ownAgentCard) {
        ShellModeModel modes = new ShellModeModel(true);
        return new ChatWorkspacePanel(modes, shell(), ownAgentCard ? shell() : null, palette);
    }

    private static void bringToFront(ChatWorkspacePanel workspace, View view) {
        switch (view) {
            case AGENT:
                workspace.modePill().select(1);
                break;
            case READER:
                workspace.showReader();
                break;
            default:
                workspace.modePill().select(0);
        }
    }

    /** Einstellungen → „Einrichtungsassistent“ wie in der Anwendung: der Knopf schließt und startet den Lauf. */
    private SetupWizardRun openFromSettings(final JRootPane window) {
        final SettingsForm form = SettingsMapper.firstStartDefaults();
        final AtomicReference<SetupWizardRun> run = new AtomicReference<SetupWizardRun>();
        final List<String> closed = new ArrayList<String>();
        SettingsPanel panel = new SettingsPanel(form, Collections.<String>emptyList(), SettingsPanel.Mode.EDIT,
                settings, ToolOverview.none(), palette);
        panel.setOnCancel(() -> closed.add("settings"));
        panel.setSetupWizardAction(() -> run.set(SetupWizardOverlay.show(window, form, settings, setup, palette,
                completed -> {
                })));
        assertTrue(panel.setupWizardButton().isVisible());
        panel.setupWizardButton().doClick();
        assertEquals("Einstellungen geschlossen", Collections.singletonList("settings"), closed);
        assertNotNull("Assistent gestartet", run.get());
        return run.get();
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static void assertCoversWindow(JRootPane window, ChatWorkspacePanel workspace, SetupWizardRun run,
                                           String scenario) {
        SetupWizardPanel wizard = run.wizard();
        JLayeredPane layers = window.getLayeredPane();
        assertSame(scenario + ": Overlay in der Ebene des Fensters", layers, run.overlay().getParent());
        assertEquals(scenario, JLayeredPane.MODAL_LAYER.intValue(), layers.getLayer(run.overlay()));
        assertEquals(scenario + ": deckt das Fenster", layers.getBounds().getSize(), run.overlay().getSize());
        assertFalse(scenario + ": nicht in der Arbeitsfläche", SwingUtilities.isDescendingFrom(wizard, workspace));
        for (Component c = wizard; c != window; c = c.getParent()) {
            assertNotNull(scenario + ": hängt am Fenster", c);
            assertTrue(scenario + ": sichtbar " + c.getClass().getSimpleName(), c.isVisible());
        }
        assertTrue(scenario + ": Assistent hat Fläche", wizard.getWidth() > 300 && wizard.getHeight() > 300);
        Component top = layers.findComponentAt(layers.getWidth() / 2, layers.getHeight() / 2);
        assertTrue(scenario + ": in der Fenstermitte liegt der Assistent oben, nicht " + top,
                top != null && SwingUtilities.isDescendingFrom(top, run.overlay()));
    }

    private void checkHeadless(final boolean ownAgentCard) throws Exception {
        for (final View view : View.values()) {
            final String scenario = view + (ownAgentCard ? " (eigene Agent-Karte)" : "");
            final AtomicReference<ChatWorkspacePanel> workspace = new AtomicReference<ChatWorkspacePanel>();
            final AtomicReference<JRootPane> window = new AtomicReference<JRootPane>();
            final AtomicReference<SetupWizardRun> run = new AtomicReference<SetupWizardRun>();
            SwingUtilities.invokeAndWait(() -> {
                workspace.set(workspace(ownAgentCard));
                window.set(new JRootPane());
                window.get().setContentPane(ShellFrame.content("Test", workspace.get(), palette, () -> {
                }));
                window.get().setSize(WIDTH, HEIGHT);
                layoutTree(window.get());
                bringToFront(workspace.get(), view);
                if (view == View.READER) {
                    assertTrue(workspace.get().isReaderShowing());
                    assertFalse("der frühere Gastgeber (Chat-Antwortfenster) ist hinter dem Lesebereich verdeckt",
                            workspace.get().chatShell().isVisible());
                }
                if (view == View.AGENT) {
                    assertEquals(1, workspace.get().modePill().getSelectedIndex());
                }
                run.set(openFromSettings(window.get()));
                layoutTree(window.get().getLayeredPane());
                assertCoversWindow(window.get(), workspace.get(), run.get(), scenario);
                // Fenster vergrößert: das Overlay geht mit (Größenereignis kommt über die Ereignisschlange)
                window.get().setSize(WIDTH + 300, HEIGHT + 200);
                window.get().doLayout();
            });
            SwingUtilities.invokeAndWait(() -> {
                layoutTree(window.get().getLayeredPane());
                assertCoversWindow(window.get(), workspace.get(), run.get(), scenario + " vergrößert");
                run.get().wizard().cancelButton().doClick();
                assertFalse(scenario + ": Abbrechen entfernt das Overlay",
                        run.get().overlay().getParent() == window.get().getLayeredPane());
            });
        }
    }

    @Test
    public void wizardFromSettingsCoversTheWindowInChatAgentAndReader() throws Exception {
        checkHeadless(false);
    }

    @Test
    public void wizardFromSettingsCoversTheWindowWithASeparateAgentCard() throws Exception {
        checkHeadless(true);
    }

    @Test
    public void secondCallReplacesTheOpenWizard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRootPane window = new JRootPane();
            window.setContentPane(ShellFrame.content("Test", workspace(false), palette, () -> {
            }));
            window.setSize(WIDTH, HEIGHT);
            final List<Boolean> first = new ArrayList<Boolean>();
            SetupWizardRun one = SetupWizardOverlay.show(window, SettingsMapper.firstStartDefaults(), settings,
                    setup, palette, first::add);
            SetupWizardRun two = SetupWizardOverlay.show(window, SettingsMapper.firstStartDefaults(), settings,
                    setup, palette, completed -> {
                    });
            assertEquals("der erste Lauf endet als Abbruch", Collections.singletonList(Boolean.FALSE), first);
            int overlays = 0;
            for (Component child : window.getLayeredPane().getComponents()) {
                if (SetupWizardOverlay.NAME.equals(child.getName())) {
                    overlays++;
                }
            }
            assertEquals(1, overlays);
            assertSame(window.getLayeredPane(), two.overlay().getParent());
            assertFalse(one.overlay().getParent() == window.getLayeredPane());
        });
    }

    /** Mit Display: im echten rahmenlosen Hauptfenster ist der Assistent auf dem Bildschirm, auch aus dem Reader. */
    @Test
    public void wizardIsShowingInTheRealMainWindow() throws Exception {
        assumeFalse("braucht ein Display", GraphicsEnvironment.isHeadless());
        final List<JFrame> frames = new ArrayList<JFrame>();
        try {
            for (final View view : View.values()) {
                final AtomicReference<SetupWizardRun> run = new AtomicReference<SetupWizardRun>();
                SwingUtilities.invokeAndWait(() -> {
                    ChatWorkspacePanel workspace = workspace(false);
                    JFrame frame = ShellFrame.create("Test", workspace, palette);
                    frames.add(frame);
                    frame.setVisible(true);
                    bringToFront(workspace, view);
                    run.set(openFromSettings(frame.getRootPane()));
                });
                SwingUtilities.invokeAndWait(() -> {
                    assertTrue(view + ": Assistent auf dem Bildschirm", run.get().wizard().isShowing());
                    assertTrue(view + ": „Weiter“ auf dem Bildschirm", run.get().wizard().nextButton().isShowing());
                });
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (JFrame frame : frames) {
                    frame.dispose();
                }
            });
        }
    }
}
