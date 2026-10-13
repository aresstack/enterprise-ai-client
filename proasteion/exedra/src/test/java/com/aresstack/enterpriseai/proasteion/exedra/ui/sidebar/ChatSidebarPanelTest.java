package com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar;

import org.junit.Test;

import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

    @Test
    public void theLeadingFooterFollowsTheActivePaneAndStaysEmptyWithoutContribution() {
        final JButton newChat = new JButton("+ Neuer Chat");
        final JButton addSource = new JButton("+ Quelle");
        ChatSidebarPanel sidebar = new ChatSidebarPanel("Chats", new JPanel());
        sidebar.setDefaultTabFooterLeadingComponent(newChat);
        sidebar.setExtraTabsSupplier(() -> Arrays.asList(new ChatSidebarTab() {
            @Override
            public String getTitle() {
                return "Wissensquellen";
            }

            @Override
            public JComponent getComponent() {
                return new JLabel("Wissensquellen");
            }

            @Override
            public JComponent getFooterLeadingComponent() {
                return addSource;
            }
        }, tab("Favoriten")));
        sidebar.rebuildTabs();

        assertSame(newChat, sidebar.footerLeadingComponent());
        sidebar.showTab("Wissensquellen");
        assertSame(addSource, sidebar.footerLeadingComponent());
        assertNull("der Knopf der vorigen Seite bleibt nicht stehen", newChat.getParent());
        sidebar.showTab("Favoriten");
        assertNull("ohne Beitrag bleibt links leer", sidebar.footerLeadingComponent());
        assertNull(addSource.getParent());
        sidebar.showTab("Chats");
        assertSame(newChat, sidebar.footerLeadingComponent());
        sidebar.rebuildTabs();
        assertSame("Neuaufbau behält den Beitrag der aktiven Seite", newChat, sidebar.footerLeadingComponent());
    }

    @Test
    public void footerPillsShareOneSizeWhateverTheirLabel() {
        ResearchPillButton newChat = SidebarFooterPill.create("+ Neuer Chat", "Neuen Chat beginnen");
        ResearchPillButton addSource = SidebarFooterPill.create("+ Quelle", "Quelle hinzufügen");
        assertEquals(newChat.getPreferredSize(), addSource.getPreferredSize());
        assertEquals(newChat.getFont(), addSource.getFont());
        assertEquals(ResearchUiMetrics.FOOTER_CONTROL_HEIGHT, addSource.getPreferredSize().height);
    }
}
