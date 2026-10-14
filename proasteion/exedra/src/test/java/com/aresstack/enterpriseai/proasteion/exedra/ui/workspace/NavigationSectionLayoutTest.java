package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** #114: the navigation drawer shows, hides, orders and sizes its three sections and remembers that globally. */
public class NavigationSectionLayoutTest {

    private static final double EPSILON = 1e-6;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void theDefaultShowsAllThreeWithTheTreeLargest() {
        NavigationSectionLayout layout = NavigationSectionLayout.defaults();
        assertEquals(Arrays.asList("tree", "outgoing", "incoming"), layout.visibleOrder());
        assertEquals(1d, layout.share("tree") + layout.share("outgoing") + layout.share("incoming"), EPSILON);
        assertTrue(layout.share("tree") > layout.share("outgoing"));
        assertEquals(0.62, layout.share("tree"), EPSILON);
    }

    @Test
    public void togglingHidesAndShowsAndKeepsTheSharesNormalized() {
        NavigationSectionLayout layout = NavigationSectionLayout.defaults().toggle("outgoing");
        assertEquals(Arrays.asList("tree", "incoming"), layout.visibleOrder());
        assertEquals(0d, layout.share("outgoing"), EPSILON);
        assertEquals(1d, layout.share("tree") + layout.share("incoming"), EPSILON);
        NavigationSectionLayout back = layout.toggle("outgoing");
        assertEquals(Arrays.asList("tree", "outgoing", "incoming"), back.visibleOrder());
        assertEquals(1d, back.share("tree") + back.share("outgoing") + back.share("incoming"), EPSILON);
    }

    @Test
    public void movingPutsASectionAtTheDropPositionAndShowsIt() {
        NavigationSectionLayout layout = NavigationSectionLayout.defaults().toggle("incoming").moveTo("incoming", 1);
        assertEquals(Arrays.asList("tree", "incoming", "outgoing"), layout.visibleOrder());
        assertEquals(Arrays.asList("incoming", "tree", "outgoing"), layout.moveTo("incoming", 0).visibleOrder());
        assertEquals(Arrays.asList("tree", "outgoing", "incoming"), layout.moveTo("incoming", 9).visibleOrder());
    }

    @Test
    public void aDividerMovesTheBorderOfItsTwoNeighboursOnly() {
        NavigationSectionLayout layout = NavigationSectionLayout.defaults();
        NavigationSectionLayout resized = layout.resize(0, -0.12);
        assertEquals(0.50, resized.share("tree"), EPSILON);
        assertEquals(0.34, resized.share("outgoing"), EPSILON);
        assertEquals(layout.share("incoming"), resized.share("incoming"), EPSILON);
        NavigationSectionLayout squeezed = layout.resize(1, 1.0);
        assertEquals(NavigationSectionLayout.MIN_SHARE, squeezed.share("incoming"), EPSILON);
    }

    @Test
    public void storedValuesRoundTripAndInvalidOnesFallBack() {
        NavigationSectionLayout layout = NavigationSectionLayout.defaults().toggle("tree").moveTo("incoming", 0)
                .resize(0, 0.1);
        NavigationSectionLayout read = NavigationSectionLayout.parse(layout.encodeOrder(), layout.encodeVisible(),
                layout.encodeSizes());
        assertEquals(layout, read);
        assertEquals(Collections.<String>emptyList(), NavigationSectionLayout.parse("tree,outgoing,incoming", "",
                null).visibleOrder());

        NavigationSectionLayout broken = NavigationSectionLayout.parse("tree,tree,x", "nope", "a,-1");
        assertEquals(NavigationSectionLayout.defaults(), broken);
        assertEquals(NavigationSectionLayout.defaults(), NavigationSectionLayout.parse(null, null, null));
    }

    @Test
    public void thePanelHasOneToggleEachAndDividersOnlyBetweenVisibleSections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageNavigationPanel panel = new PageNavigationPanel(ComicPalette.defaultPalette());
            assertEquals(3, panel.sectionToggles().getComponentCount());
            assertEquals(2, panel.sectionStack().dividers().size());
            assertTrue(panel.sectionToggle("tree").isEmphasized());

            panel.sectionToggle("outgoing").doClick();
            assertFalse(panel.sectionLayout().isVisible("outgoing"));
            assertFalse(panel.sectionToggle("outgoing").isEmphasized());
            assertEquals(1, panel.sectionStack().dividers().size());
            panel.sectionToggle("incoming").doClick();
            assertEquals("one section: no divider", 0, panel.sectionStack().dividers().size());
            panel.sectionToggle("tree").doClick();
            assertEquals(Collections.<String>emptyList(), panel.sectionLayout().visibleOrder());
            panel.sectionToggle("outgoing").doClick();
            assertTrue(panel.sectionLayout().isVisible("outgoing"));
        });
    }

    @Test
    public void outgoingAndIncomingReferencesShowInTheirOwnSections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageNavigationPanel panel = new PageNavigationPanel(ComicPalette.defaultPalette());
            panel.showReferences(Collections.<PageNavigationPanel.Item>emptyList(), null);
            assertEquals(1, panel.outgoingReferences().getModel().getChildCount(panel.outgoingReferences().getModel()
                    .getRoot()));
            assertEquals(Arrays.asList("Verweist auf", "  " + PageNavigationPanel.NO_OUTGOING, "Wird verlinkt von",
                    "  " + PageNavigationPanel.INCOMING_UNSUPPORTED), panel.referenceRows());
            Object incomingGroup = panel.incomingReferences().getModel().getChild(
                    panel.incomingReferences().getModel().getRoot(), 0);
            assertEquals(1, panel.incomingReferences().getModel().getChildCount(incomingGroup));
        });
    }

    @Test
    public void theLayoutIsStoredOnceAfterChangesAndRestoredByANewWorkspace() throws Exception {
        final Path file = temp.getRoot().toPath().resolve("application-state.json");
        SwingUtilities.invokeAndWait(() -> {
            ChatWorkspacePanel workspace = workspace();
            ApplicationStateService state = new ApplicationStateService(file);
            workspace.setApplicationState(state);
            assertEquals(NavigationSectionLayout.defaults(), workspace.navigation().sectionLayout());

            workspace.navigation().toggleSection("tree");
            workspace.navigation().toggleSection("incoming");
            workspace.navigation().toggleSection("incoming");
            assertNull("nothing written while the changes keep coming",
                    state.get(ChatWorkspacePanel.STATE_NAVIGATION_VISIBLE, null));
            workspace.saveNavigationLayoutNow();
            assertEquals("outgoing,incoming", state.get(ChatWorkspacePanel.STATE_NAVIGATION_VISIBLE, null));

            ChatWorkspacePanel restarted = workspace();
            restarted.setApplicationState(new ApplicationStateService(file));
            assertEquals(workspace.navigation().sectionLayout(), restarted.navigation().sectionLayout());
            assertSame("the navigation tab carries the toggles in its footer",
                    restarted.navigation().sectionToggles(), footerOf(restarted));
        });
    }

    private static Object footerOf(ChatWorkspacePanel workspace) {
        workspace.selectWorkspaceTab(ChatWorkspacePanel.NAVIGATION_TAB);
        return workspace.sidebar().footerLeadingComponent();
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
}
