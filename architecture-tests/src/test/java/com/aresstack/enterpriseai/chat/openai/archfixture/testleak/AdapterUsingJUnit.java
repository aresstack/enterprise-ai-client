package com.aresstack.enterpriseai.chat.openai.archfixture.testleak;

import org.junit.Assert;

/** Absichtlicher Verstoß: Produktionscode benutzt JUnit. */
public final class AdapterUsingJUnit {

    public void check(boolean ok) {
        Assert.assertTrue(ok);
    }
}
