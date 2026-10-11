package com.aresstack.enterpriseai.proasteion.exedra.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die letzten Suchbegriffe der Hub-Suche, eine Zeile je Begriff neben der Konfigurationsdatei, neuester zuerst. Fehler
 * beim Lesen oder Schreiben kosten nur die Historie.
 */
final class HubSearchHistory {

    static final String FILE_NAME = "hub-search-history.txt";
    static final int LIMIT = 10;

    private HubSearchHistory() {
    }

    static List<String> read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        try {
            List<String> history = new ArrayList<String>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String query = line.trim();
                if (!query.isEmpty() && !history.contains(query) && history.size() < LIMIT) {
                    history.add(query);
                }
            }
            return history;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    static List<String> remember(Path file, String query) {
        List<String> history = new ArrayList<String>(read(file));
        String trimmed = query == null ? "" : query.trim().replace('\n', ' ').replace('\r', ' ');
        if (trimmed.isEmpty() || file == null) {
            return history;
        }
        history.remove(trimmed);
        history.add(0, trimmed);
        while (history.size() > LIMIT) {
            history.remove(history.size() - 1);
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, history, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // nur die Historie geht verloren
        }
        return history;
    }
}
