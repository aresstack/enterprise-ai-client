package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.PageNavigationUseCase;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.PageNavigationPanel;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.source.api.testing.InMemoryNavigationSource;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Issue #97: the compass follows the reader, shows the space tree and the cross references. */
public class PageNavigationControllerTest {

    private static final String SOURCE = "wiki";
    private static final int BATCH = 3;
    private static final int MANY = 11;

    private final InMemoryNavigationSource source = new InMemoryNavigationSource(SOURCE, BATCH)
            .space("DEV", "Development").page("DEV", "home", "Home").page("home", "guide", "Guide")
            .page("guide", "setup", "Setup").page("home", "faq", "FAQ").page("DEV", "second", "Second root")
            .space("OPS", "Operations").page("OPS", "runbook", "Runbook")
            .link("setup", "faq", "runbook");
    private final List<ReaderLink> opened = new ArrayList<ReaderLink>();

    private String key(String page) {
        return SOURCE + "\n" + source.idOf(page).value();
    }

    private ReaderLink link(String page) {
        return new ReaderLink(SOURCE, source.idOf(page).value(), page, "", "");
    }

    @Test
    public void theOpenPageSitsUnderItsSpaceWithItsPathOpen() throws Exception {
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(link("setup"));
            PageNavigationPanel panel = fixture.panel();

            assertEquals(SOURCE + "\nspace:DEV", panel.rootKey());
            assertEquals(Arrays.asList(key("home"), key("second")), panel.childKeys(SOURCE + "\nspace:DEV"));
            assertEquals(Arrays.asList(key("guide"), key("faq")), panel.childKeys(key("home")));
            assertEquals(key("setup"), panel.currentKey());
            TreePath selected = panel.tree().getSelectionPath();
            assertEquals(key("setup"), ((PageNavigationPanel.Item) ((DefaultMutableTreeNode)
                    selected.getLastPathComponent()).getUserObject()).key());
            assertTrue(panel.tree().isExpanded(selected.getParentPath()));
        });
    }

    @Test
    public void anotherSpaceReplacesTheRoot() throws Exception {
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(link("setup"));
            fixture.show(link("runbook"));

            assertEquals(SOURCE + "\nspace:OPS", fixture.panel().rootKey());
            assertEquals(key("runbook"), fixture.panel().currentKey());
        });
    }

    @Test
    public void aLateAnswerForAnOlderPageDoesNotOverwriteTheNewerOne() throws Exception {
        run(() -> {
            final Deque<Runnable> work = new ArrayDeque<Runnable>();
            Fixture fixture = new Fixture(work::add);
            fixture.show(link("setup"));
            fixture.show(link("runbook"));
            // The newer page answers first, the older one afterwards.
            List<Runnable> tasks = new ArrayList<Runnable>(work);
            work.clear();
            for (int i = tasks.size() - 1; i >= 0; i--) {
                tasks.get(i).run();
            }

            assertEquals(SOURCE + "\nspace:OPS", fixture.panel().rootKey());
            assertEquals(key("runbook"), fixture.panel().currentKey());
            assertEquals(Arrays.asList("Verweist auf", "  Keine Verweise auf andere Seiten.", "Wird verlinkt von",
                    "  Eingehende Verweise kann diese Quelle nicht zuverlässig ermitteln."),
                    fixture.panel().referenceRows());
        });
    }

    @Test
    public void openingABranchByHandLoadsAllItsChildrenInBatches() throws Exception {
        for (int i = 0; i < MANY; i++) {
            source.page("second", "child" + i, "Child " + i);
        }
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(link("setup"));
            PageNavigationPanel panel = fixture.panel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
            DefaultMutableTreeNode space = (DefaultMutableTreeNode) root.getChildAt(0);
            DefaultMutableTreeNode second = (DefaultMutableTreeNode) space.getChildAt(1);

            panel.tree().expandPath(new TreePath(second.getPath()));

            List<String> children = panel.childKeys(key("second"));
            assertEquals(MANY, children.size());
            assertEquals(key("child0"), children.get(0));
            assertEquals(key("child" + (MANY - 1)), children.get(MANY - 1));
            assertTrue(panel.isLoaded(key("second")));
        });
    }

    @Test
    public void crossReferencesAreSeparateFromTheTreeAndOpenThroughTheReaderPath() throws Exception {
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(link("setup"));

            assertEquals(Arrays.asList("Verweist auf (2)", "  FAQ", "  Runbook", "Wird verlinkt von",
                    "  Eingehende Verweise kann diese Quelle nicht zuverlässig ermitteln."),
                    fixture.panel().referenceRows());
            fixture.controller.pageOpened(link("faq"), false);
            assertEquals(1, opened.size());
            assertEquals(source.idOf("faq").value(), opened.get(0).resourceId());
        });
    }

    @Test
    public void incomingReferencesShowOnlyWhenTheSourceDeliversThem() throws Exception {
        source.withIncoming();
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(link("faq"));

            assertEquals(Arrays.asList("Verweist auf", "  Keine Verweise auf andere Seiten.",
                    "Wird verlinkt von (1)", "  Setup"), fixture.panel().referenceRows());
        });
    }

    @Test
    public void aSourceWithoutHierarchySaysSoInsteadOfAnEmptyTree() throws Exception {
        final InMemoryKnowledgeSource plain = new InMemoryKnowledgeSource("plain").add("x", "X", "text");
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run, plain);
            fixture.show(new ReaderLink("plain", plain.idOf("x").value(), "X", "", ""));

            assertNull(fixture.panel().rootKey());
            assertEquals(PageNavigationController.NOT_NAVIGABLE, fixture.panel().treeMessage());
        });
    }

    @Test
    public void aRestrictedPageShowsTheProblem() throws Exception {
        source.failWith("faq", KnowledgeSourceException.Kind.ACCESS_DENIED);
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(link("faq"));

            assertNull(fixture.panel().rootKey());
            assertTrue(fixture.panel().treeMessage().startsWith("Kein Zugriff"));
        });
    }

    @Test
    public void noOpenPageAndSourceOverviewsKeepTheirHints() throws Exception {
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.show(null);
            assertEquals(PageNavigationPanel.NOTHING_OPEN, fixture.panel().treeMessage());

            fixture.show(link("setup"));
            fixture.show(ReaderLink.partition(SOURCE, "space:DEV", "Development"));
            assertEquals(SOURCE + "\nspace:DEV", fixture.panel().rootKey());
            assertNull(fixture.panel().currentKey());
        });
    }

    @Test
    public void nothingLoadsWhileTheCompassIsHidden() throws Exception {
        run(() -> {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.controller.activePageChanged(link("setup"));

            assertTrue(source.calls().isEmpty());
            fixture.controller.shown();
            assertFalse(source.calls().isEmpty());
        });
    }

    /** Workspace, controller and the navigable source; the UI executor runs directly (tests run on the EDT). */
    private final class Fixture {
        final ChatWorkspacePanel workspace;
        final PageNavigationController controller;

        Fixture(Executor work) {
            this(work, source);
        }

        Fixture(Executor work, com.aresstack.enterpriseai.source.api.KnowledgeSourcePort port) {
            workspace = workspace();
            final KnowledgeSourceRegistration registration = new KnowledgeSourceRegistration(port,
                    SourceScope.of("x"));
            controller = new PageNavigationController(workspace,
                    id -> port.sourceId().value().equals(id) ? registration : null, new PageNavigationUseCase(),
                    (link, pin) -> opened.add(link), Runnable::run, work);
            workspace.navigation().setActions(controller);
        }

        void show(ReaderLink link) {
            controller.activePageChanged(link);
            controller.shown();
        }

        PageNavigationPanel panel() {
            return workspace.navigation();
        }
    }

    private static ChatWorkspacePanel workspace() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        ChatShellPanel shell = new ChatShellPanel(model, new ChatShellActions() {
            @Override
            public void sendRequested(String text, boolean ragEnabled) {
            }

            @Override
            public void stopRequested() {
            }
        }, ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired());
        return new ChatWorkspacePanel(new ShellModeModel(false), shell, null, ComicPalette.defaultPalette());
    }

    private static void run(Runnable body) throws Exception {
        try {
            SwingUtilities.invokeAndWait(body);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw ex;
        }
    }
}
