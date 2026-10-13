package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PageNavigationSource;
import com.aresstack.enterpriseai.source.api.PagePosition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use case "navigate a knowledge source" for the compass view of the drawer (issue #97): where the open page sits in the
 * hierarchy of its source, the children of a node batch by batch and the cross references of the page. It knows sources
 * only through their port and the optional {@link PageNavigationSource}; it blocks for the duration of a call.
 *
 * <p>Child batches are cached per source, node and cursor for the lifetime of the use case, so switching back to a space
 * or along a path does not ask the source again. Position and cross references always come fresh: they describe the
 * page as it is now.
 */
public final class PageNavigationUseCase {

    private final Map<String, NavigationChildren> childCache = new ConcurrentHashMap<String, NavigationChildren>();

    /** Return whether the source can navigate at all. */
    public boolean supports(KnowledgeSourcePort source) {
        return source instanceof PageNavigationSource;
    }

    /**
     * Locate the page; {@code null} if the source has no hierarchy (not navigable, or {@code UNSUPPORTED} for this
     * resource).
     */
    public PagePosition position(KnowledgeSourcePort source, KnowledgeResourceId page) throws KnowledgeSourceException {
        require(source, page);
        if (!supports(source)) {
            return null;
        }
        try {
            return ((PageNavigationSource) source).position(page);
        } catch (KnowledgeSourceException e) {
            if (e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED) {
                return null;
            }
            throw e;
        }
    }

    /** Return one batch of children of {@code parent}; {@code ""} as cursor starts at the first batch. */
    public NavigationChildren children(KnowledgeSourcePort source, NavigationNode parent, String cursor)
            throws KnowledgeSourceException {
        require(source, parent);
        if (!supports(source)) {
            return new NavigationChildren(null, null);
        }
        String from = cursor == null ? "" : cursor;
        String key = source.sourceId().value() + '\n' + parent.key() + '\n' + from;
        NavigationChildren cached = childCache.get(key);
        if (cached != null) {
            return cached;
        }
        NavigationChildren loaded = ((PageNavigationSource) source).children(parent, from);
        childCache.put(key, loaded);
        return loaded;
    }

    /**
     * Return the cross references of the page; {@code null} if the source cannot tell them (not navigable or
     * {@code UNSUPPORTED}).
     */
    public CrossReferences crossReferences(KnowledgeSourcePort source, KnowledgeResourceId page)
            throws KnowledgeSourceException {
        require(source, page);
        if (!supports(source)) {
            return null;
        }
        try {
            return ((PageNavigationSource) source).crossReferences(page);
        } catch (KnowledgeSourceException e) {
            if (e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED) {
                return null;
            }
            throw e;
        }
    }

    /** Forget the cached children of one source, e.g. after the user asked to reload. */
    public void forget(KnowledgeSourcePort source) {
        if (source == null) {
            return;
        }
        String prefix = source.sourceId().value() + '\n';
        for (String key : childCache.keySet()) {
            if (key.startsWith(prefix)) {
                childCache.remove(key);
            }
        }
    }

    private static void require(KnowledgeSourcePort source, Object target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("source and target must not be null");
        }
    }
}
