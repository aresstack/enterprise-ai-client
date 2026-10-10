package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.application.history.ChatHistoryStore;
import com.aresstack.enterpriseai.application.history.ChatRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chats als JSON-Dateien (aus askai-java8 arch, {@code ChatHistoryStore}). Ablage unter {@code <root>}:
 *
 * <pre>
 *   &lt;chatId&gt;.json                           der Chat (Nachrichten und Metadaten)
 *   &lt;chatId&gt;/&lt;attachmentId&gt;/&lt;Dateiname&gt;     die Anhänge ({@link FileAttachmentStore} mit derselben Wurzel)
 * </pre>
 *
 * Chat und Anhänge liegen zusammen, Löschen entfernt beides. Alle Methoden sind nachsichtig: ein unlesbarer
 * Chat fehlt in der Liste, ein gescheitertes Schreiben wird protokolliert, nie geworfen.
 */
public final class FileChatHistoryStore implements ChatHistoryStore {

    private static final Logger LOG = Logger.getLogger(FileChatHistoryStore.class.getName());
    private static final String SUFFIX = ".json";

    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public FileChatHistoryStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root;
    }

    @Override
    public synchronized void save(ChatRecord record) {
        if (record == null || !validId(record.getId())) {
            return;
        }
        if (record.isEmpty()) {
            delete(record.getId());
            return;
        }
        Path target = chatFile(record.getId());
        Path temp = root.resolve(record.getId() + SUFFIX + ".tmp");
        try {
            Files.createDirectories(root);
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(record, writer);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Chat nicht gespeichert: " + e.getClass().getSimpleName(), e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // bleibt liegen und wird beim nächsten Speichern überschrieben
            }
        }
    }

    @Override
    public synchronized ChatRecord load(String chatId) {
        if (!validId(chatId)) {
            return null;
        }
        Path file = chatFile(chatId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ChatRecord record = gson.fromJson(reader, ChatRecord.class);
            return record != null && chatId.equals(record.getId()) ? record : null;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Chat " + chatId + " nicht lesbar: " + e.getClass().getSimpleName(), e);
            return null;
        }
    }

    @Override
    public synchronized List<ChatRecord> list() {
        List<ChatRecord> records = new ArrayList<ChatRecord>();
        if (!Files.isDirectory(root)) {
            return records;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*" + SUFFIX)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                ChatRecord record = load(name.substring(0, name.length() - SUFFIX.length()));
                if (record != null && !record.isEmpty()) {
                    records.add(record);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Chat-Verzeichnis nicht lesbar: " + e.getClass().getSimpleName(), e);
        }
        Collections.sort(records, new Comparator<ChatRecord>() {
            @Override
            public int compare(ChatRecord a, ChatRecord b) {
                return Long.compare(b.getModifiedAt(), a.getModifiedAt());
            }
        });
        return records;
    }

    @Override
    public synchronized void delete(String chatId) {
        if (!validId(chatId)) {
            return;
        }
        try {
            Files.deleteIfExists(chatFile(chatId));
            deleteTree(root.resolve(chatId));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Chat " + chatId + " nicht gelöscht: " + e.getClass().getSimpleName(), e);
        }
    }

    private Path chatFile(String chatId) {
        return root.resolve(chatId + SUFFIX);
    }

    /** Nur Kennungen, wie Unterhaltungen sie haben (UUIDs): keine Pfadanteile. */
    private static boolean validId(String chatId) {
        return chatId != null && chatId.matches("[A-Za-z0-9_-]{1,64}");
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
