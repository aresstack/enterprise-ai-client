package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Use case "export pages" (#115): builds the {@link ExportDocumentNode} tree every output format writes from, either
 * for one page or for a page with its complete subtree. The subtree comes from the source ({@link PageNavigationUseCase}
 * batch by batch until complete), never from what a view happens to show; every page is loaded fresh through
 * {@link KnowledgeSourcePort#load}. A node key seen before is skipped, so a faulty source cannot loop or export a page
 * twice. Loading goes on past a failure to name all of them; then {@link ExportIncompleteException} replaces the tree.
 * Blocks for the duration of the export.
 */
public final class KnowledgeExportUseCase {

    /** Hears how far an export is; called on the exporting thread. */
    public interface Progress {
        /**
         * @param loaded pages loaded so far
         * @param found  pages known so far (grows while the subtree is walked)
         * @param title  the page just loaded
         */
        void pageLoaded(int loaded, int found, String title);
    }

    private final PageNavigationUseCase navigation;

    public KnowledgeExportUseCase(PageNavigationUseCase navigation) {
        if (navigation == null) {
            throw new IllegalArgumentException("navigation must not be null");
        }
        this.navigation = navigation;
    }

    /** Load one page as a tree with a single chapter; its children are never asked for. */
    public ExportDocumentNode page(KnowledgeSourcePort source, KnowledgeResourceId page, String title,
                                   Progress progress, BooleanSupplier cancelled) throws ExportIncompleteException {
        if (source == null || page == null) {
            throw new IllegalArgumentException("source and page must not be null");
        }
        Walk walk = new Walk(source, progress, cancelled);
        walk.found = 1;
        KnowledgeDocument document = walk.load(page, title);
        walk.finish();
        return new ExportDocumentNode(title, document, null);
    }

    /** Load {@code node} and every page below it, in source order, whatever a view has expanded. */
    public ExportDocumentNode subtree(KnowledgeSourcePort source, NavigationNode node, Progress progress,
                                      BooleanSupplier cancelled) throws ExportIncompleteException {
        if (source == null || node == null) {
            throw new IllegalArgumentException("source and node must not be null");
        }
        Walk walk = new Walk(source, progress, cancelled);
        walk.visited.add(node.key());
        walk.found = 1;
        ExportDocumentNode root = walk.node(node);
        walk.finish();
        return root;
    }

    /** One run through a subtree with its failures and counters. */
    private final class Walk {
        private final KnowledgeSourcePort source;
        private final Progress progress;
        private final BooleanSupplier cancelled;
        private final Set<String> visited = new HashSet<String>();
        private final List<String> failures = new ArrayList<String>();
        private int loaded;
        private int found;

        Walk(KnowledgeSourcePort source, Progress progress, BooleanSupplier cancelled) {
            this.source = source;
            this.progress = progress;
            this.cancelled = cancelled;
        }

        ExportDocumentNode node(NavigationNode node) {
            checkCancelled();
            KnowledgeDocument document = node.isRoot() ? null : load(node.resource(), node.title());
            List<NavigationNode> children = node.children() == NavigationNode.Children.NO
                    ? Collections.<NavigationNode>emptyList() : children(node);
            List<NavigationNode> fresh = new ArrayList<NavigationNode>();
            for (NavigationNode child : children) {
                if (visited.add(child.key())) {
                    fresh.add(child);
                }
            }
            found += fresh.size();
            List<ExportDocumentNode> chapters = new ArrayList<ExportDocumentNode>();
            for (NavigationNode child : fresh) {
                chapters.add(node(child));
            }
            return new ExportDocumentNode(node.title(), document, chapters);
        }

        /** All children of {@code parent}, batch after batch; a failed batch is a failure, not an empty branch. */
        private List<NavigationNode> children(NavigationNode parent) {
            List<NavigationNode> all = new ArrayList<NavigationNode>();
            Set<String> cursors = new HashSet<String>();
            String cursor = "";
            try {
                do {
                    checkCancelled();
                    NavigationChildren batch = navigation.children(source, parent, cursor);
                    all.addAll(batch.nodes());
                    cursor = batch.nextCursor();
                } while (!cursor.isEmpty() && cursors.add(cursor)); // a repeated cursor would never end
            } catch (KnowledgeSourceException e) {
                failures.add(parent.title() + " (Unterseiten): " + reason(e));
            }
            return all;
        }

        KnowledgeDocument load(KnowledgeResourceId page, String title) {
            checkCancelled();
            try {
                KnowledgeDocument document = source.load(page);
                loaded++;
                if (progress != null) {
                    progress.pageLoaded(loaded, found, title);
                }
                return document;
            } catch (KnowledgeSourceException e) {
                failures.add((title == null || title.trim().isEmpty() ? page.value() : title.trim()) + ": "
                        + reason(e));
                return null;
            }
        }

        void finish() throws ExportIncompleteException {
            if (!failures.isEmpty()) {
                throw new ExportIncompleteException(failures);
            }
        }

        private void checkCancelled() {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancellationException("export cancelled");
            }
        }
    }

    private static String reason(KnowledgeSourceException e) {
        switch (e.kind()) {
            case NOT_FOUND:
                return "gibt es nicht (mehr)";
            case ACCESS_DENIED:
                return "kein Zugriff";
            case UNAVAILABLE:
                return "Quelle nicht erreichbar";
            default:
                return "nicht ladbar";
        }
    }
}
