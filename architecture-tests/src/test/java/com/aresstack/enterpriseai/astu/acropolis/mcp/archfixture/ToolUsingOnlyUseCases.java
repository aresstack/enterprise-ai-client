package com.aresstack.enterpriseai.astu.acropolis.mcp.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.mcp.archfixture.usecase.FakeUseCase;

/** Erlaubt: ein MCP-Werkzeug, das nur einen Application-Use-Case und das JDK kennt. */
public final class ToolUsingOnlyUseCases {

    public String invoke(String query) {
        return String.valueOf(new FakeUseCase().run(query));
    }
}
