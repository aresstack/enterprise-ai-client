package com.aresstack.enterpriseai.astu.acropolis.chat.project;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;

/** Resolves the project context of a conversation for one new turn, from its current project assignment. */
public interface ProjectContextResolver {

    /** No projects at all: every conversation is without project and keeps the scope {@link EffectiveProjectContext#NONE_SCOPE}. */
    static ProjectContextResolver none() {
        return conversation -> EffectiveProjectContext.none();
    }

    /** Never {@code null}; may throw, callers then fail closed ({@link EffectiveProjectContext#unresolved()}). */
    EffectiveProjectContext resolve(ChatConversationId conversation);
}
