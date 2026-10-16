package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.memory.MemoryManagement;
import com.aresstack.enterpriseai.proasteion.exedra.ui.memory.MemoryBrowser;
import com.aresstack.enterpriseai.proasteion.exedra.ui.memory.MemoryEntry;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Binds the memory management view to {@link MemoryManagement} for one owner (#144). */
final class StoreMemoryBrowser implements MemoryBrowser {

    private final MemoryManagement management;
    private final MemoryManagement.Owner owner;
    private final String key;

    private StoreMemoryBrowser(MemoryManagement management, MemoryManagement.Owner owner, String key) {
        this.management = management;
        this.owner = owner;
        this.key = key;
    }

    /** {@code null} without management or without a key for a chat or project. */
    static MemoryBrowser of(MemoryManagement management, MemoryManagement.Owner owner, String key) {
        if (management == null || owner == null
                || (owner != MemoryManagement.Owner.GLOBAL && (key == null || key.trim().isEmpty()))) {
            return null;
        }
        return new StoreMemoryBrowser(management, owner, key);
    }

    @Override
    public String title() {
        switch (owner) {
            case CHAT:
                return "Chat";
            case PROJECT:
                return "Projekt";
            default:
                return "Global";
        }
    }

    @Override
    public String description() {
        switch (owner) {
            case CHAT:
                return "Erinnerungen nur dieses Chats.";
            case PROJECT:
                return "Erinnerungen des Projekts; alle Chats im Projekt sehen sie.";
            default:
                return "Globale Erinnerungen; alle Chats sehen sie.";
        }
    }

    @Override
    public List<MemoryEntry> list(String query) {
        List<MemoryEntry> entries = new ArrayList<MemoryEntry>();
        for (MemoryManagement.Item item : management.list(owner, key, query, MemoryManagement.MAX_LIMIT)) {
            entries.add(new MemoryEntry(item.id(), item.version(), item.content(),
                    item.createdAt().atZone(ZoneId.systemDefault()).toLocalDate().toString()));
        }
        return entries;
    }

    @Override
    public String delete(MemoryEntry entry) {
        switch (management.delete(owner, key, entry.id(), entry.version())) {
            case DELETED:
                return null;
            case CHANGED:
                return "Nichts gelöscht: " + entry.id() + " wurde inzwischen geändert oder gelöscht.";
            case NOT_OWNED:
                return "Nichts gelöscht: " + entry.id() + " gehört nicht hierher.";
            default:
                return "Nichts gelöscht: der Erinnerungsspeicher ist nicht verfügbar.";
        }
    }
}
