package com.aresstack.enterpriseai.astu.acropolis.chat.project;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Use case "chat projects" (#143): the project tree (create, rename, nest, reorder, delete) and the assignment of
 * chats to at most one project. Every change is saved at once through {@link ChatProjectStore}.
 *
 * <p>As {@link ProjectContextResolver} it answers, for every new turn, which project context currently applies.
 * Moving a chat raises its generation, so the {@linkplain EffectiveProjectContext#scope() scope} of every earlier
 * assignment stops matching (also after moving back). Deleting a project never deletes chats: the chats of the
 * project and of its sub-projects become chats without project. Sub-projects do not inherit instructions or
 * memories.
 *
 * <p>Thread-safe; listeners are called after every change on the calling thread.
 */
public final class ChatProjects implements ProjectContextResolver {

    /** Told after every change of the tree, the context of a project or an assignment. */
    public interface Listener {
        void projectsChanged();
    }

    private final ChatProjectStore store;
    private final Map<String, ChatProjectRecord> projects = new HashMap<String, ChatProjectRecord>();
    private final Map<String, ChatProjectsRecord.Assignment> assignments =
            new HashMap<String, ChatProjectsRecord.Assignment>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public ChatProjects(ChatProjectStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
        ChatProjectsRecord stored = store.load();
        if (stored != null) {
            for (ChatProjectRecord project : stored.getProjects()) {
                if (!project.getId().isEmpty() && !project.getName().trim().isEmpty()) {
                    projects.put(project.getId(), project.copy());
                }
            }
            // Parents that no longer exist (or cycles from a hand-edited file) make a project top level.
            for (ChatProjectRecord project : projects.values()) {
                if (project.getParentId() != null && (!projects.containsKey(project.getParentId())
                        || isWithin(project.getParentId(), project.getId()))) {
                    project.setParentId(null);
                }
            }
            for (ChatProjectsRecord.Assignment assignment : stored.getAssignments()) {
                if (assignment.getChatId().isEmpty()) {
                    continue;
                }
                String projectId = assignment.getProjectId();
                assignments.put(assignment.getChatId(), new ChatProjectsRecord.Assignment(assignment.getChatId(),
                        projectId != null && projects.containsKey(projectId) ? projectId : null,
                        Math.max(0L, assignment.getGeneration())));
            }
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** All projects, depth first in tree order (parents before their children, siblings by position). */
    public synchronized List<ChatProjectRecord> projects() {
        List<ChatProjectRecord> ordered = new ArrayList<ChatProjectRecord>(projects.size());
        addSubtree(null, ordered);
        return ordered;
    }

    /** The direct children of {@code parentId} ({@code null} = top level), by position. */
    public synchronized List<ChatProjectRecord> children(String parentId) {
        List<ChatProjectRecord> copies = new ArrayList<ChatProjectRecord>();
        for (ChatProjectRecord child : sortedChildren(parentId)) {
            copies.add(child.copy());
        }
        return copies;
    }

    /** One project, or {@code null}. */
    public synchronized ChatProjectRecord project(String projectId) {
        ChatProjectRecord project = projectId == null ? null : projects.get(projectId);
        return project == null ? null : project.copy();
    }

    /** How deep a project is nested (0 = top level); -1 if unknown. */
    public synchronized int depth(String projectId) {
        ChatProjectRecord project = projectId == null ? null : projects.get(projectId);
        if (project == null) {
            return -1;
        }
        int depth = 0;
        while (project.getParentId() != null) {
            project = projects.get(project.getParentId());
            depth++;
        }
        return depth;
    }

    /**
     * Creates a project as last child of {@code parentId} ({@code null} = top level).
     *
     * @return the new stable id, or {@code null} for a blank name or an unknown parent
     */
    public String create(String name, String parentId) {
        String id;
        synchronized (this) {
            if (name == null || name.trim().isEmpty() || parentId != null && !projects.containsKey(parentId)) {
                return null;
            }
            id = UUID.randomUUID().toString();
            projects.put(id, new ChatProjectRecord(id, name.trim(), parentId, sortedChildren(parentId).size()));
            persist();
        }
        fire();
        return id;
    }

    /** Renames a project; blank names and unknown projects are ignored. */
    public boolean rename(String projectId, String name) {
        synchronized (this) {
            ChatProjectRecord project = projectId == null ? null : projects.get(projectId);
            if (project == null || name == null || name.trim().isEmpty()) {
                return false;
            }
            project.setName(name.trim());
            persist();
        }
        fire();
        return true;
    }

    /** Replaces the instructions and memories of a project; they apply to the next turn of its chats. */
    public boolean updateContext(String projectId, String instructions, List<String> memories) {
        synchronized (this) {
            ChatProjectRecord project = projectId == null ? null : projects.get(projectId);
            if (project == null) {
                return false;
            }
            project.setInstructions(instructions == null ? "" : instructions.trim());
            List<String> kept = new ArrayList<String>();
            if (memories != null) {
                for (String memory : memories) {
                    if (memory != null && !memory.trim().isEmpty()) {
                        kept.add(memory.trim());
                    }
                }
            }
            project.setMemories(kept);
            persist();
        }
        fire();
        return true;
    }

    /** Whether {@code projectId} may become a child of {@code newParentId}: known, and not into its own subtree. */
    public synchronized boolean canMove(String projectId, String newParentId) {
        if (projectId == null || !projects.containsKey(projectId)) {
            return false;
        }
        return newParentId == null || projects.containsKey(newParentId) && !isWithin(newParentId, projectId);
    }

    /**
     * Moves a project below {@code newParentId} ({@code null} = top level) at {@code index} among its new siblings
     * (out of range = last). Moves into the own subtree are refused.
     */
    public boolean move(String projectId, String newParentId, int index) {
        synchronized (this) {
            if (!canMove(projectId, newParentId)) {
                return false;
            }
            ChatProjectRecord project = projects.get(projectId);
            String oldParent = project.getParentId();
            List<ChatProjectRecord> siblings = sortedChildren(newParentId);
            siblings.remove(project);
            int at = index < 0 || index > siblings.size() ? siblings.size() : index;
            siblings.add(at, project);
            project.setParentId(newParentId);
            renumber(siblings);
            if (oldParent == null ? newParentId != null : !oldParent.equals(newParentId)) {
                renumber(sortedChildren(oldParent));
            }
            persist();
        }
        fire();
        return true;
    }

    /**
     * Deletes a project and its sub-projects. No chat is deleted: every chat in them becomes a chat without project
     * (with a new generation, so their project context ends at once).
     *
     * @return the ids of the chats that lost their project; empty for an unknown project
     */
    public List<String> delete(String projectId) {
        List<String> released = new ArrayList<String>();
        synchronized (this) {
            if (projectId == null || !projects.containsKey(projectId)) {
                return released;
            }
            Set<String> removed = subtree(projectId);
            for (ChatProjectsRecord.Assignment assignment : new ArrayList<ChatProjectsRecord.Assignment>(
                    assignments.values())) {
                if (assignment.getProjectId() != null && removed.contains(assignment.getProjectId())) {
                    assignments.put(assignment.getChatId(), new ChatProjectsRecord.Assignment(assignment.getChatId(),
                            null, assignment.getGeneration() + 1));
                    released.add(assignment.getChatId());
                }
            }
            String parent = projects.get(projectId).getParentId();
            for (String id : removed) {
                projects.remove(id);
            }
            renumber(sortedChildren(parent));
            persist();
        }
        fire();
        return released;
    }

    /** The chats of a project and its sub-projects (what {@link #delete} would release). */
    public synchronized List<String> chatsWithin(String projectId) {
        List<String> chats = new ArrayList<String>();
        if (projectId == null || !projects.containsKey(projectId)) {
            return chats;
        }
        Set<String> ids = subtree(projectId);
        for (ChatProjectsRecord.Assignment assignment : assignments.values()) {
            if (assignment.getProjectId() != null && ids.contains(assignment.getProjectId())) {
                chats.add(assignment.getChatId());
            }
        }
        return chats;
    }

    /** The project of a chat, or {@code null} for a chat without project. */
    public synchronized String projectOf(String chatId) {
        ChatProjectsRecord.Assignment assignment = chatId == null ? null : assignments.get(chatId);
        return assignment == null ? null : assignment.getProjectId();
    }

    /**
     * Assigns a chat to a project ({@code null} = chat without project). History, chat id and the chat's own
     * attachments stay as they are; only the project context of new turns changes.
     *
     * @return {@code false} for an unknown project or when nothing changes
     */
    public boolean assign(String chatId, String projectId) {
        synchronized (this) {
            if (chatId == null || chatId.isEmpty() || projectId != null && !projects.containsKey(projectId)) {
                return false;
            }
            ChatProjectsRecord.Assignment current = assignments.get(chatId);
            String before = current == null ? null : current.getProjectId();
            if (before == null ? projectId == null : before.equals(projectId)) {
                return false;
            }
            long generation = current == null ? 1L : current.getGeneration() + 1;
            assignments.put(chatId, new ChatProjectsRecord.Assignment(chatId, projectId, generation));
            persist();
        }
        fire();
        return true;
    }

    @Override
    public EffectiveProjectContext resolve(ChatConversationId conversation) {
        return resolve(conversation == null ? null : conversation.value());
    }

    /** The project context of the chat's next turn; unknown chats are without project. */
    public synchronized EffectiveProjectContext resolve(String chatId) {
        if (chatId == null) {
            return EffectiveProjectContext.unresolved();
        }
        ChatProjectsRecord.Assignment assignment = assignments.get(chatId);
        if (assignment == null) {
            return EffectiveProjectContext.none();
        }
        ChatProjectRecord project = assignment.getProjectId() == null ? null : projects.get(assignment.getProjectId());
        return project == null ? EffectiveProjectContext.withoutProject(assignment.getGeneration())
                : EffectiveProjectContext.of(project, assignment.getGeneration());
    }

    private void addSubtree(String parentId, List<ChatProjectRecord> into) {
        for (ChatProjectRecord child : sortedChildren(parentId)) {
            into.add(child.copy());
            addSubtree(child.getId(), into);
        }
    }

    private List<ChatProjectRecord> sortedChildren(String parentId) {
        List<ChatProjectRecord> children = new ArrayList<ChatProjectRecord>();
        for (ChatProjectRecord project : projects.values()) {
            String parent = project.getParentId();
            if (parentId == null ? parent == null : parentId.equals(parent)) {
                children.add(project);
            }
        }
        Collections.sort(children, new Comparator<ChatProjectRecord>() {
            @Override
            public int compare(ChatProjectRecord a, ChatProjectRecord b) {
                int byOrder = Integer.compare(a.getOrder(), b.getOrder());
                return byOrder != 0 ? byOrder : a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return children;
    }

    private static void renumber(List<ChatProjectRecord> siblings) {
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setOrder(i);
        }
    }

    /** Whether {@code candidate} is {@code ancestorId} or lies below it. */
    private boolean isWithin(String candidate, String ancestorId) {
        Set<String> seen = new LinkedHashSet<String>();
        String current = candidate;
        while (current != null && seen.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            ChatProjectRecord project = projects.get(current);
            current = project == null ? null : project.getParentId();
        }
        return current != null; // a cycle counts as "within": never allowed
    }

    private Set<String> subtree(String projectId) {
        Set<String> ids = new LinkedHashSet<String>();
        ids.add(projectId);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ChatProjectRecord project : projects.values()) {
                if (project.getParentId() != null && ids.contains(project.getParentId()) && ids.add(project.getId())) {
                    grew = true;
                }
            }
        }
        return ids;
    }

    private void persist() {
        List<ChatProjectRecord> copies = new ArrayList<ChatProjectRecord>();
        addSubtree(null, copies);
        store.save(new ChatProjectsRecord(copies, new ArrayList<ChatProjectsRecord.Assignment>(assignments.values())));
    }

    private void fire() {
        for (Listener listener : listeners) {
            listener.projectsChanged();
        }
    }
}
