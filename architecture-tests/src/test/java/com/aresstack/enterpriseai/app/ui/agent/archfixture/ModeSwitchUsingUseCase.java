package com.aresstack.enterpriseai.app.ui.agent.archfixture;

import com.aresstack.enterpriseai.application.agent.archfixture.AgentUseCaseUsingAcp;

/** Absichtlicher Verstoß: die Modus-Umschaltung ruft einen Use Case (AgentModeBoundaryTest). */
public final class ModeSwitchUsingUseCase {

    private final AgentUseCaseUsingAcp target = new AgentUseCaseUsingAcp();

    public Object use() {
        return target.session();
    }
}
