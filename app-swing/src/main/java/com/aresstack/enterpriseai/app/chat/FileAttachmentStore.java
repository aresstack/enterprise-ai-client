package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.application.attachment.Attachment;
import com.aresstack.enterpriseai.application.attachment.AttachmentException;
import com.aresstack.enterpriseai.application.attachment.AttachmentStore;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Anhänge als Dateien: {@code <root>/<chatId>/<attachmentId>/<Dateiname>}, Kennungen {@code att-} plus acht
 * Hex-Zeichen. Die Reihenfolge ist die der Ablage (Änderungszeit des Kennungsverzeichnisses, dann Kennung).
 * Kopiert wird beim Anhängen, damit spätere Änderungen an der Originaldatei die Unterhaltung nicht berühren.
 */
public final class FileAttachmentStore implements AttachmentStore {

    /** Größte Datei, die angehängt werden kann. */
    public static final long MAX_BYTES = 50L * 1024 * 1024;

    private final Path root;
    private final SecureRandom random = new SecureRandom();

    public FileAttachmentStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root;
    }

    @Override
    public synchronized Attachment add(ChatConversationId conversation, Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new AttachmentException("Keine Datei: " + source);
        }
        String fileName = safeName(source.getFileName().toString());
        try {
            long size = Files.size(source);
            if (size > MAX_BYTES) {
                throw new AttachmentException(fileName + " ist größer als " + (MAX_BYTES / (1024 * 1024)) + " MB");
            }
            Path directory;
            String id;
            do {
                id = String.format(Locale.ROOT, "att-%08x", random.nextInt());
                directory = conversationDirectory(conversation).resolve(id);
            } while (Files.exists(directory));
            Files.createDirectories(directory);
            Files.copy(source, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return new Attachment(id, fileName, size);
        } catch (IOException e) {
            throw new AttachmentException(fileName + " konnte nicht abgelegt werden: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public synchronized List<Attachment> list(ChatConversationId conversation) {
        Path directory = conversationDirectory(conversation);
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        final List<Path> entries = new ArrayList<Path>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory, "att-*")) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    entries.add(child);
                }
            }
        } catch (IOException e) {
            throw new AttachmentException("Anhänge nicht lesbar: " + e.getClass().getSimpleName(), e);
        }
        Collections.sort(entries, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                int byTime = Long.compare(modified(a), modified(b));
                return byTime != 0 ? byTime : a.getFileName().toString().compareTo(b.getFileName().toString());
            }
        });
        List<Attachment> attachments = new ArrayList<Attachment>();
        for (Path entry : entries) {
            Path file = fileIn(entry);
            if (file != null) {
                try {
                    attachments.add(new Attachment(entry.getFileName().toString(), file.getFileName().toString(),
                            Files.size(file)));
                } catch (IOException ignored) {
                    // eine verschwundene Datei ist kein Anhang mehr
                }
            }
        }
        return attachments;
    }

    @Override
    public synchronized byte[] read(ChatConversationId conversation, String attachmentId) {
        if (attachmentId == null || !attachmentId.matches("att-[0-9a-f]{8}")) {
            throw new AttachmentException("Unbekannter Anhang " + attachmentId);
        }
        Path file = fileIn(conversationDirectory(conversation).resolve(attachmentId));
        if (file == null) {
            throw new AttachmentException("Unbekannter Anhang " + attachmentId);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new AttachmentException(attachmentId + " nicht lesbar: " + e.getClass().getSimpleName(), e);
        }
    }

    private Path conversationDirectory(ChatConversationId conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation must not be null");
        }
        return root.resolve(safeName(conversation.value()));
    }

    private static Path fileIn(Path directory) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (Files.isRegularFile(child)) {
                    return child;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Dateiname ohne Pfadanteile und ohne Zeichen, die Windows verbietet. */
    static String safeName(String name) {
        String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        while (clean.startsWith(".")) {
            clean = clean.substring(1);
        }
        return clean.isEmpty() ? "anhang" : clean;
    }
}
