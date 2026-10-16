package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

/** A shared project file as the project view shows it (#143). */
public final class ChatProjectFileItem {

    private final String id;
    private final String name;
    private final long sizeBytes;

    public ChatProjectFileItem(String id, String name, long sizeBytes) {
        this.id = id;
        this.name = name;
        this.sizeBytes = sizeBytes;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public String toString() {
        long kb = Math.max(1, sizeBytes / 1024);
        return name + " (" + kb + " KB)";
    }
}
