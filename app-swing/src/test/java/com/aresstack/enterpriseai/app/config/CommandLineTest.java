package com.aresstack.enterpriseai.app.config;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CommandLineTest {

    @Test
    public void splitsOnWhitespaceAndHonoursQuotes() {
        assertEquals(Arrays.asList("-jar", "C:/Programme/mein agent/agent.jar", "--stdio"),
                CommandLine.split("-jar \"C:/Programme/mein agent/agent.jar\"   --stdio"));
        assertEquals(Arrays.asList("a b", "c"), CommandLine.split("'a b' c"));
        assertEquals(Collections.emptyList(), CommandLine.split("   "));
        assertEquals(Collections.emptyList(), CommandLine.split(null));
    }
}
