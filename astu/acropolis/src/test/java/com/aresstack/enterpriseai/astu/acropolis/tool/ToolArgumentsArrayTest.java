package com.aresstack.enterpriseai.astu.acropolis.tool;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** #127: a JSON array argument is read as a list of texts, never split at commas. */
public class ToolArgumentsArrayTest {

    @Test
    public void arrayOfStringsKeepsOrderAndSpecialCharacters() {
        ToolArguments args = ToolArguments.parse("{\"ids\":[\"a,b\", \"c\\\"d]\", 7, null, \"e\"]}");
        assertEquals(Arrays.asList("a,b", "c\"d]", "7", "e"), args.texts("ids"));
    }

    @Test
    public void singleValueEmptyArrayAndMissingArgument() {
        assertEquals(Collections.singletonList("[not an array]"),
                ToolArguments.parse("{\"ids\":\"[not an array]\"}").texts("ids"));
        assertTrue(ToolArguments.parse("{\"ids\":[]}").texts("ids").isEmpty());
        assertNull(ToolArguments.parse("{}").texts("ids"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nestedValuesAreRejected() {
        ToolArguments.parse("{\"ids\":[[\"a\"]]}").texts("ids");
    }
}
