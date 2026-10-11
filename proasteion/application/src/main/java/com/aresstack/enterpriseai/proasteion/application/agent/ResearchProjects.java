package com.aresstack.enterpriseai.proasteion.application.agent;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Die Ablage der Research-Projekte: genau ein Projektordner je Chat, benannt nach der Chat-Kennung (askai arch:
 * die Projekt-Kennung einer Research-Session ist der Scope ihres Chat-Tabs, {@code AgentSessionCoordinator}).
 * Es gibt keinen Zeiger auf ein „aktuelles“ Projekt: welches Projekt gilt, bestimmt allein der offene Chat.
 */
final class ResearchProjects {

    private static final Logger LOG = Logger.getLogger(ResearchProjects.class.getName());

    private final File root;

    ResearchProjects(File root) {
        this.root = root;
    }

    File root() {
        return root;
    }

    /** Der Projektordner eines Chats; Zeichen außerhalb von {@code [A-Za-z0-9._-]} werden zu {@code _}. */
    File directoryFor(String chatId) {
        return new File(root, folderName(chatId));
    }

    static String folderName(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        String name = chatId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return name.startsWith(".") ? "_" + name.substring(1) : name;
    }

    /** Ob es zu diesem Chat schon ein Projekt gibt (nichts wird angelegt). */
    boolean exists(String chatId) {
        return directoryFor(chatId).isDirectory();
    }

    /** Löscht das Projekt eines Chats samt Konzept, Quellen, Gliederung und Dokument. */
    void delete(String chatId) {
        deleteTree(directoryFor(chatId));
    }

    /**
     * Verwirft alle Projekte, deren Chat nicht (mehr) gespeichert ist, und alle Reste der früheren Ablage
     * ({@code current-project}, {@code research-<Zeit>}): kein Produktivbetrieb, keine Migration.
     */
    void retainOnly(Collection<String> chatIds) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        Set<String> keep = new HashSet<String>();
        for (String chatId : chatIds) {
            if (chatId != null && !chatId.trim().isEmpty()) {
                keep.add(folderName(chatId));
            }
        }
        for (File child : children) {
            if (!keep.contains(child.getName())) {
                deleteTree(child);
            }
        }
    }

    private static void deleteTree(File target) {
        if (!target.exists()) {
            return;
        }
        try {
            Files.walkFileTree(target.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException notDeleted) {
            LOG.warning("Research-Projekt nicht gelöscht: " + target.getName() + ": " + notDeleted.getMessage());
        }
    }
}
