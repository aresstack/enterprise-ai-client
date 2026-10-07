package com.aresstack.enterpriseai.app.ui.archfixture;

import com.aresstack.enterpriseai.chat.api.archfixture.PortWithMain;

/** Absichtlicher Verstoß: Die Oberfläche spricht einen Port direkt an statt über einen Use Case. */
public final class UiCallingPort {

    public Class<?> port() {
        return PortWithMain.class;
    }
}
