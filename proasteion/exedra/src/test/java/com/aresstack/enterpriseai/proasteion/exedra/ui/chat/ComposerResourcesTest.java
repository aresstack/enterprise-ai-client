package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.event.MouseEvent;
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
    public void leftClickAddsRightClickAndReaderIconOnlyOpen() throws Exception {
        Edt.run(() -> {
            ResourceActions actions = new ResourceActions(Arrays.asList(FILE, PAGE, SOURCE));
            ChatComposerPanel composer = composer(actions);
            RecordingReader reader = new RecordingReader();
            composer.setResourceReader(reader);
            composer.refreshResources();
            JPopupMenu menu = openMenu(composer);
            ResourceMenuItem source = (ResourceMenuItem) item(menu, "ZAUF Schnittstellenvertrag");
            source.setSize(source.getPreferredSize());

            // Right click: context menu with "Im Lesebereich öffnen", nothing added.
            click(source, MouseEvent.BUTTON3, 5);
            assertTrue("Rechtsklick hängt nichts an", composer.references().isEmpty());
            JPopupMenu context = composer.lastResourceContextMenu();
            assertNotNull("Kontextmenü angefordert", context);
            JMenuItem open = (JMenuItem) context.getComponent(0);
            assertEquals("Im Lesebereich öffnen", open.getText());
            assertTrue(open.isEnabled());
            open.doClick();
            assertEquals(Arrays.asList(SOURCE), reader.read);
            assertTrue(composer.references().isEmpty());

            // Reader icon: opens directly, nothing added.
            java.awt.Rectangle icon = source.readerIconBounds();
            assertFalse("Symbol rechts", icon.isEmpty());
            assertEquals(ResourceMenuItem.OPEN_IN_READER, source.getToolTipText(new MouseEvent(source,
                    MouseEvent.MOUSE_MOVED, 0L, 0, icon.x + 1, icon.y + 1, 0, false)));
            click(source, MouseEvent.BUTTON1, icon.x + 1);
            assertEquals(Arrays.asList(SOURCE, SOURCE), reader.read);
            assertTrue("Symbol hängt nichts an", composer.references().isEmpty());

            // Left click on the entry keeps adding the reference.
            source.doClick();
            assertEquals(Arrays.asList(SOURCE), composer.references());
            assertEquals("Linksklick öffnet nichts", 2, reader.read.size());
        });
    }

    /**
     * Issue #119: the action menu offers "URL kopieren" only for a real browser address of a used source, copies it
     * exactly, never a bookmark or an id, and opens right next to its item inside the still open overview.
     */
    @Test
    public void actionMenuCopiesOnlyRealBrowserUrlsAndOpensBesideTheItem() throws Exception {
        Edt.run(() -> {
            ChatResource web = new ChatResource(ChatResource.Kind.SOURCE, "wiki:ZAUF", "ZAUF Vertrag",
                    "https://wiki.example.test/display/ZAUF/Vertrag");
            ChatResource idOnly = new ChatResource(ChatResource.Kind.SOURCE, "wiki:NIZZA", "NIZZA", "wiki:NIZZA");
            ChatComposerPanel composer = composer(new ResourceActions(Arrays.asList(FILE, PAGE, web, idOnly)));
            final List<String> copied = new ArrayList<String>();
            composer.setClipboard(copied::add);
            composer.refreshResources();

            JMenuItem copy = copyItem(composer.resourceContextMenu(web));
            assertTrue(copy.isEnabled());
            copy.doClick();
            assertEquals(Arrays.asList("https://wiki.example.test/display/ZAUF/Vertrag"), copied);
            assertTrue("Kopieren hängt nichts an", composer.references().isEmpty());

            assertFalse("Bookmark ist keine Browser-URL", copyItem(composer.resourceContextMenu(PAGE)).isEnabled());
            assertFalse("Resource-ID ist keine Browser-URL",
                    copyItem(composer.resourceContextMenu(idOnly)).isEnabled());
            assertFalse(copyItem(composer.resourceContextMenu(FILE)).isEnabled());
            assertFalse(copyItem(composer.resourceContextMenu(SOURCE)).isEnabled());

            JPopupMenu menu = openMenu(composer);
            assertTrue(menu instanceof ChatComposerPanel.ResourcesPopupMenu);
            ResourceMenuItem item = (ResourceMenuItem) item(menu, "ZAUF Vertrag");
            item.setSize(item.getPreferredSize());
            assertEquals(new java.awt.Point(item.getWidth(), 0), ChatComposerPanel.actionMenuLocation(item));
            click(item, MouseEvent.BUTTON3, 5);
            assertEquals(ChatComposerPanel.COPY_URL,
                    ((JMenuItem) composer.lastResourceContextMenu().getComponent(1)).getText());
            assertTrue("Rechtsklick hängt nichts an", composer.references().isEmpty());
        });
    }

    private static JMenuItem copyItem(JPopupMenu context) {
        JMenuItem copy = (JMenuItem) context.getComponent(1);
        assertEquals(ChatComposerPanel.COPY_URL, copy.getText());
        return copy;
    }

    @Test
    public void unreadableResourcesOfferNoReaderAction() throws Exception {
        Edt.run(() -> {
            ChatComposerPanel composer = composer(new ResourceActions(Arrays.asList(FILE, SOURCE)));
            RecordingReader reader = new RecordingReader();
            composer.setResourceReader(reader);
            composer.refreshResources();
            JPopupMenu menu = openMenu(composer);
            ResourceMenuItem file = (ResourceMenuItem) item(menu, "IAR-Konzept.pdf");
            assertFalse("kein Symbol für Dateien", file.readable());
            assertFalse(composer.resourceContextMenu(FILE).getComponent(0).isEnabled());

            ChatComposerPanel withoutReader = composer(new ResourceActions(Arrays.asList(SOURCE)));
            withoutReader.refreshResources();
            assertFalse("ohne Lesebereich kein Symbol",
                    ((ResourceMenuItem) item(openMenu(withoutReader), "ZAUF Schnittstellenvertrag")).readable());
        });
    }

    private static void click(java.awt.Component target, int button, int x) {
        int mask = button == MouseEvent.BUTTON3 ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK
                : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        int y = target.getHeight() / 2;
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0L, mask, x, y, 1, false, button));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, 0L, mask, x, y, 1, false, button));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, 0L, mask, x, y, 1, false, button));
    }

    /** Reads only pages and sources, as the real navigation does. */
    private static final class RecordingReader implements ChatResourceReader {
        final List<ChatResource> read = new ArrayList<ChatResource>();

        @Override
        public boolean canRead(ChatResource resource) {
            return resource.kind() != ChatResource.Kind.FILE;
        }

        @Override
        public void read(ChatResource resource) {
            read.add(resource);
        }
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
