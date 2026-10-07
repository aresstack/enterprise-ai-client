package com.aresstack.enterpriseai.app.ui.archfixture.web;

import javafx.archstub.JavaFxStub;

/** Absichtlicher Verstoß (Nachtrag 3): JavaFX in der Oberfläche. */
public final class PanelUsingJavaFx {

    private final JavaFxStub javaFx = new JavaFxStub();

    public String describe() {
        return javaFx.describe();
    }
}
