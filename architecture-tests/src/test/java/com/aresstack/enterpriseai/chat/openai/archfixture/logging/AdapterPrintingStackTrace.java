package com.aresstack.enterpriseai.chat.openai.archfixture.logging;

import java.io.IOException;

/** Absichtlicher Verstoß: printStackTrace im Produktionscode. */
public final class AdapterPrintingStackTrace {

    public void run() {
        try {
            throw new IOException("x");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
