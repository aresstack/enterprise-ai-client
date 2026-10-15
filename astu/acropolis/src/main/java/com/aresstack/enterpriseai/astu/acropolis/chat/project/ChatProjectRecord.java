package com.aresstack.enterpriseai.astu.acropolis.chat.project;

import java.util.ArrayList;
import java.util.List;

/**
 * One chat project as persisted: stable id (independent of name and position), name, parent ({@code null} = top
 * level), position among its siblings, and the project context (instructions, memories). Mutable only inside
 * {@link ChatProjects}; callers get copies.
 */
public final class ChatProjectRecord {

    private String id;
    private String name;
    private String parentId;
    private int order;
    private String instructions;
    private List<String> memories = new ArrayList<String>();

    /** For deserialisation. */
    public ChatProjectRecord() {
    }

    public ChatProjectRecord(String id, String name, String parentId, int order) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.order = order;
    }

    ChatProjectRecord copy() {
        ChatProjectRecord copy = new ChatProjectRecord(id, name, parentId, order);
        copy.instructions = instructions;
        copy.memories = new ArrayList<String>(getMemories());
        return copy;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public String getName() {
        return name != null ? name : "";
    }

    /** The parent project id, or {@code null} for a top-level project. */
    public String getParentId() {
        return parentId == null || parentId.isEmpty() ? null : parentId;
    }

    public int getOrder() {
        return order;
    }

    public String getInstructions() {
        return instructions != null ? instructions : "";
    }

    public List<String> getMemories() {
        return memories != null ? memories : new ArrayList<String>();
    }

    void setName(String name) {
        this.name = name;
    }

    void setParentId(String parentId) {
        this.parentId = parentId;
    }

    void setOrder(int order) {
        this.order = order;
    }

    void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    void setMemories(List<String> memories) {
        this.memories = new ArrayList<String>(memories);
    }
}
