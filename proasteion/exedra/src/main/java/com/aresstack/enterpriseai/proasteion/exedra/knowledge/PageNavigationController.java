package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.PageNavigationUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.PageNavigationPanel;
import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.SourceLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connects the compass page of the drawer ({@link PageNavigationPanel}, issue #97) with the reader and
 * {@link PageNavigationUseCase}: it follows the active page of the reader, whatever opened it, and shows its place in the
 * tree of its source and its cross references; a click in the compass opens through the same reader path as search and
 * favorites ({@code opener}, in the shell {@link KnowledgeReadingController#open}).
 *
 * <p>Source calls run on the work executor, everything on the panel on the UI executor. A newer page wins: results of
 * an older one are dropped ({@link #generation}); children of a branch belong to the root they were asked for and are
 * dropped when another root took its place. While the compass is hidden nothing loads; showing it catches up.
 */
public final class PageNavigationController implements PageNavigationPanel.Actions {

    private static final Logger LOG = Logger.getLogger(PageNavigationController.class.getName());
    static final String SOURCE_OVERVIEW = "Eine Quellenübersicht hat keinen Platz im Seitenbaum. Öffne eine Seite, dann "
            + "zeigt der Kompass, wo sie steht.";
    static final String NOT_CONNECTED = "Die Quelle dieser Seite ist nicht angebunden.";
    static final String NOT_NAVIGABLE = "Diese Quelle bietet (noch) keine Seitenstruktur und keine Querverweise.";
    static final String NO_HIERARCHY = "Diese Seite hat in ihrer Quelle keine Seitenhierarchie.";
    static final String NO_REFERENCES = "Diese Quelle kann keine Querverweise ermitteln.";
    /** Separates the source from the node key in the keys of the panel. */
    private static final char KEY_SEPARATOR = '\n';

    private final ChatWorkspacePanel workspace;
    private final Function<String, KnowledgeSourceRegistration> lookup;
    private final PageNavigationUseCase navigation;
    private final BiConsumer<ReaderLink, Boolean> opener;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    /** Exports a page with its subtree (#115); {@code null} without export. */
    private final KnowledgeExportController exporter;
    /** The page the compass follows (UI thread). */
    private ReaderLink current;
    private boolean stale = true;
    private int generation;
    /** Identity of the root on the panel; children asked for under another root are dropped. */
    private Object rootToken = new Object();
    private KnowledgeSourceRegistration rootSource;
    /** The nodes of the panel's tree by panel key (UI thread). */
    private final Map<String, NavigationNode> nodes = new HashMap<String, NavigationNode>();

    public PageNavigationController(ChatWorkspacePanel workspace,
                                    Function<String, KnowledgeSourceRegistration> lookup,
                                    PageNavigationUseCase navigation, BiConsumer<ReaderLink, Boolean> opener,
                                    Executor uiExecutor, Executor workExecutor) {
        this(workspace, lookup, navigation, opener, uiExecutor, workExecutor, null);
    }

    public PageNavigationController(ChatWorkspacePanel workspace,
                                    Function<String, KnowledgeSourceRegistration> lookup,
                                    PageNavigationUseCase navigation, BiConsumer<ReaderLink, Boolean> opener,
                                    Executor uiExecutor, Executor workExecutor, KnowledgeExportController exporter) {
        if (workspace == null || lookup == null || navigation == null || opener == null || uiExecutor == null
                || workExecutor == null) {
            throw new IllegalArgumentException("all arguments are required");
        }
        this.workspace = workspace;
        this.lookup = lookup;
        this.navigation = navigation;
        this.opener = opener;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
        this.exporter = exporter;
    }

    /** Follow the reader and serve the compass page; on the UI thread. */
    public void attach() {
        workspace.navigation().setActions(this);
        workspace.reader().addActiveLinkListener(this::activePageChanged);
        activePageChanged(workspace.reader().activeLink());
    }

    /** The reader shows another page (or none). */
    public void activePageChanged(ReaderLink link) {
        current = link;
        stale = true;
        if (workspace.navigation().isShowing()) {
            refresh();
        }
    }

    @Override
    public void shown() {
        if (stale) {
            refresh();
        }
    }

    @Override
    public void pageOpened(ReaderLink page, boolean pin) {
        opener.accept(page, pin);
    }

    /** Export the page of the tree with its whole subtree from the source, not what the tree shows (#115). */
    @Override
    public void exportRequested(String key) {
        NavigationNode node = nodes.get(key);
        KnowledgeSourceRegistration registration = rootSource;
        if (exporter == null || node == null || registration == null) {
            return;
        }
        exporter.exportSubtree(workspace.navigation(), registration.port(), node);
    }

    /** Show where {@link #current} sits and what it links to. */
    void refresh() {
        stale = false;
        final int run = ++generation;
        final PageNavigationPanel panel = workspace.navigation();
        final ReaderLink link = current;
        if (link == null) {
            panel.showMessage(PageNavigationPanel.NOTHING_OPEN);
            return;
        }
        if (link.isSource()) {
            // A click on the root opens the overview of the space: keep the tree, only nothing is current.
            if (panel.rootKey() == null) {
                panel.showMessage(SOURCE_OVERVIEW);
            } else {
                panel.reveal(new ArrayList<String>());
                panel.showReferencesMessage("");
            }
            return;
        }
        final KnowledgeSourceRegistration registration = lookup.apply(link.sourceId());
        if (registration == null) {
            panel.showMessage(NOT_CONNECTED);
            return;
        }
        if (!navigation.supports(registration.port())) {
            panel.showMessage(NOT_NAVIGABLE);
            return;
        }
        final KnowledgeResourceId page = KnowledgeResourceId.of(link.resourceId());
        panel.showReferencesLoading();
        if (panel.rootKey() == null) {
            panel.showTreeMessage(PageNavigationPanel.LOADING);
        }
        execute(() -> loadTree(run, registration, link, page), () -> panel.showTreeMessage(
                "Gerade nicht ladbar."));
        execute(() -> loadReferences(run, registration, link, page), () -> panel.showReferencesMessage(
                "Gerade nicht ladbar."));
    }

    // ------------------------------------------------------------------ Seitenbaum

    /** Locate the page and load the children along its path (cached in the use case), then reveal it. */
    private void loadTree(final int run, final KnowledgeSourceRegistration registration, final ReaderLink link,
                          KnowledgeResourceId page) {
        final KnowledgeSourcePort port = registration.port();
        try {
            final PagePosition position = navigation.position(port, page);
            if (position == null) {
                ui(run, () -> workspace.navigation().showTreeMessage(NO_HIERARCHY));
                return;
            }
            List<NavigationNode> path = position.path();
            final Map<NavigationNode, List<NavigationNode>> children =
                    new LinkedHashMap<NavigationNode, List<NavigationNode>>();
            for (int i = 0; i < path.size() - 1; i++) {
                children.put(path.get(i), allChildren(port, path.get(i)));
            }
            ui(run, () -> showPath(registration, link.sourceId(), position, children));
        } catch (KnowledgeSourceException e) {
            LOG.log(Level.INFO, "Seitenbaum zu " + link.key() + " nicht geladen (" + e.kind() + ")", e);
            final String text = problemText(e);
            ui(run, () -> workspace.navigation().showTreeMessage(text));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Seitenbaum zu " + link.key() + " nicht geladen", e);
            ui(run, () -> workspace.navigation().showTreeMessage("Seitenbaum nicht ladbar (Protokoll)."));
        }
    }

    /** All children of a node, batch after batch until the source says it is complete. */
    private List<NavigationNode> allChildren(KnowledgeSourcePort port, NavigationNode parent)
            throws KnowledgeSourceException {
        List<NavigationNode> all = new ArrayList<NavigationNode>();
        String cursor = "";
        do {
            NavigationChildren batch = navigation.children(port, parent, cursor);
            all.addAll(batch.nodes());
            cursor = batch.nextCursor();
        } while (!cursor.isEmpty());
        return all;
    }

    private void showPath(KnowledgeSourceRegistration registration, String sourceId, PagePosition position,
                          Map<NavigationNode, List<NavigationNode>> children) {
        PageNavigationPanel panel = workspace.navigation();
        String rootKey = key(sourceId, position.root());
        if (!rootKey.equals(panel.rootKey())) {
            rootToken = new Object();
            rootSource = registration;
            nodes.clear();
            nodes.put(rootKey, position.root());
            panel.showRoot(PageNavigationPanel.Item.root(rootKey, position.root().title(), rootLink(sourceId,
                    position.root())));
        }
        for (Map.Entry<NavigationNode, List<NavigationNode>> entry : children.entrySet()) {
            String parentKey = key(sourceId, entry.getKey());
            if (!panel.isLoaded(parentKey)) {
                panel.addChildren(parentKey, items(sourceId, entry.getValue()), true);
            }
        }
        List<String> path = new ArrayList<String>();
        for (NavigationNode node : position.path()) {
            path.add(key(sourceId, node));
        }
        panel.reveal(path);
    }

    /** A branch was opened by hand: load its children batch by batch into the tree. */
    @Override
    public void childrenRequested(String key) {
        NavigationNode node = nodes.get(key);
        KnowledgeSourceRegistration registration = rootSource;
        if (node == null || registration == null) {
            workspace.navigation().childrenFailed(key, "Nicht ladbar.");
            return;
        }
        loadBatch(rootToken, registration, key, node, "");
    }

    private void loadBatch(final Object token, final KnowledgeSourceRegistration registration, final String key,
                           final NavigationNode node, final String cursor) {
        final String sourceId = registration.sourceId().value();
        execute(() -> {
            try {
                final NavigationChildren batch = navigation.children(registration.port(), node, cursor);
                uiExecutor.execute(() -> {
                    if (token != rootToken) {
                        return; // another root is on the panel now
                    }
                    workspace.navigation().addChildren(key, items(sourceId, batch.nodes()), batch.complete());
                    if (!batch.complete()) {
                        loadBatch(token, registration, key, node, batch.nextCursor());
                    }
                });
            } catch (KnowledgeSourceException e) {
                LOG.log(Level.INFO, "Kindseiten von " + node.key() + " nicht geladen (" + e.kind() + ")", e);
                final String text = problemText(e);
                uiExecutor.execute(() -> {
                    if (token == rootToken) {
                        workspace.navigation().childrenFailed(key, text);
                    }
                });
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Kindseiten von " + node.key() + " nicht geladen", e);
                uiExecutor.execute(() -> {
                    if (token == rootToken) {
                        workspace.navigation().childrenFailed(key, "Nicht ladbar (Protokoll).");
                    }
                });
            }
        }, () -> workspace.navigation().childrenFailed(key, "Gerade nicht ladbar."));
    }

    /** Panel items for nodes; remember them for later expansion (UI thread). */
    private List<PageNavigationPanel.Item> items(String sourceId, List<NavigationNode> children) {
        List<PageNavigationPanel.Item> items = new ArrayList<PageNavigationPanel.Item>();
        for (NavigationNode child : children) {
            String key = key(sourceId, child);
            nodes.put(key, child);
            items.add(child.isRoot() ? PageNavigationPanel.Item.root(key, child.title(), rootLink(sourceId, child))
                    : PageNavigationPanel.Item.page(key, child.title(), pageLink(sourceId, child.resource(),
                    child.title()), child.children() != NavigationNode.Children.NO));
        }
        return items;
    }

    // ------------------------------------------------------------------ Querverweise

    private void loadReferences(final int run, KnowledgeSourceRegistration registration, final ReaderLink link,
                                KnowledgeResourceId page) {
        final String sourceId = link.sourceId();
        try {
            CrossReferences references = navigation.crossReferences(registration.port(), page);
            if (references == null) {
                ui(run, () -> workspace.navigation().showReferencesMessage(NO_REFERENCES));
                return;
            }
            final List<PageNavigationPanel.Item> outgoing = referenceItems(sourceId, references.outgoing());
            final List<PageNavigationPanel.Item> incoming = references.incomingSupported()
                    ? referenceItems(sourceId, references.incoming()) : null;
            ui(run, () -> workspace.navigation().showReferences(outgoing, incoming));
        } catch (KnowledgeSourceException e) {
            LOG.log(Level.INFO, "Querverweise von " + link.key() + " nicht geladen (" + e.kind() + ")", e);
            final String text = problemText(e);
            ui(run, () -> workspace.navigation().showReferencesMessage(text));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Querverweise von " + link.key() + " nicht geladen", e);
            ui(run, () -> workspace.navigation().showReferencesMessage("Querverweise nicht ladbar (Protokoll)."));
        }
    }

    private static List<PageNavigationPanel.Item> referenceItems(String sourceId, List<SourceLink> links) {
        List<PageNavigationPanel.Item> items = new ArrayList<PageNavigationPanel.Item>();
        for (SourceLink reference : links) {
            String title = reference.label().isEmpty() ? reference.target().value() : reference.label();
            items.add(PageNavigationPanel.Item.page(sourceId + KEY_SEPARATOR + reference.target().value(), title,
                    pageLink(sourceId, reference.target(), title), false));
        }
        return items;
    }

    // ------------------------------------------------------------------ Hilfen

    private static String key(String sourceId, NavigationNode node) {
        return sourceId + KEY_SEPARATOR + node.key();
    }

    private static ReaderLink pageLink(String sourceId, KnowledgeResourceId resource, String title) {
        return new ReaderLink(sourceId, resource.value(), title, "", resource.value());
    }

    /** The root opens the overview of its partition (Confluence: the pages of the space). */
    private static ReaderLink rootLink(String sourceId, NavigationNode root) {
        return root.startPoint().isEmpty() ? ReaderLink.source(sourceId, root.title())
                : ReaderLink.partition(sourceId, root.startPoint(), root.title());
    }

    /** Run {@code task} on the UI executor unless a newer page took over. */
    private void ui(final int run, final Runnable task) {
        uiExecutor.execute(() -> {
            if (run == generation) {
                task.run();
            }
        });
    }

    private void execute(Runnable work, Runnable rejected) {
        try {
            workExecutor.execute(work);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Navigation: Hintergrundarbeit abgelehnt", e);
            rejected.run();
        }
    }

    private static String problemText(KnowledgeSourceException e) {
        switch (e.kind()) {
            case NOT_FOUND:
                return "Die Seite gibt es in der Quelle nicht (mehr).";
            case ACCESS_DENIED:
                return "Kein Zugriff (Berechtigung oder Anmeldung).";
            case UNAVAILABLE:
                return "Die Quelle ist gerade nicht erreichbar.";
            default:
                return "Unerwartete Antwort der Quelle (Protokoll).";
        }
    }
}
