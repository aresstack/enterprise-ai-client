package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.BookmarkUri;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;

import java.util.regex.Pattern;

/**
 * Bookmarks als Eingabe von Lesewerkzeugen: {@code confluence://cf.intern/confluence/page/123} wird zur
 * kanonischen Resource-ID {@code confluence:cf.intern/confluence/page/123} ({@link KnowledgeBookmarks}). Ein
 * Bookmark hat nach dem Schema immer {@code //}, eine Resource-ID nie ({@code SourceLocators}); so bleibt die
 * Unterscheidung eindeutig.
 */
public final class KnowledgeBookmarkIds {

    private static final Pattern BOOKMARK = Pattern.compile("[a-z][a-z0-9+.-]*://.+", Pattern.DOTALL);

    private KnowledgeBookmarkIds() {
    }

    /** Ob {@code value} wie ein Bookmark aussieht (Schema gefolgt von {@code ://}). */
    public static boolean isBookmark(String value) {
        return value != null && BOOKMARK.matcher(value.trim()).matches();
    }

    /** @throws IllegalArgumentException wenn {@code bookmark} kein gültiges Bookmark ist */
    public static KnowledgeResourceId resourceIdOf(String bookmark) {
        if (!isBookmark(bookmark)) {
            throw new IllegalArgumentException("kein Bookmark: " + bookmark);
        }
        return KnowledgeBookmarks.toResourceId(BookmarkUri.parse(bookmark.trim()));
    }
}
