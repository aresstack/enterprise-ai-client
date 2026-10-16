package com.aresstack.enterpriseai.memory.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MemoryIdTest {

    @Test
    public void visibleFormHasAtLeastFourDigits() {
        assertEquals("M-0042", MemoryId.of(42).visible());
        assertEquals("M-12345", MemoryId.of(12345).visible());
    }

    @Test
    public void parsesVisibleAndRelaxedForms() {
        assertEquals(MemoryId.of(42), MemoryId.parse("M-0042"));
        assertEquals(MemoryId.of(42), MemoryId.parse(" m-42 "));
        assertEquals(MemoryId.of(42), MemoryId.parse("M42"));
    }

    @Test
    public void rejectsAnythingElse() {
        assertNull(MemoryId.parse(null));
        assertNull(MemoryId.parse(""));
        assertNull(MemoryId.parse("M-0000"));
        assertNull(MemoryId.parse("42"));
        assertNull(MemoryId.parse("M-42; DROP"));
        assertNull(MemoryId.parse("Stand-up"));
    }

    @Test
    public void scopeParsingIsCaseInsensitiveAndStrict() {
        assertEquals(MemoryScope.PROJECT, MemoryScope.parse(" Project "));
        assertNull(MemoryScope.parse("team"));
    }
}
