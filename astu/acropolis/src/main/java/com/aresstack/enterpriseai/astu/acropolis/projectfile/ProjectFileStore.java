package com.aresstack.enterpriseai.astu.acropolis.projectfile;

import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;

import java.nio.file.Path;
import java.util.List;

/** Port: the shared files of a chat project, keyed by project id. */
public interface ProjectFileStore {

    /**
     * Stores a copy of {@code source} under a new id.
     *
     * @throws com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException if the file is unreadable,
     *         too large or the store is not writable
     */
    Attachment add(String projectId, Path source);

    /** The project's files in storage order; empty if there are none. */
    List<Attachment> list(String projectId);

    /** @throws com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException if the file is unknown */
    byte[] read(String projectId, String fileId);

    /** Removes one file; an unknown file is not an error. */
    void remove(String projectId, String fileId);

    /** Removes all files of a deleted project; an unknown project is not an error. */
    void deleteProject(String projectId);
}
