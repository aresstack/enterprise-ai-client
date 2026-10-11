package com.aresstack.enterpriseai.proasteion.exedra.chat.archfixture.secret;

import com.aresstack.enterpriseai.adyton.archfixture.secret.FakeSecretMaterial;

/** Absichtlicher Verstoß: Ein Binding der Composition Root außerhalb von app.security liest Secret-Material. */
public final class ChatBindingTouchingSecret {

    public int tokenLength(FakeSecretMaterial material) {
        return material.copySecret().length;
    }
}
