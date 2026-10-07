package com.aresstack.enterpriseai.application.mcp.archfixture;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: ein MCP-Werkzeug nimmt Secret-Material entgegen. */
public final class ToolTouchingSecret {

    public int invoke(FakeSecretMaterial material) {
        return material.copySecret().length;
    }
}
