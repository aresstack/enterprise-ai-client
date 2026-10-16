package com.aresstack.enterpriseai.astu.knowledge;

/**
 * The shared files of a chat project as knowledge (#143): one source per project, {@code chatproject://<projectId>},
 * and one resource per file, {@code chatproject:<projectId>/<fileId>}. Such a source never stands in the global
 * source catalog; its resources are visible only to a turn whose chat currently belongs to that project.
 */
public final class ProjectKnowledge {

    /** Scheme of project sources and project resources. */
    public static final String SCHEME = "chatproject";

    private ProjectKnowledge() {
    }

    /** The source of project {@code projectId}. */
    public static SourceAddress source(String projectId) {
        return SourceAddress.of(SCHEME, requireId(projectId));
    }

    /** The resource of file {@code fileId} in project {@code projectId}. */
    public static KnowledgeResourceId resource(String projectId, String fileId) {
        return KnowledgeResourceId.of(SCHEME, requireId(projectId) + "/" + requireId(fileId));
    }

    /** Is {@code source} the source of some chat project? */
    public static boolean isProjectSource(SourceAddress source) {
        return source != null && SCHEME.equals(source.scheme());
    }

    /** Is {@code resource} a file of some chat project? */
    public static boolean isProjectResource(KnowledgeResourceId resource) {
        return resource != null && SCHEME.equals(resource.scheme());
    }

    /** The source of the project that owns {@code resource}, or {@code null} for any other resource. */
    public static SourceAddress sourceOf(KnowledgeResourceId resource) {
        if (!isProjectResource(resource)) {
            return null;
        }
        String part = resource.schemeSpecificPart();
        int slash = part.indexOf('/');
        return slash <= 0 ? null : source(part.substring(0, slash));
    }

    /**
     * May a turn that sees project {@code visibleProject} ({@code null}: none) use content of {@code source}? Every
     * source that is no project source is visible; a project source only to its own project.
     */
    public static boolean isVisible(SourceAddress source, SourceAddress visibleProject) {
        return !isProjectSource(source) || source.equals(visibleProject);
    }

    /** Like {@link #isVisible(SourceAddress, SourceAddress)} for a resource id; a malformed project id is not. */
    public static boolean isVisible(KnowledgeResourceId resource, SourceAddress visibleProject) {
        if (!isProjectResource(resource)) {
            return true;
        }
        SourceAddress owner = sourceOf(resource);
        return owner != null && owner.equals(visibleProject);
    }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty() || id.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Ungültige Projekt- oder Datei-ID: " + id);
        }
        return id;
    }
}
