package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

/** A chat project as the drawer shows it (#143): stable id, name, nesting and its instructions for the edit dialog. */
public final class ChatProjectItem {

    private final String id;
    private final String name;
    private final String parentId;
    private final int depth;
    private final String instructions;

    public ChatProjectItem(String id, String name, String parentId, int depth, String instructions) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "(ohne Namen)" : name.trim();
        this.parentId = parentId;
        this.depth = Math.max(0, depth);
        this.instructions = instructions == null ? "" : instructions;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** The parent project, or {@code null} at top level. */
    public String parentId() {
        return parentId;
    }

    public int depth() {
        return depth;
    }

    public String instructions() {
        return instructions;
    }
}
