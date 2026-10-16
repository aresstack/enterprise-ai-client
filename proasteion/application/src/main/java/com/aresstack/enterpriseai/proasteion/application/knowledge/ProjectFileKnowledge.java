package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.projectfile.ProjectFileStore;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.ProjectKnowledge;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puts the shared files of chat projects (#143) into the normal knowledge index, under the project's own source
 * ({@link ProjectKnowledge}): same chunking, embeddings and full-text index as every other resource. Who may see them
 * is decided at retrieval and read time against the chat's current project, never here.
 */
public final class ProjectFileKnowledge {

    private static final Logger LOG = Logger.getLogger(ProjectFileKnowledge.class.getName());
    /** How long adding a file waits for its preparation; afterwards it finishes in the background queue. */
    static final long INDEX_TIMEOUT_MILLIS = 120_000L;

    private final ProjectFileStore files;
    private final AttachmentTextExtractor text;
    private final KnowledgePrefetchService prefetch;
    private final KnowledgeIndexPort index;
    private final RawKnowledgeIndexPort raw;

    /** @param raw the raw text index, {@code null} without one */
    public ProjectFileKnowledge(ProjectFileStore files, AttachmentTextExtractor text, KnowledgePrefetchService prefetch,
                                KnowledgeIndexPort index, RawKnowledgeIndexPort raw) {
        if (files == null || text == null || prefetch == null || index == null) {
            throw new IllegalArgumentException("files, text, prefetch and index must not be null");
        }
        this.files = files;
        this.text = text;
        this.prefetch = prefetch;
        this.index = index;
        this.raw = raw;
    }

    public ProjectFileStore files() {
        return files;
    }

    /** The document a project file is indexed as. */
    KnowledgeDocument document(String projectId, Attachment file) {
        String content = text.extractText(file.fileName(), files.read(projectId, file.id()));
        KnowledgeResource resource = KnowledgeResource.builder(ProjectKnowledge.resource(projectId, file.id()),
                        ProjectKnowledge.source(projectId))
                .title(file.fileName())
                .revision(KnowledgeRevision.version(file.id() + "/" + file.sizeBytes()))
                .build();
        return KnowledgeDocument.of(resource, content == null ? "" : content);
    }

    /**
     * Indexes one file of the project (unchanged files are skipped by the pipeline). Blocks up to
     * {@link #INDEX_TIMEOUT_MILLIS}; call it off the UI thread.
     */
    public void index(String projectId, Attachment file) {
        prefetch.indexNow(document(projectId, file), INDEX_TIMEOUT_MILLIS);
    }

    /** Indexes every stored file of the projects, e.g. at start after an index rebuild; failures are logged. */
    public void indexAll(Collection<String> projectIds) {
        for (String projectId : projectIds) {
            for (Attachment file : files.list(projectId)) {
                try {
                    index(projectId, file);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Projektdatei " + file.fileName() + " nicht indexiert: " + e.getMessage(),
                            e);
                }
            }
        }
    }

    /** Removes one file from the store and the index. */
    public void remove(String projectId, String fileId) {
        files.remove(projectId, fileId);
        KnowledgeResourceId id = ProjectKnowledge.resource(projectId, fileId);
        index.remove(id);
        if (raw != null) {
            raw.remove(id);
        }
    }

    /** Removes all files of a deleted project from the store and the index. */
    public void deleteProject(String projectId) {
        files.deleteProject(projectId);
        SourceAddress source = ProjectKnowledge.source(projectId);
        index.removeSource(source);
        if (raw != null) {
            raw.removeSource(source);
        }
    }
}
