package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Die Ressourcenübersicht im Composer (Akzeptanztests 10, 11, 13): „Ressourcen n“ rechts neben „Denken“, ein Klick
 * nimmt eine Ressource als Verweis-Chip in den Entwurf, ✕ nimmt nur den Bezug zurück, Senden übergibt einen
 * {@link ChatDraft} mit unverändertem Text.
 */
public class ComposerResourcesTest {

    private static final ChatResource FILE = new ChatResource(ChatResource.Kind.FILE, "att-1", "IAR-Konzept.pdf",
            "12 KB");
    private static final ChatResource PAGE = new ChatResource(ChatResource.Kind.PAGE, "wiki://intern/NIZZA",
            "NIZZA Fachkonzept", "");
    private static final ChatResource SOURCE = new ChatResource(ChatResource.Kind.SOURCE, "wiki:ZAUF",
            "ZAUF Schnittstellenvertrag", "");

    @Test
    public void pillCountsTheResourcesAndSitsRightOfReasoning() throws Exception {
        Edt.run(() -> {
            ResourceActions actions = new ResourceActions(Arrays.asList(FILE, PAGE, SOURCE));
            ChatComposerPanel composer = composer(actions);
            composer.refreshResources();
            assertEquals("Ressourcen 3", composer.resourcesButton().getText());
            assertTrue(composer.resourcesButton().isEnabled());
            java.awt.Container row = composer.reasoningButton().getParent();
            assertEquals("rechts neben Denken", indexOf(row, composer.reasoningButton()) + 1,
                    indexOf(row, composer.resourcesButton()));

            ChatComposerPanel empty = composer(new ResourceActions(new ArrayList<ChatResource>()));
            empty.refreshResources();
            assertEquals("Ressourcen 0", empty.resourcesButton().getText());
            assertFalse(empty.resourcesButton().isEnabled());
        });
    }

    @Test
    public void choosingAResourceAddsAChipAndRemovingItKeepsTheResource() throws Exception {
        Edt.run(() -> {
            ResourceActions actions = new ResourceActions(Arrays.asList(FILE, PAGE, SOURCE));
            ChatComposerPanel composer = composer(actions);
            composer.refreshResources();
            JPopupMenu menu = openMenu(composer);
            List<String> labels = new ArrayList<String>();
            for (Component item : menu.getComponents()) {
                if (item instanceof JMenuItem) {
                    labels.add(((JMenuItem) item).getText());
                }
            }
            assertEquals(Arrays.asList("DATEIEN", "IAR-Konzept.pdf", "SEITEN", "NIZZA Fachkonzept",
                    "VERWENDETE QUELLEN", "ZAUF Schnittstellenvertrag"), labels);
            item(menu, "ZAUF Schnittstellenvertrag").doClick();
            menu.setVisible(false);
            assertEquals(Arrays.asList(SOURCE), composer.references());
            assertTrue("kein Senden beim Klick", actions.drafts.isEmpty());

            composer.addReference(SOURCE);
            assertEquals("dieselbe Ressource nur einmal", 1, composer.references().size());
            composer.removeReference(SOURCE);
            assertTrue(composer.references().isEmpty());
            assertEquals("die Ressource bleibt im Chat", 3, composer.resources().size());
        });
    }

    @Test
    public void sendingPassesTheReferencesWithTheUnchangedText() throws Exception {
        Edt.run(() -> {
            ResourceActions actions = new ResourceActions(Arrays.asList(FILE, SOURCE));
            ChatComposerPanel composer = composer(actions);
            composer.addReference(FILE);
            composer.addReference(SOURCE);
            composer.editor().setText("  Was genau ist damit gemeint?  ");
            composer.submit();
            assertEquals(1, actions.drafts.size());
            ChatDraft draft = actions.drafts.get(0);
            assertEquals("Was genau ist damit gemeint?", draft.text());
            assertEquals(Arrays.asList(FILE, SOURCE), draft.references());
            assertTrue("keine neue Datei", draft.attachments().isEmpty());
            assertTrue("Chips geleert", composer.references().isEmpty());
        });
    }

    @Test
    public void openingAnotherChatDropsPendingReferences() throws Exception {
        Edt.run(() -> {
            ChatShellModel model = new ChatShellModel(() -> 0L);
            ChatComposerPanel composer = new ChatComposerPanel(model,
                    new ResourceActions(Arrays.asList(FILE)), ComicPalette.defaultPalette());
            model.addUserMessage("Hallo");
            composer.addReference(FILE);
            model.clear();
            assertTrue(composer.references().isEmpty());
        });
    }

    private static ChatComposerPanel composer(ChatShellActions actions) {
        return new ChatComposerPanel(new ChatShellModel(() -> 0L), actions, ComicPalette.defaultPalette());
    }

    private static int indexOf(java.awt.Container parent, Component child) {
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) == child) {
                return i;
            }
        }
        return -1;
    }

    private static JPopupMenu openMenu(ChatComposerPanel composer) {
        JPopupMenu menu = composer.lastResourcesMenu();
        assertNotNull("Übersicht geöffnet", menu);
        return menu;
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (Component component : menu.getComponents()) {
            if (component instanceof JMenuItem && text.equals(((JMenuItem) component).getText())) {
                return (JMenuItem) component;
            }
        }
        throw new AssertionError("kein Eintrag " + text);
    }

    /** Liefert feste Ressourcen und zeichnet gesendete Entwürfe auf. */
    private static final class ResourceActions implements ChatShellActions {
        final List<ChatResource> resources;
        final List<ChatDraft> drafts = new ArrayList<ChatDraft>();

        ResourceActions(List<ChatResource> resources) {
            this.resources = resources;
        }

        @Override
        public List<ChatResource> chatResources() {
            return resources;
        }

        @Override
        public void sendRequested(ChatDraft draft) {
            drafts.add(draft);
        }

        @Override
        public void sendRequested(String text, boolean ragEnabled) {
            throw new AssertionError("der Composer sendet einen ChatDraft");
        }

        @Override
        public void stopRequested() {
        }
    }
}
