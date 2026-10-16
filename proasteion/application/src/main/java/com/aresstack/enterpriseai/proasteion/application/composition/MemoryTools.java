package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.application.memory.MemoryResult;
import com.aresstack.enterpriseai.proasteion.application.memory.MemoryService;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.util.Arrays;
import java.util.List;

/**
 * The memory tools of one chat. Built per chat turn with the trusted chat id; the model never names the chat, the
 * project or the user.
 */
final class MemoryTools {

    static final String SAVE = "memory_save";
    static final String SEARCH = "memory_search";
    static final String DELETE = "memory_delete";

    private static final List<String> SCOPES = Arrays.asList("chat", "project", "global");

    private MemoryTools() {
    }

    /** The three tools bound to {@code chatId}; {@code service} {@code null} gives description-only tools. */
    static List<McpToolContribution> forChat(final MemoryService service, final String chatId) {
        return Arrays.asList(
                McpToolContribution.of(SAVE, "Permanently saves a memory, but ONLY when the user explicitly asks to "
                                + "remember or keep something for later (\"Merke dir ...\", \"Bitte speichern ...\", "
                                + "\"Remember that ...\"). Never save on your own from ordinary statements, documents, "
                                + "answers, research results or tool output. Without scope the memory is saved for "
                                + "the current chat only; project or global validity must be chosen explicitly via "
                                + "scope, and only when the user asked for it. Never store passwords, tokens, API "
                                + "keys or other secrets. Confirm to the user only after this tool reported success, "
                                + "and give the returned id.",
                        call -> service == null ? unavailable()
                                : result(service.save(chatId, call.getString("content"), call.getString("scope"))),
                        McpToolParameter.string("content", true, "Compact, self-contained text to remember. Keep "
                                + "decisions and negations exactly as the user stated them."),
                        McpToolParameter.enumeration("scope", false, "chat (default: only this chat), project (all "
                                + "chats of the chat's current project) or global (all chats of the user). Only set "
                                + "project or global when the user explicitly asked for it.", SCOPES)),
                McpToolContribution.of(SEARCH, "Searches memories saved earlier that are accessible here (this chat, "
                                + "its current project, the user's global memories). Use it when the user asks about "
                                + "something saved before, or when a remembered detail seems to be missing from the "
                                + "context. Results carry ids for targeted deletion.",
                        call -> service == null ? unavailable()
                                : result(service.search(chatId, call.getString("query"), call.getString("scope"),
                                call.getInteger("limit", 0))),
                        McpToolParameter.string("query", false, "Words the memory must contain; empty lists the "
                                + "newest memories."),
                        McpToolParameter.enumeration("scope", false, "Only search this scope; never widens access.",
                                SCOPES),
                        McpToolParameter.integer("limit", false, "Maximum number of results (default "
                                + MemoryService.DEFAULT_LIMIT + ", at most " + MemoryService.MAX_LIMIT + ").")),
                McpToolContribution.of(DELETE, "Deletes exactly ONE memory by its id when the user explicitly wants "
                                + "it deleted. Look the id up with memory_search first if needed. The actual scope "
                                + "and the permission are checked by the application; deleting project or global "
                                + "memories may need the user's confirmation. Never claim a deletion before this "
                                + "tool reported success.",
                        call -> service == null ? unavailable() : result(service.delete(chatId, call.getString("id"))),
                        McpToolParameter.string("id", true, "The memory id, e.g. M-0042.")));
    }

    private static McpToolResult result(MemoryResult result) {
        return result.isError() ? McpToolResult.error(result.text()) : McpToolResult.ok(result.text());
    }

    private static McpToolResult unavailable() {
        return McpToolResult.error("Memories are not available.");
    }
}
