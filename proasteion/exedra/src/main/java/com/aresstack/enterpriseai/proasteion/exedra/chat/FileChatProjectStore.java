package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectStore;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ChatProjectsRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chat projects (#143) as one JSON file, separate from the chat files ({@link FileChatHistoryStore}): the chats stay
 * where they are, only this file says which project they belong to. Lenient like the chat store: an unreadable file
 * reads as "no projects", a failed write is logged, never thrown.
 */
public final class FileChatProjectStore implements ChatProjectStore {

    private static final Logger LOG = Logger.getLogger(FileChatProjectStore.class.getName());

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public FileChatProjectStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
    }

    @Override
    public synchronized ChatProjectsRecord load() {
        if (!Files.isRegularFile(file)) {
            return new ChatProjectsRecord();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ChatProjectsRecord record = gson.fromJson(reader, ChatProjectsRecord.class);
            return record != null ? record : new ChatProjectsRecord();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Chat-Projekte nicht lesbar: " + e.getClass().getSimpleName(), e);
            return new ChatProjectsRecord();
        }
    }

    @Override
    public synchronized void save(ChatProjectsRecord record) {
        if (record == null) {
            return;
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(record, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Chat-Projekte nicht gespeichert: " + e.getClass().getSimpleName(), e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // overwritten by the next save
            }
        }
    }
}
