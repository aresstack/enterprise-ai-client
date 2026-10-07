package com.aresstack.enterpriseai.application.chat.archfixture;

import com.aresstack.enterpriseai.knowledge.api.archfixture.FakeKnowledgeIndexPort;

/** Absichtlicher Verstoß: der Chat-Use-Case kennt den Index-Port (ChatBoundaryTest, RagBoundaryTest). */
public final class ChatUseCaseUsingIndex {

    public FakeKnowledgeIndexPort index() {
        return null;
    }
}
