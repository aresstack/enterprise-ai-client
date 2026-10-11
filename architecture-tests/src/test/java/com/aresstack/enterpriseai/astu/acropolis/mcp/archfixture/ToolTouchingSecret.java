package com.aresstack.enterpriseai.astu.acropolis.mcp.archfixture;

import com.aresstack.enterpriseai.adyton.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: ein MCP-Werkzeug nimmt Secret-Material entgegen. */
public final class ToolTouchingSecret {

    public int invoke(FakeSecretMaterial material) {
        return material.copySecret().length;
    }
}
