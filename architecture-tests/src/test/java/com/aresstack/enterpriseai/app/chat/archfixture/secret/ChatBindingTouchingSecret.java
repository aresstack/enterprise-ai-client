package com.aresstack.enterpriseai.app.chat.archfixture.secret;

import com.aresstack.enterpriseai.security.api.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: Ein Binding der Composition Root außerhalb von app.security liest Secret-Material. */
public final class ChatBindingTouchingSecret {

    public int tokenLength(FakeSecretMaterial material) {
        return material.copySecret().length;
    }
}
