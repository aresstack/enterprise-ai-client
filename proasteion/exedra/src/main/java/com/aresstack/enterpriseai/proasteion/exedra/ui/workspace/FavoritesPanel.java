package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComicSearchBar;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.paint.SidebarIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Die Drawer-Seite „Favoriten“ (Reiter mit dem gelben Stern) als Ordnerbaum wie die Lesezeichen in MainframeMate
 * ({@code LeftDrawer} + {@code BookmarkTreeTransferHandler}): Ordner mit Unterordnern, Favoriten mit Titel und Adresse.
 * Ein Klick zeigt einen Favoriten im Lesebereich (Doppelklick heftet an). Ein Stern im Lesebereich oder an einem
 * Treffer legt den Favoriten in den hier selektierten Ordner, bei selektiertem Favoriten in dessen Ordner, ohne
 * Auswahl in die oberste Ebene ({@link #targetFolderId()}); danach ist der neue Favorit selektiert, der nächste landet
 * also daneben. Ordner anlegen, umbenennen und entfernen über das Kontextmenü (Rechtsklick; auf freier Fläche: neuer
 * Ordner oben), Entf entfernt die Auswahl; ein Ordner mit Inhalt nur nach Rückfrage. Ordner und Favoriten lassen sich
 * per Drag and Drop in einen Ordner, zwischen Zeilen oder auf die freie Fläche (oberste Ebene, ans Ende) ziehen; ein
 * Ordner nie in sich selbst. Strg+Klick nimmt einzelne Einträge zur Auswahl hinzu oder heraus, Umschalt+Klick wählt
 * einen Bereich; wer einen ausgewählten Eintrag zieht, verschiebt die ganze Auswahl als Block in Baumreihenfolge,
 * wer einen anderen zieht, nur diesen (#112). Die Suchleiste filtert nach Titel, Adresse und Ordnername; solange gefiltert wird, ist
 * Ziehen aus (die Positionen entsprächen nicht dem Baum). Ein Klick auf die freie Fläche hebt die Auswahl auf.
 */
public final class FavoritesPanel extends JPanel {

    /** Was die Seite auslöst; Aufrufe auf dem EDT. */
    public interface Actions {
        void favoriteOpened(ReaderLink favorite, boolean pin);

        /** Favorit oder Ordner samt Inhalt entfernen (Rückfrage ist schon erfolgt). */
        void entryRemoveRequested(String id);

        /** Neuer Ordner in {@code parentId} ({@code null} = oberste Ebene); die ID des neuen Ordners. */
        String folderCreateRequested(String parentId, String name);

        void folderRenameRequested(String id, String name);

        /**
         * {@code ids} als Block in Baumreihenfolge nach {@code targetFolderId} ({@code null} = oberste Ebene) an
         * Position {@code index} der Liste vor dem Herausnehmen ({@code -1} = ans Ende); {@code false} = abgelehnt.
         */
        boolean moveRequested(List<String> ids, String targetFolderId, int index);
    }

    static final String EMPTY_TEXT = "Noch keine Favoriten. Der Stern im Lesebereich oder an einem Treffer merkt eine "
            + "Quelle oder ein Dokument; Rechtsklick legt Ordner an.";
    static final String SEARCH_PLACEHOLDER = "Favoriten durchsuchen…";
    static final String SEARCH_TOOLTIP = "Favoriten nach Titel, Adresse und Ordner filtern";
    static final String NO_MATCH_TEXT = "Keine passenden Favoriten";
    static final String NEW_FOLDER = "Neuer Ordner";
    static final String NEW_SUBFOLDER = "Neuer Unterordner";
    static final String RENAME = "Umbenennen";
    static final String REMOVE = "Entfernen";
    static final String REMOVE_TOOLTIP = "Aus den Favoriten entfernen";
    /** Breite der Fläche mit ✕ am rechten Rand jeder Zeile. */
    private static final int REMOVE_WIDTH = 22;
    static final String FOLDER_NAME_PROMPT = "Name des neuen Ordners:";

    private static final DataFlavor ENTRY_FLAVOR = new DataFlavor(List.class, "Favoriten");
    private static final Color SELECTION_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.86f);

    private final ComicPalette palette;
    private final ComicSearchBar searchBar;
    private final JTextArea message = new JTextArea();
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    private final DefaultTreeModel model = new DefaultTreeModel(rootNode);
    private final JTree tree = new FillingTree(model);
    /** Zugeklappte Ordner (alle anderen sind offen, auch neue). */
    private final Set<String> collapsed = new HashSet<String>();
    private List<FavoriteEntry> entries = Collections.emptyList();
    private Actions actions;
    private boolean rebuilding;
    private String selectedId;
    /** All selected ids in row order, restored after a rebuild. */
    private List<String> selectedIds = Collections.emptyList();
    /** Ordner des selektierten Knotens ({@code null} = oberste Ebene), falls der Knoten verschwindet. */
    private String selectedParentId;
    private List<FavoriteEntry> dragged;

    public FavoritesPanel(ComicPalette palette) {
        super(new BorderLayout());
        this.palette = palette;
        setOpaque(false);
        searchBar = new ComicSearchBar(SEARCH_PLACEHOLDER, SEARCH_TOOLTIP, palette);
        searchBar.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });
        searchBar.getTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchBar.setText("");
                }
            }
        });
        JPanel searchRow = new JPanel(new BorderLayout());
        searchRow.setOpaque(false);
        searchRow.setBorder(BorderFactory.createEmptyBorder(ResearchUiMetrics.SLIM_BAR_TOP_GAP, 8, 4, 4));
        searchRow.add(searchBar, BorderLayout.CENTER);

        JLabel header = new JLabel("FAVORITEN");
        header.setFont(ResearchUiTypography.semiBold(11f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 8));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setHighlighter(null);
        message.setFont(ResearchUiTypography.regular(12f));
        message.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        message.setBorder(BorderFactory.createEmptyBorder(2, 10, 6, 8));
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        header.setAlignmentX(LEFT_ALIGNMENT);
        message.setAlignmentX(LEFT_ALIGNMENT);
        north.add(searchRow);
        north.add(header);
        north.add(message);
        add(north, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0); // Höhe je Zeile aus dem Renderer (Favoriten zweizeilig)
        tree.setOpaque(true);
        tree.setBackground(palette.getSurface());
        tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setCellRenderer(new EntryRenderer());
        tree.setToggleClickCount(2);
        setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new EntryTransferHandler());
        tree.addTreeSelectionListener(event -> {
            if (!rebuilding) {
                remember(selectedEntryPath());
                selectedIds = selectedIdsInRowOrder();
            }
        });
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                FavoriteEntry entry = entryOf(event.getPath().getLastPathComponent());
                if (!rebuilding && !isFiltering() && entry != null) {
                    collapsed.remove(entry.id());
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                FavoriteEntry entry = entryOf(event.getPath().getLastPathComponent());
                if (!rebuilding && !isFiltering() && entry != null) {
                    collapsed.add(entry.id());
                }
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int row = tree.getRowForLocation(event.getX(), event.getY());
                if (row < 0) {
                    tree.clearSelection(); // freie Fläche: Ziel ist wieder die oberste Ebene
                    remember(null);
                } else if (SwingUtilities.isRightMouseButton(event) && !tree.isRowSelected(row)) {
                    tree.setSelectionRow(row);
                }
                if (event.isPopupTrigger()) {
                    showMenu(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showMenu(event); // Windows meldet das Kontextmenü beim Loslassen
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                FavoriteEntry entry = entryAt(event.getX(), event.getY());
                if (entry != null && onRemoveMark(event.getX(), event.getY())) {
                    if (event.getClickCount() == 1) {
                        removeWithConfirmation(entry); // ✕ am Zeilenende wie bisher
                    }
                    return;
                }
                if (entry != null && !entry.isFolder() && actions != null && !isSelectionGesture(event)) {
                    actions.favoriteOpened(entry.link(), event.getClickCount() >= 2);
                }
            }
        });
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeFavorite");
        tree.getActionMap().put("removeFavorite", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                FavoriteEntry entry = selectedEntry();
                if (entry != null) {
                    removeWithConfirmation(entry);
                }
            }
        });

        JScrollPane scroll = new ComicScrollPane(tree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        scroll.getViewport().setBackground(palette.getSurface());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        rebuild();
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    /** Der Baum unterhalb der Wurzel, wie die Ablage ihn liefert. */
    public void setFavorites(List<FavoriteEntry> values) {
        this.entries = values == null ? Collections.<FavoriteEntry>emptyList()
                : new ArrayList<FavoriteEntry>(values);
        rebuild();
    }

    public List<FavoriteEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public ComicSearchBar searchBar() {
        return searchBar;
    }

    public JTree tree() {
        return tree;
    }

    /**
     * Wohin ein neuer Favorit gehört (MainframeMate {@code resolveTargetBookmarkFolderLabel}): der selektierte Ordner,
     * bei einem selektierten Favoriten dessen Ordner, ohne Auswahl die oberste Ebene ({@code null}).
     */
    public String targetFolderId() {
        if (selectedId == null) {
            return null;
        }
        List<FavoriteEntry> path = pathTo(entries, selectedId);
        if (path.isEmpty()) {
            return null;
        }
        FavoriteEntry selected = path.get(path.size() - 1);
        if (selected.isFolder()) {
            return selected.id();
        }
        return path.size() >= 2 ? path.get(path.size() - 2).id() : null;
    }

    /** Selektiert den Knoten {@code id} (z. B. den gerade angelegten Favoriten) und zeigt ihn. */
    public void select(String id) {
        TreePath path = treePath(id);
        if (path != null) {
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    // ------------------------------------------------------------------ Aufbau

    private boolean isFiltering() {
        return !searchBar.getText().trim().isEmpty();
    }

    private void rebuild() {
        rebuilding = true;
        try {
            String filter = searchBar.getText().trim().toLowerCase(Locale.ROOT);
            rootNode.removeAllChildren();
            for (FavoriteEntry entry : entries) {
                DefaultMutableTreeNode node = node(entry, filter);
                if (node != null) {
                    rootNode.add(node);
                }
            }
            model.reload();
            expand(rootNode, !filter.isEmpty());
            boolean empty = entries.isEmpty();
            boolean none = rootNode.getChildCount() == 0;
            message.setText(none ? (empty ? EMPTY_TEXT : NO_MATCH_TEXT) : "");
            message.setVisible(none);
            setDragEnabled(filter.isEmpty());
            restoreSelection();
        } finally {
            rebuilding = false;
        }
        revalidate();
        repaint();
    }

    /** Der Teilbaum; beim Filtern nur Treffer und die Ordner auf dem Weg (ein passender Ordner mit allem Inhalt). */
    private static DefaultMutableTreeNode node(FavoriteEntry entry, String filter) {
        boolean matches = filter.isEmpty() || matches(entry, filter);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
        if (entry.isFolder()) {
            for (FavoriteEntry child : entry.children()) {
                DefaultMutableTreeNode childNode = node(child, matches ? "" : filter);
                if (childNode != null) {
                    node.add(childNode);
                }
            }
            return matches || node.getChildCount() > 0 ? node : null;
        }
        return matches ? node : null;
    }

    private static boolean matches(FavoriteEntry entry, String filter) {
        if (entry.name().toLowerCase(Locale.ROOT).contains(filter)) {
            return true;
        }
        return !entry.isFolder() && entry.link().location().toLowerCase(Locale.ROOT).contains(filter);
    }

    private void expand(DefaultMutableTreeNode parent, boolean all) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            FavoriteEntry entry = entryOf(child);
            if (entry != null && entry.isFolder() && (all || !collapsed.contains(entry.id()))) {
                tree.expandPath(new TreePath(child.getPath()));
                expand(child, all);
            }
        }
    }

    /** Nach dem Neuaufbau: dieselbe Auswahl, sonst ihr Ordner (z. B. nach dem Entfernen), sonst keine. */
    private void restoreSelection() {
        TreePath path = treePath(selectedId);
        if (path == null && selectedParentId != null) {
            path = treePath(selectedParentId);
            if (path != null) {
                selectedId = selectedParentId;
                List<FavoriteEntry> parents = pathTo(entries, selectedId);
                selectedParentId = parents.size() >= 2 ? parents.get(parents.size() - 2).id() : null;
            }
        }
        if (path == null) {
            tree.clearSelection();
            if (pathTo(entries, selectedId).isEmpty()) {
                selectedId = null;
                selectedParentId = null;
            }
            selectedIds = Collections.emptyList();
            return;
        }
        List<TreePath> paths = new ArrayList<TreePath>();
        for (String id : selectedIds) {
            TreePath other = treePath(id);
            if (other != null && !other.equals(path)) {
                paths.add(other);
            }
        }
        paths.add(path); // the lead (target of the next star) stays last
        tree.setSelectionPaths(paths.toArray(new TreePath[0]));
        tree.setLeadSelectionPath(path);
    }

    /** Ctrl or Shift change only the selection; they never open a favorite. */
    private static boolean isSelectionGesture(MouseEvent event) {
        return event.isControlDown() || event.isShiftDown() || event.isMetaDown();
    }

    /** The selected entries in row order (top to bottom), without the invisible root. */
    List<FavoriteEntry> selectedEntries() {
        List<FavoriteEntry> selected = new ArrayList<FavoriteEntry>();
        int[] rows = tree.getSelectionRows();
        if (rows == null) {
            return selected;
        }
        int[] sorted = rows.clone();
        java.util.Arrays.sort(sorted);
        for (int row : sorted) {
            TreePath path = tree.getPathForRow(row);
            FavoriteEntry entry = path == null ? null : entryOf(path.getLastPathComponent());
            if (entry != null) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private List<String> selectedIdsInRowOrder() {
        List<String> ids = new ArrayList<String>();
        for (FavoriteEntry entry : selectedEntries()) {
            ids.add(entry.id());
        }
        return ids;
    }

    /** Select {@code ids} together (e.g. after moving them as a block) and show the first one. */
    public void selectAll(List<String> ids) {
        List<TreePath> paths = new ArrayList<TreePath>();
        for (String id : ids) {
            TreePath path = treePath(id);
            if (path != null) {
                paths.add(path);
            }
        }
        if (paths.isEmpty()) {
            return;
        }
        tree.setSelectionPaths(paths.toArray(new TreePath[0]));
        tree.setLeadSelectionPath(paths.get(paths.size() - 1));
        tree.scrollPathToVisible(paths.get(0));
        remember(pathTo(entries, ids.get(ids.size() - 1)));
        selectedIds = selectedIdsInRowOrder();
    }

    private void remember(List<FavoriteEntry> path) {
        if (path == null || path.isEmpty()) {
            selectedId = null;
            selectedParentId = null;
        } else {
            selectedId = path.get(path.size() - 1).id();
            selectedParentId = path.size() >= 2 ? path.get(path.size() - 2).id() : null;
        }
    }

    private TreePath treePath(String id) {
        if (id == null) {
            return null;
        }
        java.util.Enumeration<?> nodes = rootNode.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            FavoriteEntry entry = entryOf(node);
            if (entry != null && entry.id().equals(id)) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    /** Der Weg von der obersten Ebene bis {@code id} im echten (ungefilterten) Baum; leer, wenn es ihn nicht gibt. */
    static List<FavoriteEntry> pathTo(List<FavoriteEntry> nodes, String id) {
        if (id != null) {
            for (FavoriteEntry entry : nodes) {
                if (entry.id().equals(id)) {
                    return Collections.singletonList(entry);
                }
                List<FavoriteEntry> below = pathTo(entry.children(), id);
                if (!below.isEmpty()) {
                    List<FavoriteEntry> path = new ArrayList<FavoriteEntry>();
                    path.add(entry);
                    path.addAll(below);
                    return path;
                }
            }
        }
        return Collections.emptyList();
    }

    private List<FavoriteEntry> selectedEntryPath() {
        FavoriteEntry entry = selectedEntry();
        return entry == null ? null : pathTo(entries, entry.id());
    }

    private FavoriteEntry selectedEntry() {
        TreePath path = tree.getLeadSelectionPath();
        if (path == null || !tree.isPathSelected(path)) {
            path = tree.getSelectionPath();
        }
        return path == null ? null : entryOf(path.getLastPathComponent());
    }

    private FavoriteEntry entryAt(int x, int y) {
        int row = tree.getRowForLocation(x, y);
        TreePath path = row < 0 ? null : tree.getPathForRow(row);
        return path == null ? null : entryOf(path.getLastPathComponent());
    }

    /** Ob die Stelle auf dem ✕ am Ende ihrer Zeile liegt. */
    private boolean onRemoveMark(int x, int y) {
        int row = tree.getRowForLocation(x, y);
        java.awt.Rectangle bounds = row < 0 ? null : tree.getRowBounds(row);
        return bounds != null && x >= bounds.x + bounds.width - REMOVE_WIDTH;
    }

    private static FavoriteEntry entryOf(Object node) {
        if (node instanceof DefaultMutableTreeNode) {
            Object value = ((DefaultMutableTreeNode) node).getUserObject();
            return value instanceof FavoriteEntry ? (FavoriteEntry) value : null;
        }
        return null;
    }

    /** Ziehen gibt es nur mit Bildschirm ({@code JTree} wirft sonst, z. B. in Tests ohne Anzeige). */
    private void setDragEnabled(boolean enabled) {
        if (!GraphicsEnvironment.isHeadless()) {
            tree.setDragEnabled(enabled);
        }
    }

    // ------------------------------------------------------------------ Kontextmenü

    private void showMenu(MouseEvent event) {
        if (actions == null) {
            return;
        }
        final FavoriteEntry entry = entryAt(event.getX(), event.getY());
        JPopupMenu menu = new JPopupMenu();
        if (entry == null) {
            menu.add(item(NEW_FOLDER, () -> createFolder(null)));
        } else {
            if (entry.isFolder()) {
                menu.add(item(NEW_SUBFOLDER, () -> createFolder(entry.id())));
                menu.add(item(RENAME, () -> rename(entry)));
            }
            menu.add(item(REMOVE, () -> removeWithConfirmation(entry)));
        }
        menu.show(tree, event.getX(), event.getY());
    }

    private static JMenuItem item(String text, final Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void createFolder(String parentId) {
        String name = JOptionPane.showInputDialog(this, FOLDER_NAME_PROMPT, NEW_FOLDER, JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty() && actions != null) {
            if (parentId != null) {
                collapsed.remove(parentId); // der neue Ordner soll sichtbar sein
            }
            String id = actions.folderCreateRequested(parentId, name.trim());
            if (id != null) {
                select(id);
            }
        }
    }

    private void rename(FavoriteEntry folder) {
        Object name = JOptionPane.showInputDialog(this, "Neuer Name:", RENAME, JOptionPane.PLAIN_MESSAGE, null, null,
                folder.name());
        if (name != null && !name.toString().trim().isEmpty() && actions != null) {
            actions.folderRenameRequested(folder.id(), name.toString().trim());
        }
    }

    /** Ein Favorit oder ein leerer Ordner sofort, ein Ordner mit Inhalt erst nach Rückfrage (MainframeMate: nie). */
    private void removeWithConfirmation(FavoriteEntry entry) {
        if (actions == null) {
            return;
        }
        if (entry.isFolder() && !entry.children().isEmpty()) {
            int favorites = entry.favoriteCount();
            int choice = JOptionPane.showConfirmDialog(this, "Ordner „" + entry.name() + "“ mit "
                            + favorites + (favorites == 1 ? " Favorit" : " Favoriten") + " und allen Unterordnern "
                            + "entfernen?", REMOVE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
        }
        actions.entryRemoveRequested(entry.id());
    }

    // ------------------------------------------------------------------ Drag and Drop

    /** Wohin ein Ablegen führt: Zielordner ({@code null} = oberste Ebene) und Position ({@code -1} = ans Ende). */
    static final class DropTarget {
        final String folderId;
        final int index;

        DropTarget(String folderId, int index) {
            this.folderId = folderId;
            this.index = index;
        }
    }

    /**
     * Übersetzt die Ablagestelle im Baum: auf einen Ordner = hinein, ans Ende; zwischen Zeilen = an diese Stelle; auf
     * einen Favoriten = hinter ihn in seinen Ordner (MainframeMate verlor den Eintrag hier); auf die freie Fläche =
     * oberste Ebene. {@code null}, wenn ein gezogener Ordner in sich selbst oder seinen Unterbaum fiele.
     */
    static DropTarget dropTarget(List<FavoriteEntry> moved, FavoriteEntry onEntry, FavoriteEntry parentOfEntry,
                                 int indexOfEntry, int childIndex) {
        DropTarget target;
        if (onEntry == null) {
            target = new DropTarget(null, childIndex);
        } else if (onEntry.isFolder()) {
            target = new DropTarget(onEntry.id(), childIndex);
        } else {
            target = new DropTarget(parentOfEntry == null ? null : parentOfEntry.id(), indexOfEntry + 1);
        }
        if (target.folderId != null) {
            for (FavoriteEntry entry : moved) {
                if (entry.containsId(target.folderId)) {
                    return null;
                }
            }
        }
        return target;
    }

    private DropTarget dropTarget(TransferHandler.TransferSupport support) {
        if (dragged == null || isFiltering() || !support.isDrop()
                || !support.isDataFlavorSupported(ENTRY_FLAVOR)) {
            return null;
        }
        JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
        TreePath path = location.getPath();
        if (path == null) {
            return new DropTarget(null, -1); // unterhalb der letzten Zeile: oberste Ebene, ans Ende
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        FavoriteEntry onEntry = entryOf(node);
        FavoriteEntry parent = null;
        int index = -1;
        if (onEntry != null && !onEntry.isFolder()) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
            parent = entryOf(parentNode);
            index = parentNode.getIndex(node);
        }
        return dropTarget(dragged, onEntry, parent, index, location.getChildIndex());
    }

    /** Zieht Ordner und Favoriten wie MainframeMates {@code BookmarkTreeTransferHandler}, aber über IDs. */
    private final class EntryTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            final List<FavoriteEntry> selection = selectedEntries();
            if (selection.isEmpty() || isFiltering()) {
                return null;
            }
            dragged = selection;
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{ENTRY_FLAVOR};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return ENTRY_FLAVOR.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return selection;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return dropTarget(support) != null;
        }

        @Override
        public boolean importData(TransferSupport support) {
            DropTarget target = dropTarget(support);
            if (target == null || actions == null) {
                return false;
            }
            List<String> ids = new ArrayList<String>();
            for (FavoriteEntry entry : dragged) {
                ids.add(entry.id());
            }
            if (target.folderId != null) {
                collapsed.remove(target.folderId); // hineingezogen = sichtbar
            }
            boolean moved = actions.moveRequested(ids, target.folderId, target.index);
            if (moved) {
                selectAll(ids);
            }
            return moved;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            dragged = null;
        }
    }

    // ------------------------------------------------------------------ Darstellung

    /** Der Baum füllt die Höhe des Drawers, damit Rechtsklick und Ablegen auf der freien Fläche gehen. */
    private static final class FillingTree extends JTree {
        FillingTree(DefaultTreeModel model) {
            super(model);
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }

    /** Ordner: Mappe und Name; Favorit: Stern, Titel und darunter die Adresse (wie eine ReaderLinkRow). */
    private final class EntryRenderer extends JPanel implements TreeCellRenderer {
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel location = new JLabel();
        private final JLabel remove = new JLabel(ComposerIcons.close());
        private final Icon folderIcon = SidebarIcons.folder();
        private final Icon starIcon = SidebarIcons.star(true);
        private boolean selected;

        EntryRenderer() {
            super(new BorderLayout(6, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            title.setAlignmentX(LEFT_ALIGNMENT);
            location.setAlignmentX(LEFT_ALIGNMENT);
            location.setFont(ResearchUiTypography.regular(10.5f));
            location.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
            text.add(title);
            text.add(location);
            icon.setVerticalAlignment(JLabel.TOP);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            remove.setVerticalAlignment(JLabel.TOP);
            remove.setHorizontalAlignment(JLabel.RIGHT);
            remove.setPreferredSize(new java.awt.Dimension(REMOVE_WIDTH - 6, 16));
            remove.setToolTipText(REMOVE_TOOLTIP);
            add(remove, BorderLayout.EAST);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean isSelected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            selected = isSelected;
            FavoriteEntry entry = entryOf(value);
            if (entry == null) {
                title.setText("");
                location.setVisible(false);
                icon.setIcon(null);
                return this;
            }
            title.setText(entry.name());
            title.setForeground(palette.getInk());
            if (entry.isFolder()) {
                icon.setIcon(folderIcon);
                title.setFont(ResearchUiTypography.semiBold(12.5f));
                location.setVisible(false);
            } else {
                icon.setIcon(starIcon);
                title.setFont(ResearchUiTypography.regular(12.5f));
                String where = entry.link().location();
                location.setText(where);
                location.setVisible(!where.isEmpty() && !where.equals(entry.name()));
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
}
