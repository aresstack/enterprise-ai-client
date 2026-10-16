package com.aresstack.enterpriseai.proasteion.application.memory;

import com.aresstack.enterpriseai.astu.acropolis.chat.project.ProjectContextResolver;
import com.aresstack.enterpriseai.astu.approval.ApprovalPolicy;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.approval.ApprovalPromptPort;
import com.aresstack.enterpriseai.astu.approval.ApprovalRules;
import com.aresstack.enterpriseai.astu.approval.ApprovalTarget;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemorySaved;
import com.aresstack.enterpriseai.memory.api.MemoryScope;
import com.aresstack.enterpriseai.memory.api.MemoryStoreException;
import com.aresstack.enterpriseai.memory.api.MemoryStorePort;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use cases behind the memory tools (#144). Every call resolves what the chat may see from trusted context: the chat
 * itself, the project it is assigned to now, and the running user. A scope argument can only narrow that, never widen
 * it. Results are model-facing text; memory content is never logged.
 *
 * <p>Deletion goes through the generic {@link ToolApprovalGate} (#145): this service registers the side-effect-free
 * target resolver of {@code memory_delete} with the built-in defaults chat ALLOW, project and global ASK, which
 * {@code tools.approval} can override per scope.
 */
public final class MemoryService {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;
    static final int MAX_CONTENT_LENGTH = 2000;
    private static final int PREVIEW_LENGTH = 200;
    /** Name of the delete tool; the approval rules are keyed by it. */
    public static final String DELETE_TOOL = "memory_delete";
    static final String DELETE_ACTION = "delete";
    private static final String ID_ARGUMENT = "id";

    private static final Logger LOG = Logger.getLogger(MemoryService.class.getName());

    private final MemoryStorePort store;
    private final String userId;
    private final ProjectContextResolver projects;
    private final ToolApprovalGate approvals;

    /**
     * @param userId    the running user/profile; key of the global memories
     * @param projects  current project of a chat, asked on every call; {@code null}: no chat has a project
     * @param approvals the application's approval gate; this service registers {@value #DELETE_TOOL} with it.
     *                  {@code null}: a gate without rules and without anyone to ask, so only chat memories can be
     *                  deleted
     */
    public MemoryService(MemoryStorePort store, String userId, ProjectContextResolver projects,
                         ToolApprovalGate approvals) {
        if (store == null || userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("store and userId must be given");
        }
        this.store = store;
        this.userId = userId;
        this.projects = projects == null ? ProjectContextResolver.none() : projects;
        this.approvals = approvals == null ? new ToolApprovalGate(ApprovalRules::none,
                ApprovalPromptPort.unavailable(), ToolApprovalGate.DEFAULT_TIMEOUT_MILLIS) : approvals;
        this.approvals.registerResolver(DELETE_TOOL, (toolName, arguments) -> deletionTarget(arguments),
                defaultDeletePolicies());
    }

    /** Built-in rules of {@value #DELETE_TOOL}: chat {@code ALLOW}, project and global {@code ASK}. */
    static Map<String, ApprovalPolicy> defaultDeletePolicies() {
        Map<String, ApprovalPolicy> policies = new LinkedHashMap<String, ApprovalPolicy>();
        policies.put(DELETE_ACTION + "/" + MemoryScope.CHAT.wireName(), ApprovalPolicy.ALLOW);
        policies.put(DELETE_ACTION + "/" + MemoryScope.PROJECT.wireName(), ApprovalPolicy.ASK);
        policies.put(DELETE_ACTION + "/" + MemoryScope.GLOBAL.wireName(), ApprovalPolicy.ASK);
        return policies;
    }

    /**
     * Side-effect-free resolver for the gate: the memory's real scope and version from the store. Throws for an
     * unknown id, so the gate does not run the call.
     */
    private ApprovalTarget deletionTarget(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get(ID_ARGUMENT);
        MemoryId id = MemoryId.parse(raw == null ? null : String.valueOf(raw));
        Memory memory = id == null ? null : store.find(id);
        if (memory == null) {
            throw new IllegalArgumentException("unknown memory");
        }
        return new ApprovalTarget(DELETE_ACTION, memory.scope().wireName(), id.visible(),
                String.valueOf(memory.version()), "Erinnerung " + id.visible() + " ("
                + memory.scope().wireName() + ") löschen: " + preview(memory.content()));
    }

    /** {@code memory_save}: {@code scope} {@code null} or blank means chat. */
    public MemoryResult save(String chatId, String content, String scopeArgument) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return MemoryResult.error("Memories are not available without a chat context.");
        }
        if (content == null || content.trim().isEmpty()) {
            return MemoryResult.error("Missing argument: content (the information to remember).");
        }
        String text = content.trim();
        if (text.length() > MAX_CONTENT_LENGTH) {
            return MemoryResult.error("The memory is too long (" + text.length() + " characters, at most "
                    + MAX_CONTENT_LENGTH + "). Save a shorter, self-contained summary.");
        }
        if (SecretGuard.looksSecret(text)) {
            return MemoryResult.error("Not saved: the text looks like it contains a password, token, key or other "
                    + "secret. Secrets are never stored as memories.");
        }
        MemoryScope scope;
        if (scopeArgument == null || scopeArgument.trim().isEmpty()) {
            scope = MemoryScope.CHAT;
        } else {
            scope = MemoryScope.parse(scopeArgument);
            if (scope == null) {
                return MemoryResult.error("Unknown scope '" + scopeArgument.trim()
                        + "'. Use chat (default), project or global.");
            }
        }
        MemoryOwner owner;
        switch (scope) {
            case PROJECT:
                String projectId = projectOf(chatId);
                if (projectId == null || projectId.trim().isEmpty()) {
                    return MemoryResult.error("Not saved: this chat is not assigned to a project, so there is no "
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
            return MemoryResult.ok((saved.existing() ? "Already saved as " : "Saved as ") + memory.id().visible()
                    + " (scope: " + memory.scope().wireName() + ").");
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory not saved: " + e.getMessage(), e);
            return MemoryResult.error("Not saved: the memory store is not available (" + e.getMessage() + ").");
        }
    }

    /** {@code memory_search}: {@code scope} only narrows the accessible scopes. */
    public MemoryResult search(String chatId, String query, String scopeArgument, long limit) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return MemoryResult.error("Memories are not available without a chat context.");
        }
        MemoryScope only = null;
        if (scopeArgument != null && !scopeArgument.trim().isEmpty()) {
            only = MemoryScope.parse(scopeArgument);
            if (only == null) {
                return MemoryResult.error("Unknown scope '" + scopeArgument.trim()
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
            return MemoryResult.ok(only == MemoryScope.PROJECT
                    ? "No project memories: this chat is not assigned to a project."
                    : "No memories found.");
        }
        int max = limit < 1 ? DEFAULT_LIMIT : (int) Math.min(limit, MAX_LIMIT);
        try {
            List<Memory> found = store.search(owners, query, max);
            if (found.isEmpty()) {
                return MemoryResult.ok("No memories found.");
            }
            StringBuilder text = new StringBuilder();
            text.append(found.size()).append(found.size() == 1 ? " memory:" : " memories:");
            for (Memory memory : found) {
                text.append('\n').append("- ").append(memory.id().visible())
                        .append(" [").append(memory.scope().wireName()).append(", ")
                        .append(memory.createdAt().atOffset(ZoneOffset.UTC).toLocalDate()).append("] ")
                        .append(memory.content());
            }
            return MemoryResult.ok(text.toString());
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory search failed: " + e.getMessage(), e);
            return MemoryResult.error("The memory store is not available (" + e.getMessage() + ").");
        }
    }

    /** {@code memory_delete}: exactly one memory, authorized by its actual owner and the policy of its scope. */
    public MemoryResult delete(String chatId, String idArgument) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return MemoryResult.error("Memories are not available without a chat context.");
        }
        MemoryId id = MemoryId.parse(idArgument);
        if (id == null) {
            return MemoryResult.error("Missing or invalid argument: id (a memory id such as M-0042 from "
                    + "memory_search or memory_save).");
        }
        try {
            Memory memory = store.find(id);
            // Unknown and not accessible look the same: the existence of other chats' memories is not revealed.
            if (memory == null || !accessibleOwners(chatId).contains(memory.owner())) {
                return MemoryResult.error("Nothing deleted: there is no memory " + id.visible()
                        + " accessible from this chat.");
            }
            // Ownership is checked above, so the confirmation never shows another chat's memory. The gate resolves
            // scope and version from the store, asks on ASK and re-checks the target after the answer; the delete
            // below is bound to the version read here, which closes the window up to the write.
            ToolApprovalGate.Verdict verdict = approvals.check(DELETE_TOOL,
                    Collections.<String, Object>singletonMap(ID_ARGUMENT, id.visible()));
            if (!verdict.permitted()) {
                return MemoryResult.error("Nothing deleted: deleting the " + memory.scope().wireName() + " memory "
                        + id.visible() + " was not permitted (" + verdict.reason() + ").");
            }
            // The project assignment may have changed while the user decided.
            if (!accessibleOwners(chatId).contains(memory.owner())) {
                return MemoryResult.error("Nothing deleted: memory " + id.visible()
                        + " is no longer accessible from this chat.");
            }
            if (!store.delete(id, memory.version())) {
                return MemoryResult.error("Nothing deleted: memory " + id.visible()
                        + " was changed or deleted in the meantime.");
            }
            return MemoryResult.ok("Deleted " + id.visible() + " (scope: " + memory.scope().wireName() + ").");
        } catch (MemoryStoreException e) {
            LOG.log(Level.WARNING, "Memory deletion failed: " + e.getMessage(), e);
            return MemoryResult.error("Nothing deleted: the memory store is not available (" + e.getMessage()
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
        String projectId = projectOf(chatId);
        if (projectId != null && !projectId.trim().isEmpty()) {
            owners.add(MemoryOwner.project(projectId));
        }
        owners.add(MemoryOwner.global(userId));
        return owners;
    }

    /** The chat's current project (#143), or {@code null}; an unresolvable assignment counts as none (fail closed). */
    private String projectOf(String chatId) {
        try {
            String projectId = projects.resolve(new ChatConversationId(chatId)).projectId();
            return projectId == null || projectId.trim().isEmpty() ? null : projectId;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Project of a chat not resolved, memories without project: " + e.getMessage(), e);
            return null;
        }
    }

    private static String preview(String content) {
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
    }
}
