package com.aresstack.enterpriseai.astu.acropolis.chat.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.archfixture.FakeKnowledgeIndexPort;

/** Absichtlicher Verstoß: der Chat-Use-Case kennt den Index-Port (ChatBoundaryTest, RagBoundaryTest). */
public final class ChatUseCaseUsingIndex {

    public FakeKnowledgeIndexPort index() {
        return null;
    }
}
