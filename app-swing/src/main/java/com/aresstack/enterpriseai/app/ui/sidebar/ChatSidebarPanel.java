package com.aresstack.enterpriseai.app.ui.sidebar;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Der Drawer (Seitenleiste) nach askai-java8 (arch): eine Seite je Reiter als schlichte CardLayout-Karten,
 * bewusst kein {@code JTabbedPane} — gewechselt wird über die Java2D-{@link SidebarTabRibbon} neben dem
 * Hamburger. Die feste Standardseite („Chats“) kommt zuerst; die beim Öffnen gelieferten
 * {@link ChatSidebarTab}-Beiträge folgen.
 *
 * <p>Der Drawer hat keine eigenen Pin- oder Schließknöpfe: Reiterleiste und Drawer öffnen und schließen
 * gemeinsam, die Arbeitsfläche bestimmt die Regel. Die Kopfzeile trägt nur die Komponente der
 * Arbeitsfläche (die Chat-Suche) und ist allein auf der Standardseite sichtbar.</p>
 */
public final class ChatSidebarPanel extends JPanel {

    private final CardLayout paneLayout = new CardLayout();
    private final JPanel panes = new JPanel(paneLayout);
    private final JPanel headerLeft = new JPanel(new BorderLayout());
    private JComponent header;
    private final String defaultTabTitle;
    private final JComponent defaultTabComponent;
    private final List<String> titles = new ArrayList<String>();

    private String activeTitle;
    private Supplier<List<ChatSidebarTab>> extraTabsSupplier;

    public ChatSidebarPanel(String defaultTabTitle, JComponent defaultTabComponent) {
        this(defaultTabTitle, defaultTabComponent, ComicPalette.defaultPalette());
    }

    public ChatSidebarPanel(String defaultTabTitle, JComponent defaultTabComponent, ComicPalette palette) {
        super(new BorderLayout());
        if (defaultTabTitle == null || defaultTabComponent == null || palette == null) {
            throw new IllegalArgumentException("defaultTabTitle, defaultTabComponent and palette must not be null");
        }
        this.defaultTabTitle = defaultTabTitle;
        this.defaultTabComponent = defaultTabComponent;
        this.activeTitle = defaultTabTitle;
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, palette.getSurface().darker()));
        panes.setOpaque(false);
        add(buildHeader(), BorderLayout.NORTH);
        add(panes, BorderLayout.CENTER);
        rebuildTabs();
        setPreferredSize(new Dimension(360, 10));
    }

    /** Beiträge kommen über diesen Lieferanten; er wird bei jedem Öffnen des Drawers neu gelesen. */
    public void setExtraTabsSupplier(Supplier<List<ChatSidebarTab>> supplier) {
        this.extraTabsSupplier = supplier;
    }

    /** Die Seitentitel in Reihenfolge — Standardseite zuerst, dann die Beiträge. */
    public List<String> tabTitles() {
        return Collections.unmodifiableList(new ArrayList<String>(titles));
    }

    /** Der Titel der sichtbaren Seite. */
    public String activeTab() {
        return activeTitle;
    }

    /** Zeigt die Seite mit diesem Titel (unbekannte Titel werden ignoriert). */
    public void showTab(String title) {
        if (title == null || !titles.contains(title)) {
            return;
        }
        activeTitle = title;
        paneLayout.show(panes, title);
        if (header != null) {
            header.setVisible(defaultTabTitle.equals(title));
        }
    }

    /** Ob die Kopfzeile (Chat-Suche) gerade sichtbar ist. */
    boolean isHeaderVisible() {
        return header != null && header.isVisible();
    }

    /** Der Platz in der Kopfzeile, volle Breite, z. B. für die Suchleiste. */
    public void setHeaderComponent(JComponent component) {
        headerLeft.removeAll();
        if (component != null) {
            headerLeft.add(component, BorderLayout.CENTER);
        }
        headerLeft.revalidate();
        headerLeft.repaint();
    }

    /** Baut die Seiten neu auf: Standardseite zuerst, dann die aktuellen Beiträge. */
    public void rebuildTabs() {
        panes.removeAll();
        titles.clear();
        Map<String, JComponent> byTitle = new LinkedHashMap<String, JComponent>();
        byTitle.put(defaultTabTitle, defaultTabComponent);
        List<ChatSidebarTab> extras = extraTabsSupplier == null
                ? Collections.<ChatSidebarTab>emptyList() : extraTabsSupplier.get();
        if (extras != null) {
            for (ChatSidebarTab tab : extras) {
                if (tab != null && tab.getTitle() != null && !byTitle.containsKey(tab.getTitle())) {
                    byTitle.put(tab.getTitle(), tab.getComponent());
                }
            }
        }
        for (Map.Entry<String, JComponent> entry : byTitle.entrySet()) {
            titles.add(entry.getKey());
            panes.add(entry.getValue(), entry.getKey());
        }
        if (!titles.contains(activeTitle)) {
            activeTitle = defaultTabTitle;
        }
        showTab(activeTitle);
        panes.revalidate();
        panes.repaint();
    }

    private JComponent buildHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(ResearchUiMetrics.SLIM_BAR_TOP_GAP, 8, 4, 4));
        headerLeft.setOpaque(false);
        headerPanel.add(headerLeft, BorderLayout.CENTER);
        this.header = headerPanel;
        return headerPanel;
    }
}
