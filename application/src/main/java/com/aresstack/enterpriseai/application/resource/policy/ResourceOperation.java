package com.aresstack.enterpriseai.application.resource.policy;

/**
 * Stable set of resource operations that can be requested through the
 * mediated bronze access path.
 */
public enum ResourceOperation {
    /** List children of a directory/container resource. */
    LIST_CHILDREN,
    /** Read metadata (existence, name, type, size, modified time). */
    READ_METADATA,
    /** Read content snapshot. */
    READ_CONTENT,
    /** Fetch from the external source (triggers acquisition). */
    FETCH_EXTERNAL,
    /** Refresh a stale cached/archived resource from the external source. */
    REFRESH_EXTERNAL,
    /** Index content into derived views (anagraphai/pinakes). */
    INDEX_CONTENT,
    /** Return as a search result. */
    SEARCH_RESULT,
    /** Delete an archive entry (tombstone). */
    DELETE_ARCHIVE_ENTRY
}
