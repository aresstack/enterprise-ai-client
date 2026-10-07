package com.aresstack.enterpriseai.chat.openai.archfixture.testleak;

import com.tngtech.archunit.core.importer.ClassFileImporter;

/** Absichtlicher Verstoß: Produktionscode benutzt ArchUnit. */
public final class AdapterUsingArchUnit {

    public Object importer() {
        return new ClassFileImporter();
    }
}
