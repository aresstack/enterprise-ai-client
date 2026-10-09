package com.aresstack.enterpriseai.app.ui.sidebar;

import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Die Seiten des Drawers (askai arch): CardLayout ohne JTabbedPane, Standardseite zuerst, Beiträge beim
 * Neuaufbau angehängt, Wechsel per Titel; die Kopfzeile (Chat-Suche) gehört nur zur Standardseite.
 */
public class ChatSidebarPanelTest {

    private static ChatSidebarTab tab(final String title) {
        return new ChatSidebarTab() {
            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public JComponent getComponent() {
                return new JLabel(title);
            }
        };
    }

    @Test
    public void theDefaultPaneIsAlwaysFirstAndActive() {
        ChatSidebarPanel panel = new ChatSidebarPanel("Chats", new JPanel());
        assertEquals(Arrays.asList("Chats"), panel.tabTitles());
        assertEquals("Chats", panel.activeTab());
    }

    @Test
    public void contributedPanesAppearAfterTheDefaultOnRebuild() {
        ChatSidebarPanel panel = new ChatSidebarPanel("Chats", new JPanel());
        final List<ChatSidebarTab> extras = Arrays.asList(tab("Wissensquellen"), tab("Notizen"));
        panel.setExtraTabsSupplier(() -> extras);
        panel.rebuildTabs();
        assertEquals(Arrays.asList("Chats", "Wissensquellen", "Notizen"), panel.tabTitles());

        panel.setExtraTabsSupplier(() -> Arrays.asList(tab("Wissensquellen")));
        panel.rebuildTabs();
        assertEquals("kein Ansammeln alter Seiten", Arrays.asList("Chats", "Wissensquellen"), panel.tabTitles());
    }

    @Test
    public void showTabSwitchesTheActivePaneAndIgnoresUnknownTitles() {
        ChatSidebarPanel panel = new ChatSidebarPanel("Chats", new JPanel());
        panel.setExtraTabsSupplier(() -> Arrays.asList(tab("Wissensquellen")));
        panel.rebuildTabs();

        panel.showTab("Wissensquellen");
        assertEquals("Wissensquellen", panel.activeTab());

        panel.showTab("Nope");
        assertEquals("bleibt auf der letzten gültigen Seite", "Wissensquellen", panel.activeTab());
    }

    @Test
    public void aVanishedContributionFallsBackToTheDefaultPane() {
        ChatSidebarPanel panel = new ChatSidebarPanel("Chats", new JPanel());
        panel.setExtraTabsSupplier(() -> Arrays.asList(tab("Wissensquellen")));
        panel.rebuildTabs();
        panel.showTab("Wissensquellen");

        panel.setExtraTabsSupplier(null);
        panel.rebuildTabs();
        assertEquals(Arrays.asList("Chats"), panel.tabTitles());
        assertEquals("Chats", panel.activeTab());
    }

    @Test
    public void nullEntriesAreTolerated() {
        ChatSidebarPanel panel = new ChatSidebarPanel("Chats", new JPanel());
        panel.setExtraTabsSupplier(() -> Arrays.asList(tab("Eins"), null));
        panel.rebuildTabs();
        assertEquals(Arrays.asList("Chats", "Eins"), panel.tabTitles());
    }

    @Test
    public void theChatFilterHeaderShowsOnlyOnTheChatsPane() {
        ChatSidebarPanel sidebar = new ChatSidebarPanel("Chats", new JPanel());
        sidebar.setHeaderComponent(new JLabel("Chats durchsuchen…"));
        sidebar.setExtraTabsSupplier(() -> Arrays.asList(tab("Wissensquellen")));
        sidebar.rebuildTabs();

        assertTrue("auf der Chats-Seite ist die Suche zu Hause", sidebar.isHeaderVisible());
        sidebar.showTab("Wissensquellen");
        assertFalse("auf jeder anderen Seite fehl am Platz", sidebar.isHeaderVisible());
        sidebar.showTab("Chats");
        assertTrue(sidebar.isHeaderVisible());
    }
}
