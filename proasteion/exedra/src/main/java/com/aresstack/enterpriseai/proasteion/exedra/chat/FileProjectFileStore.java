package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException;
import com.aresstack.enterpriseai.astu.acropolis.projectfile.ProjectFileStore;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared project files (#143) as files: {@code <root>/<projectId>/<fileId>/<file name>}, with the same ids, size
 * limit and ordering as {@link FileAttachmentStore}, which does the storage.
 */
public final class FileProjectFileStore implements ProjectFileStore {

    private final Path root;
    private final FileAttachmentStore files;

    public FileProjectFileStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root;
        this.files = new FileAttachmentStore(root);
    }

    @Override
    public Attachment add(String projectId, Path source) {
        return files.add(project(projectId), source);
    }

    @Override
    public List<Attachment> list(String projectId) {
        return files.list(project(projectId));
    }

    @Override
    public byte[] read(String projectId, String fileId) {
        return files.read(project(projectId), fileId);
    }

    @Override
    public synchronized void remove(String projectId, String fileId) {
        if (fileId == null || !fileId.matches("att-[0-9a-f]{8}")) {
            return;
        }
        Path directory = root.resolve(FileAttachmentStore.safeName(project(projectId).value())).resolve(fileId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                Files.deleteIfExists(child);
            }
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            throw new AttachmentException(fileId + " nicht löschbar: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void deleteProject(String projectId) {
        files.delete(project(projectId));
    }

    private static ChatConversationId project(String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        return new ChatConversationId(projectId);
    }
}
