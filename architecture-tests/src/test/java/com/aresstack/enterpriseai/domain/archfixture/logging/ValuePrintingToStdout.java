package com.aresstack.enterpriseai.domain.archfixture.logging;

/** Absichtlicher Verstoß: Ausgabe auf System.out/System.err im Produktionscode. */
public final class ValuePrintingToStdout {

    public void print(String text) {
        System.out.println(text);
        System.err.println(text);
    }
}
