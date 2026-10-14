package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.document.ExportFormat;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.SidebarIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.TransferHandler;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The drawer page "Navigation" (tab with the red compass, issue #97): the page tree of the source of the open page
 * (Confluence: its space as the only root), the outgoing and the incoming references of the page as three sections
 * the user shows, hides and orders with the buttons in the drawer footer and sizes with the dividers between them
 * (#114, {@link NavigationSectionLayout}); the arrangement goes to the {@link #setSectionLayoutListener listener}, never
 * into a file of its own. The tree looks like the favorites
 * tree ({@link FavoritesPanel}) but only reads: no drag and drop, no renaming, no context menu. A click on a title shows
 * the page in the reader (double click pins it), a click on the expander only opens or closes the branch; a branch
 * loads its children when it is first opened, batch by batch ({@link Actions#childrenRequested}).
 *
 * <p>The panel only displays; the controller decides what to load and tells it via {@link #showRoot},
 * {@link #addChildren}, {@link #reveal} and {@link #showReferences}. All calls on the EDT.
 */
public final class PageNavigationPanel extends JPanel {

    /** What the page triggers; calls on the EDT. */
    public interface Actions {
        void pageOpened(ReaderLink page, boolean pin);

        /** The branch {@code key} was opened for the first time and needs its children. */
        void childrenRequested(String key);

        /** The page became visible (tab chosen, drawer opened). */
        void shown();

        /** Export the page {@code key} of the tree with all pages below it (#115). */
        default void exportRequested(String key, ExportFormat format) {
        }
    }

    /** A row of the tree or of the references: key, title, what a click opens and whether it can have children. */
    public static final class Item {
        private final String key;
        private final String title;
        private final ReaderLink link;
        private final boolean expandable;
        private final boolean root;

        private Item(String key, String title, ReaderLink link, boolean expandable, boolean root) {
            if (key == null || key.isEmpty() || link == null) {
                throw new IllegalArgumentException("key and link are required");
            }
            this.key = key;
            this.title = title == null || title.trim().isEmpty() ? key : title.trim();
            this.link = link;
            this.expandable = expandable;
            this.root = root;
        }

        /** The root of the tree (Confluence: the space). */
        public static Item root(String key, String title, ReaderLink link) {
            return new Item(key, title, link, true, true);
        }

        /** A page; {@code expandable} if it has or may have children. */
        public static Item page(String key, String title, ReaderLink link, boolean expandable) {
            return new Item(key, title, link, expandable, false);
        }

        public String key() {
            return key;
        }

        public String title() {
            return title;
        }

        public ReaderLink link() {
            return link;
        }

        public boolean expandable() {
            return expandable;
        }

        /** Whether this is a root (Confluence: a space) rather than a page. */
        public boolean isRoot() {
            return root;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    static final String TREE_HEADER = "SEITENBAUM";
    static final String OUTGOING_HEADER = "AUSGEHENDE VERWEISE";
    static final String INCOMING_HEADER = "EINGEHENDE VERWEISE";
    static final String NO_SECTION = "Alle Bereiche sind ausgeblendet. Die Knöpfe unten blenden Seitenbaum und Verweise "
            + "wieder ein.";
    static final String TREE_TOGGLE = "Seitenbaum ein- oder ausblenden (ziehen zum Anordnen)";
    static final String OUTGOING_TOGGLE = "Ausgehende Verweise ein- oder ausblenden (ziehen zum Anordnen)";
    static final String INCOMING_TOGGLE = "Eingehende Verweise ein- oder ausblenden (ziehen zum Anordnen)";
    public static final String NOTHING_OPEN = "Keine Seite geöffnet. Öffne eine Seite im Lesebereich, dann zeigt der Kompass "
            + "ihren Platz in der Quelle und ihre Verweise.";
    public static final String LOADING = "Lädt …";
    static final String LOADING_MORE = "Weitere Seiten werden geladen …";
    static final String MENU_EXPORT = "Exportieren";
    static final String OUTGOING = "Verweist auf";
    static final String INCOMING = "Wird verlinkt von";
    static final String NO_OUTGOING = "Keine Verweise auf andere Seiten.";
    static final String INCOMING_UNSUPPORTED = "Eingehende Verweise kann diese Quelle nicht zuverlässig ermitteln.";
    /** How far a footer button moves before it is dragged instead of clicked. */
    private static final int DRAG_THRESHOLD_PX = 5;
    private static final String CARD_TREE = "tree";
    private static final String CARD_MESSAGE = "message";
    private static final Color SELECTION_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.86f);

    private final ComicPalette palette;
    private final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode(null);
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    private final JTree tree = new JTree(treeModel);
    private final ReferenceSection outgoing = new ReferenceSection();
    private final ReferenceSection incoming = new ReferenceSection();
    private final CardLayout treeCards = new CardLayout();
    private final JPanel treeArea = new JPanel(treeCards);
    private final JTextArea treeMessage = messageArea();
    private final NavigationSectionStack stack;
    private final JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final Map<String, ComposerButton> toggleButtons = new LinkedHashMap<String, ComposerButton>();
    private SectionLayoutListener sectionLayoutListener;
    private final Map<String, DefaultMutableTreeNode> nodes = new HashMap<String, DefaultMutableTreeNode>();
    /** Branches whose children are complete. */
    private final Set<String> loaded = new HashSet<String>();
    /** Branches whose children were asked for (and are not complete yet). */
    private final Set<String> requested = new HashSet<String>();
    private String rootKey;
    private String currentKey;
    private Actions actions;
    private boolean revealing;

    /** Gets every arrangement of the sections the user chose (toggle, drop, divider). */
    public interface SectionLayoutListener {
        void sectionLayoutChanged(NavigationSectionLayout layout);
    }

    public PageNavigationPanel(ComicPalette palette) {
        super(new BorderLayout());
        this.palette = palette;
        setOpaque(false);

        configure(tree);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                menuGesture(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                menuGesture(event); // Windows reports the context menu on release
            }
        });
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                Item item = itemOf(event.getPath().getLastPathComponent());
                if (item != null && !revealing) {
                    request(item.key());
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        configure(outgoing.tree);
        configure(incoming.tree);

        treeArea.setOpaque(false);
        treeArea.add(scroll(tree), CARD_TREE);
        treeArea.add(treeMessage, CARD_MESSAGE);
        outgoing.area.add(scroll(outgoing.tree), CARD_TREE);
        incoming.area.add(scroll(incoming.tree), CARD_TREE);

        Map<String, JComponent> sections = new LinkedHashMap<String, JComponent>();
        sections.put(NavigationSectionLayout.TREE, section(TREE_HEADER, treeArea));
        sections.put(NavigationSectionLayout.OUTGOING, section(OUTGOING_HEADER, outgoing.area));
        sections.put(NavigationSectionLayout.INCOMING, section(INCOMING_HEADER, incoming.area));
        JTextArea noSection = messageArea();
        noSection.setText(NO_SECTION);
        stack = new NavigationSectionStack(sections, noSection);
        stack.setListener(this::applyRequestedLayout);
        add(stack, BorderLayout.CENTER);
        buildToggles();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && actions != null) {
                actions.shown();
            }
        });
        showMessage(NOTHING_OPEN);
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    // ------------------------------------------------------------------ Bereiche (#114)

    /** The current arrangement of the sections. */
    public NavigationSectionLayout sectionLayout() {
        return stack.sectionLayout();
    }

    /** Show {@code layout} (e.g. the stored one) without telling the listener. */
    public void setSectionLayout(NavigationSectionLayout layout) {
        stack.setSectionLayout(layout);
        refreshToggles();
    }

    public void setSectionLayoutListener(SectionLayoutListener listener) {
        this.sectionLayoutListener = listener;
    }

    /** The footer of the tab: one toggle per section, also the handle to drag it into place. */
    public JComponent sectionToggles() {
        return toggles;
    }

    /** The toggle of section {@code id} (for tests). */
    ComposerButton sectionToggle(String id) {
        return toggleButtons.get(id);
    }

    /** The stack of the sections (for tests). */
    NavigationSectionStack sectionStack() {
        return stack;
    }

    /** Hide a visible section or show a hidden one, like a click on its footer button. */
    public void toggleSection(String id) {
        applyRequestedLayout(stack.sectionLayout().toggle(id));
    }

    private void applyRequestedLayout(NavigationSectionLayout layout) {
        stack.setSectionLayout(layout);
        refreshToggles();
        if (sectionLayoutListener != null) {
            sectionLayoutListener.sectionLayoutChanged(layout);
        }
    }

    private void buildToggles() {
        toggles.setOpaque(false);
        addToggle(NavigationSectionLayout.TREE, SidebarIcons.pageTree(), TREE_TOGGLE);
        addToggle(NavigationSectionLayout.OUTGOING, SidebarIcons.linksOutgoing(), OUTGOING_TOGGLE);
        addToggle(NavigationSectionLayout.INCOMING, SidebarIcons.linksIncoming(), INCOMING_TOGGLE);
        refreshToggles();
    }

    private void addToggle(final String id, Icon icon, String tooltip) {
        final ComposerButton button = ComposerButton.ribbonIconEntry(icon, tooltip, true);
        button.addActionListener(event -> toggleSection(id));
        // The button is also the handle: dragging it into the sections puts this section there.
        button.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent component) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent component) {
                return NavigationSectionStack.transferable(id);
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                stack.clearDropMark();
            }
        });
        MouseAdapter drag = new MouseAdapter() {
            private Point pressed;

            @Override
            public void mousePressed(MouseEvent event) {
                pressed = event.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressed != null && pressed.distance(event.getPoint()) > DRAG_THRESHOLD_PX) {
                    pressed = null;
                    button.getModel().setArmed(false);
                    button.getModel().setPressed(false);
                    button.getTransferHandler().exportAsDrag(button, event, TransferHandler.MOVE);
                }
            }
        };
        button.addMouseListener(drag);
        button.addMouseMotionListener(drag);
        toggleButtons.put(id, button);
        toggles.add(button);
    }

    private void refreshToggles() {
        NavigationSectionLayout layout = stack.sectionLayout();
        for (Map.Entry<String, ComposerButton> entry : toggleButtons.entrySet()) {
            entry.getValue().setEmphasized(layout.isVisible(entry.getKey()));
        }
    }

    // ------------------------------------------------------------------ Baum

    /** Show only a hint instead of tree and references (no page open, source not navigable). */
    public void showMessage(String text) {
        showTreeMessage(text);
        showReferencesMessage("");
    }

    /** Show a hint instead of the tree; the references stay as they are. */
    public void showTreeMessage(String text) {
        clearTree();
        treeMessage.setText(text == null ? "" : text);
        treeCards.show(treeArea, CARD_MESSAGE);
    }

    /** Start a new tree with {@code root} as its only top node; its children follow via {@link #addChildren}. */
    public void showRoot(Item root) {
        clearTree();
        DefaultMutableTreeNode node = node(root);
        treeModel.insertNodeInto(node, treeRoot, 0);
        rootKey = root.key();
        treeCards.show(treeArea, CARD_TREE);
    }

    /** The key of the root shown, {@code null} without tree. */
    public String rootKey() {
        return rootKey;
    }

    /** Whether the children of {@code key} are complete. */
    public boolean isLoaded(String key) {
        return loaded.contains(key);
    }

    /** Whether {@code key} is a node of the tree. */
    public boolean contains(String key) {
        return nodes.containsKey(key);
    }

    /** Mark {@code key} as asked for its children so an expansion does not ask again. */
    public void markRequested(String key) {
        if (nodes.containsKey(key) && !loaded.contains(key)) {
            requested.add(key);
            placeholder(nodes.get(key), LOADING);
        }
    }

    /**
     * Append one batch of children to {@code parentKey}; children the tree already has stay as they are (with their
     * branches). {@code complete} ends the loading of this branch.
     */
    public void addChildren(String parentKey, List<Item> children, boolean complete) {
        DefaultMutableTreeNode parent = nodes.get(parentKey);
        if (parent == null || loaded.contains(parentKey)) {
            return;
        }
        removePlaceholder(parent);
        for (Item child : children) {
            if (!nodes.containsKey(child.key())) {
                treeModel.insertNodeInto(node(child), parent, parent.getChildCount());
            }
        }
        if (complete) {
            loaded.add(parentKey);
            requested.remove(parentKey);
            if (parent.getChildCount() == 0) {
                treeModel.nodeStructureChanged(parent); // no expander any more
            }
        } else {
            placeholder(parent, LOADING_MORE);
        }
    }

    /** The children of {@code parentKey} could not be loaded; the text takes their place, a new opening retries. */
    public void childrenFailed(String parentKey, String text) {
        DefaultMutableTreeNode parent = nodes.get(parentKey);
        requested.remove(parentKey);
        if (parent != null) {
            placeholder(parent, text);
        }
    }

    /**
     * Open the branches along {@code path} (keys from the top down), select its last node as the current page and scroll
     * it into view; other branches keep their state. Nodes missing from the tree end the path there.
     */
    public void reveal(List<String> path) {
        currentKey = path.isEmpty() ? null : path.get(path.size() - 1);
        TreePath deepest = null;
        revealing = true;
        try {
            for (int i = 0; i < path.size(); i++) {
                DefaultMutableTreeNode node = nodes.get(path.get(i));
                if (node == null) {
                    break;
                }
                deepest = new TreePath(node.getPath());
                if (i < path.size() - 1) {
                    tree.expandPath(deepest);
                }
            }
        } finally {
            revealing = false;
        }
        if (deepest != null) {
            tree.setSelectionPath(deepest);
            tree.scrollPathToVisible(deepest);
        } else {
            tree.clearSelection();
        }
        tree.repaint();
    }

    /** The key of the highlighted page, {@code null} without one. */
    public String currentKey() {
        return currentKey;
    }

    public JTree tree() {
        return tree;
    }

    private void clearTree() {
        treeRoot.removeAllChildren();
        treeModel.reload();
        nodes.clear();
        loaded.clear();
        requested.clear();
        rootKey = null;
        currentKey = null;
    }

    private DefaultMutableTreeNode node(Item item) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
        nodes.put(item.key(), node);
        if (item.expandable()) {
            node.add(new DefaultMutableTreeNode(LOADING)); // expander until the children are known
        } else {
            loaded.add(item.key());
        }
        return node;
    }

    private void request(String key) {
        if (loaded.contains(key) || requested.contains(key)) {
            return;
        }
        requested.add(key);
        placeholder(nodes.get(key), LOADING);
        if (actions != null) {
            actions.childrenRequested(key);
        }
    }

    /** Put {@code text} as the last child (replacing an earlier placeholder). */
    private void placeholder(DefaultMutableTreeNode parent, String text) {
        if (parent == null) {
            return;
        }
        removePlaceholder(parent);
        treeModel.insertNodeInto(new DefaultMutableTreeNode(text), parent, parent.getChildCount());
    }

    private void removePlaceholder(DefaultMutableTreeNode parent) {
        int last = parent.getChildCount() - 1;
        if (last >= 0 && !(((DefaultMutableTreeNode) parent.getChildAt(last)).getUserObject() instanceof Item)) {
            treeModel.removeNodeFromParent((DefaultMutableTreeNode) parent.getChildAt(last));
        }
    }

    // ------------------------------------------------------------------ Querverweise

    /** Show that the references are being loaded. */
    public void showReferencesLoading() {
        showReferencesMessage(LOADING);
    }

    /** Show a hint instead of the references in both reference sections (empty: nothing). */
    public void showReferencesMessage(String text) {
        outgoing.showMessage(text);
        incoming.showMessage(text);
    }

    /**
     * Show the references split by direction into their sections; {@code incomingPages == null} means the source
     * cannot determine them reliably, which the section says instead of pretending there are none.
     */
    public void showReferences(List<Item> outgoingPages, List<Item> incomingPages) {
        outgoing.show(group(OUTGOING, outgoingPages, NO_OUTGOING));
        incoming.show(incomingPages == null ? group(INCOMING, Collections.<Item>emptyList(), INCOMING_UNSUPPORTED)
                : group(INCOMING, incomingPages, ""));
    }

    /** The tree of the outgoing references. */
    public JTree outgoingReferences() {
        return outgoing.tree;
    }

    /** The tree of the incoming references. */
    public JTree incomingReferences() {
        return incoming.tree;
    }

    /** One reference section: a tree with one group, or a hint instead. */
    private final class ReferenceSection {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
        final DefaultTreeModel model = new DefaultTreeModel(root);
        final JTree tree = new JTree(model);
        final CardLayout cards = new CardLayout();
        final JPanel area = new JPanel(cards);
        final JTextArea message = messageArea();

        ReferenceSection() {
            tree.setRootVisible(false);
            tree.setShowsRootHandles(false);
            area.setOpaque(false);
            area.add(message, CARD_MESSAGE);
        }

        void showMessage(String text) {
            root.removeAllChildren();
            model.reload();
            message.setText(text == null ? "" : text);
            cards.show(area, CARD_MESSAGE);
        }

        void show(DefaultMutableTreeNode group) {
            root.removeAllChildren();
            root.add(group);
            model.reload();
            for (int row = 0; row < tree.getRowCount(); row++) {
                tree.expandRow(row);
            }
            cards.show(area, CARD_TREE);
        }
    }

    private static DefaultMutableTreeNode group(String title, List<Item> items, String emptyText) {
        DefaultMutableTreeNode group = new DefaultMutableTreeNode(new Group(title, items.size()));
        for (Item item : items) {
            group.add(new DefaultMutableTreeNode(item, false));
        }
        if (items.isEmpty() && !emptyText.isEmpty()) {
            group.add(new DefaultMutableTreeNode(emptyText, false));
        }
        return group;
    }

    /** A group heading of the references with the number of entries. */
    private static final class Group {
        final String title;
        final int count;

        Group(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    // ------------------------------------------------------------------ Kontextmenü (#115)

    /** A right click selects the row of the page tree and opens its menu; it never opens the page. */
    private void menuGesture(MouseEvent event) {
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return;
        }
        if (SwingUtilities.isRightMouseButton(event)) {
            tree.setSelectionPath(path);
        }
        Item item = itemOf(path.getLastPathComponent());
        if (event.isPopupTrigger() && item != null) {
            JPopupMenu menu = treeMenu(item);
            if (menu != null) {
                menu.show(tree, event.getX(), event.getY());
            }
        }
    }

    /** The context menu of a row of the page tree; {@code null} for the root, which is no page. */
    JPopupMenu treeMenu(final Item item) {
        if (item.isRoot()) {
            return null;
        }
        JMenu export = new JMenu(MENU_EXPORT);
        for (final ExportFormat format : ExportFormat.values()) {
            JMenuItem entry = new JMenuItem(format.menuLabel());
            entry.addActionListener(event -> {
                if (actions != null) {
                    actions.exportRequested(item.key(), format);
                }
            });
            export.add(entry);
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(export);
        return menu;
    }

    // ------------------------------------------------------------------ Aufbau

    private void configure(final JTree target) {
        target.setRowHeight(0);
        target.setOpaque(true);
        target.setBackground(palette.getSurface());
        target.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        target.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        target.setCellRenderer(new Renderer());
        target.setToggleClickCount(0); // a double click pins the page, it does not fold the branch
        target.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                // getPathForLocation covers the row, not the expander: the expander only folds.
                TreePath path = target.getPathForLocation(event.getX(), event.getY());
                Item item = path == null ? null : itemOf(path.getLastPathComponent());
                if (item != null && actions != null) {
                    actions.pageOpened(item.link(), event.getClickCount() >= 2);
                }
            }
        });
    }

    private JScrollPane scroll(JTree target) {
        JScrollPane scroll = new ComicScrollPane(target, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JPanel section(String title, JComponent body) {
        JLabel header = new JLabel(title);
        header.setFont(ResearchUiTypography.semiBold(11f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 8));
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.add(header, BorderLayout.NORTH);
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    private static JTextArea messageArea() {
        JTextArea message = new JTextArea();
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setHighlighter(null);
        message.setFont(ResearchUiTypography.regular(12f));
        message.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        message.setBorder(BorderFactory.createEmptyBorder(2, 10, 6, 8));
        return message;
    }

    private static Item itemOf(Object value) {
        Object user = value instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) value).getUserObject() : null;
        return user instanceof Item ? (Item) user : null;
    }

    /** Like the favorites: symbol and title, a soft blue wash under the selection; the current page in bold. */
    private final class Renderer extends JPanel implements TreeCellRenderer {
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final Icon rootIcon = SidebarIcons.compass();
        private final Icon folderIcon = SidebarIcons.folder();
        private final Icon pageIcon = SidebarIcons.page();
        private boolean selected;

        Renderer() {
            super(new BorderLayout(6, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
            add(icon, BorderLayout.WEST);
            add(title, BorderLayout.CENTER);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean isSelected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            Object user = value instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) value).getUserObject()
                    : null;
            selected = isSelected && user instanceof Item;
            title.setForeground(palette.getInk());
            if (user instanceof Item) {
                Item item = (Item) user;
                title.setText(item.title());
                boolean current = item.key().equals(currentKey) && owner == tree;
                title.setFont(item.root || current ? ResearchUiTypography.semiBold(12.5f)
                        : ResearchUiTypography.regular(12.5f));
                icon.setIcon(item.root ? rootIcon : owner == tree && item.expandable()
                        && !(loaded.contains(item.key()) && leaf) ? folderIcon : pageIcon);
            } else if (user instanceof Group) {
                Group group = (Group) user;
                title.setText(group.title + (group.count > 0 ? " (" + group.count + ")" : ""));
                title.setFont(ResearchUiTypography.semiBold(12f));
                title.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
                icon.setIcon(null);
            } else {
                title.setText(user == null ? "" : user.toString());
                title.setFont(ResearchUiTypography.regular(11.5f));
                title.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
                icon.setIcon(null);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (selected) {
                Graphics2D g2 = ResearchUiPainter.prepare(graphics);
                try {
                    ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(),
                            ResearchUiMetrics.CHAT_ROW_RADIUS, SELECTION_WASH);
                } finally {
                    g2.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }

    /**
     * The rows of the references as shown: group headings with their count, entries by title, hints as text; the hint
     * alone while no references show.
     */
    public List<String> referenceRows() {
        List<String> rows = new ArrayList<String>();
        if (outgoing.root.getChildCount() == 0) {
            rows.add(outgoing.message.getText());
            return rows;
        }
        List<DefaultMutableTreeNode> groups = new ArrayList<DefaultMutableTreeNode>();
        for (ReferenceSection section : java.util.Arrays.asList(outgoing, incoming)) {
            for (int g = 0; g < section.root.getChildCount(); g++) {
                groups.add((DefaultMutableTreeNode) section.root.getChildAt(g));
            }
        }
        for (DefaultMutableTreeNode group : groups) {
            Group heading = (Group) group.getUserObject();
            rows.add(heading.title + (heading.count > 0 ? " (" + heading.count + ")" : ""));
            for (int i = 0; i < group.getChildCount(); i++) {
                rows.add("  " + ((DefaultMutableTreeNode) group.getChildAt(i)).getUserObject());
            }
        }
        return rows;
    }

    /** The text shown instead of the tree; empty while the tree shows. */
    public String treeMessage() {
        return rootKey == null ? treeMessage.getText() : "";
    }

    /** The keys of the children shown under {@code key}, in order. */
    public List<String> childKeys(String key) {
        List<String> keys = new ArrayList<String>();
        DefaultMutableTreeNode node = nodes.get(key);
        for (int i = 0; node != null && i < node.getChildCount(); i++) {
            Item item = itemOf(node.getChildAt(i));
            if (item != null) {
                keys.add(item.key());
            }
        }
        return keys;
    }
}
