package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemorySaved;
import com.aresstack.enterpriseai.memory.api.MemoryScope;
import com.aresstack.enterpriseai.memory.api.MemoryStoreException;
import com.aresstack.enterpriseai.memory.api.MemoryStorePort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use cases behind the memory tools (#144). Every call resolves what the chat may see from trusted context: the chat
 * itself, the project it is assigned to now, and the running user. A scope argument can only narrow that, never widen
 * it. Results are model-facing text; memory content is never logged.
 */
public final class MemoryService {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final int MAX_CONTENT_LENGTH = 2000;
    private static final int PREVIEW_LENGTH = 200;

    private static final Logger LOG = Logger.getLogger(MemoryService.class.getName());

    private final MemoryStorePort store;
    private final String userId;
    private final ChatProjectResolver projects;
    private final Map<MemoryScope, MemoryPolicy> deletePolicies;
    private final MemoryDeletionApproval approval;

    /**
     * @param userId         the running user/profile; key of the global memories
     * @param projects       current project of a chat, asked on every call
     * @param deletePolicies per scope; missing scopes use {@link #defaultDeletePolicies()}
     * @param approval       asked for deletions with {@link MemoryPolicy#ASK}
     */
    public MemoryService(MemoryStorePort store, String userId, ChatProjectResolver projects,
                         Map<MemoryScope, MemoryPolicy> deletePolicies, MemoryDeletionApproval approval) {
        if (store == null || userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("store and userId must be given");
        }
        this.store = store;
        this.userId = userId;
        this.projects = projects == null ? ChatProjectResolver.NONE : projects;
        this.deletePolicies = defaultDeletePolicies();
        if (deletePolicies != null) {
            this.deletePolicies.putAll(deletePolicies);
        }
        this.approval = approval == null ? MemoryDeletionApproval.UNAVAILABLE : approval;
    }

    /** chat {@code ALLOW}, project and global {@code ASK}. */
    public static Map<MemoryScope, MemoryPolicy> defaultDeletePolicies() {
        Map<MemoryScope, MemoryPolicy> policies = new EnumMap<MemoryScope, MemoryPolicy>(MemoryScope.class);
        policies.put(MemoryScope.CHAT, MemoryPolicy.ALLOW);
        policies.put(MemoryScope.PROJECT, MemoryPolicy.ASK);
        policies.put(MemoryScope.GLOBAL, MemoryPolicy.ASK);
        return policies;
    }

    /** {@code memory_save}: {@code scope} {@code null} or blank means chat. */
    public McpToolResult save(String chatId, String content, String scopeArgument) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return McpToolResult.error("Memories are not available without a chat context.");
        }
        if (content == null || content.trim().isEmpty()) {
            return McpToolResult.error("Missing argument: content (the information to remember).");
        }
        String text = content.trim();
        if (text.length() > MAX_CONTENT_LENGTH) {
            return McpToolResult.error("The memory is too long (" + text.length() + " characters, at most "
                    + MAX_CONTENT_LENGTH + "). Save a shorter, self-contained summary.");
        }
        if (SecretGuard.looksSecret(text)) {
            return McpToolResult.error("Not saved: the text looks like it contains a password, token, key or other "
                    + "secret. Secrets are never stored as memories.");
        }
        MemoryScope scope;
        if (scopeArgument == null || scopeArgument.trim().isEmpty()) {
            scope = MemoryScope.CHAT;
        } else {
            scope = MemoryScope.parse(scopeArgument);
            if (scope == null) {
                return McpToolResult.error("Unknown scope '" + scopeArgument.trim()
                        + "'. Use chat (default), project or global.");
            }
        }
        MemoryOwner owner;
        switch (scope) {
            case PROJECT:
                String projectId = projects.projectOf(chatId);
                if (projectId == null || projectId.trim().isEmpty()) {
                    return McpToolResult.error("Not saved: this chat is not assigned to a project, so there is no "
                            + "project scope. Save it for this chat or globally instead, if the user wants that.");
                }
                owner = MemoryOwner.project(projectId);
                break;
            case GLOBAL:
                owner = MemoryOwner.global(userId);
                break;
            default:
                owner = MemoryOwner.chat(chatId);
        }
        try {
            MemorySaved saved = store.save(owner, text);
            Memory memory = saved.memory();
            return McpToolResult.ok((saved.existing() ? "Already saved as " : "Saved as ") + memory.id().visible()
                    + " (scope: " + memory.scope().wireName() + ").");
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory not saved: " + e.getMessage(), e);
            return McpToolResult.error("Not saved: the memory store is not available (" + e.getMessage() + ").");
        }
    }

    /** {@code memory_search}: {@code scope} only narrows the accessible scopes. */
    public McpToolResult search(String chatId, String query, String scopeArgument, long limit) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return McpToolResult.error("Memories are not available without a chat context.");
        }
        MemoryScope only = null;
        if (scopeArgument != null && !scopeArgument.trim().isEmpty()) {
            only = MemoryScope.parse(scopeArgument);
            if (only == null) {
                return McpToolResult.error("Unknown scope '" + scopeArgument.trim()
                        + "'. Use chat, project or global, or leave it out to search all.");
            }
        }
        List<MemoryOwner> owners = new ArrayList<MemoryOwner>();
        for (MemoryOwner owner : accessibleOwners(chatId)) {
            if (only == null || owner.scope() == only) {
                owners.add(owner);
            }
        }
        if (owners.isEmpty()) {
            return McpToolResult.ok(only == MemoryScope.PROJECT
                    ? "No project memories: this chat is not assigned to a project."
                    : "No memories found.");
        }
        int max = limit < 1 ? DEFAULT_LIMIT : (int) Math.min(limit, MAX_LIMIT);
        try {
            List<Memory> found = store.search(owners, query, max);
            if (found.isEmpty()) {
                return McpToolResult.ok("No memories found.");
            }
            StringBuilder text = new StringBuilder();
            text.append(found.size()).append(found.size() == 1 ? " memory:" : " memories:");
            for (Memory memory : found) {
                text.append('\n').append("- ").append(memory.id().visible())
                        .append(" [").append(memory.scope().wireName()).append(", ")
                        .append(memory.createdAt().atOffset(ZoneOffset.UTC).toLocalDate()).append("] ")
                        .append(memory.content());
            }
            return McpToolResult.ok(text.toString());
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory search failed: " + e.getMessage(), e);
            return McpToolResult.error("The memory store is not available (" + e.getMessage() + ").");
        }
    }

    /** {@code memory_delete}: exactly one memory, authorized by its actual owner and the policy of its scope. */
    public McpToolResult delete(String chatId, String idArgument) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return McpToolResult.error("Memories are not available without a chat context.");
        }
        MemoryId id = MemoryId.parse(idArgument);
        if (id == null) {
            return McpToolResult.error("Missing or invalid argument: id (a memory id such as M-0042 from "
                    + "memory_search or memory_save).");
        }
        try {
            Memory memory = store.find(id);
            // Unknown and not accessible look the same: the existence of other chats' memories is not revealed.
            if (memory == null || !accessibleOwners(chatId).contains(memory.owner())) {
                return McpToolResult.error("Nothing deleted: there is no memory " + id.visible()
                        + " accessible from this chat.");
            }
            MemoryPolicy policy = deletePolicies.get(memory.scope());
            if (policy == MemoryPolicy.DENY) {
                return McpToolResult.error("Nothing deleted: deleting " + memory.scope().wireName()
                        + " memories with a tool is not allowed by the settings. The user can delete "
                        + id.visible() + " in the memory management.");
            }
            if (policy != MemoryPolicy.ALLOW) {
                boolean approved;
                try {
                    approved = approval.approve(new MemoryDeletionRequest(id, memory.scope(), memory.version(),
                            preview(memory.content())));
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Memory deletion approval failed: " + e.getMessage(), e);
                    approved = false;
                }
                if (!approved) {
                    return McpToolResult.error("Nothing deleted: deleting the " + memory.scope().wireName()
                            + " memory " + id.visible() + " needs the user's confirmation, which was not given.");
                }
                // The project assignment may have changed while the user decided.
                if (!accessibleOwners(chatId).contains(memory.owner())) {
                    return McpToolResult.error("Nothing deleted: memory " + id.visible()
                            + " is no longer accessible from this chat.");
                }
            }
            if (!store.delete(id, memory.version())) {
                return McpToolResult.error("Nothing deleted: memory " + id.visible()
                        + " was changed or deleted in the meantime.");
            }
            return McpToolResult.ok("Deleted " + id.visible() + " (scope: " + memory.scope().wireName() + ").");
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory deletion failed: " + e.getMessage(), e);
            return McpToolResult.error("Nothing deleted: the memory store is not available (" + e.getMessage()
                    + ").");
        }
    }

    /** Deletes the memories of a deleted chat; failures are logged, never thrown. */
    public void deleteChat(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return;
        }
        try {
            store.deleteAll(MemoryOwner.chat(chatId));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Memories of a deleted chat not removed: " + e.getMessage(), e);
        }
    }

    private List<MemoryOwner> accessibleOwners(String chatId) {
        List<MemoryOwner> owners = new ArrayList<MemoryOwner>(3);
        owners.add(MemoryOwner.chat(chatId));
        String projectId = projects.projectOf(chatId);
        if (projectId != null && !projectId.trim().isEmpty()) {
            owners.add(MemoryOwner.project(projectId));
        }
        owners.add(MemoryOwner.global(userId));
        return owners;
    }

    private static String preview(String content) {
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
    }
}
