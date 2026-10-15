package com.aresstack.enterpriseai.astu.acropolis.chatproject;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;

/** Resolves the project context of a conversation for one new turn, from its current project assignment. */
public interface ProjectContextResolver {

    /** No projects at all: every conversation is without project and keeps the scope {@link EffectiveProjectContext#NONE_SCOPE}. */
    ProjectContextResolver NONE = new ProjectContextResolver() {
        @Override
        public EffectiveProjectContext resolve(ChatConversationId conversation) {
            return EffectiveProjectContext.none();
        }
    };

    /** Never {@code null}; may throw, callers then fail closed ({@link EffectiveProjectContext#unresolved()}). */
    EffectiveProjectContext resolve(ChatConversationId conversation);
}
