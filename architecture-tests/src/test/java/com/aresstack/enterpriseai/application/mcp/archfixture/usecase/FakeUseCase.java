package com.aresstack.enterpriseai.application.mcp.archfixture.usecase;

/** Steht für einen Application-Use-Case; liegt unterhalb von application.mcp, damit die Regel ihn erlaubt. */
public final class FakeUseCase {

    public int run(String query) {
        return query.length();
    }
}
