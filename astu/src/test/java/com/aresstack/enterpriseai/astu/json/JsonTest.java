package com.aresstack.enterpriseai.astu.json;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonTest {

    @Test
    public void readsNestedValues() {
        Map<String, Object> root = Json.parseObject(" {\"text\":\"l\\u00f6st \\\"aus\\\"\",\"words\":[3, 4],"
                + "\"ok\":true,\"none\":null,\"inner\":{\"x\":-1.25e1}} ");

        assertEquals("löst \"aus\"", Json.string(root, "text"));
        assertEquals(Arrays.<Object>asList(new BigDecimal("3"), new BigDecimal("4")), Json.array(root, "words"));
        assertEquals(Integer.valueOf(3), Json.integer(Json.array(root, "words").get(0)));
        assertTrue(Json.bool(root, "ok", false));
        assertTrue(root.containsKey("none"));
        assertNull(root.get("none"));
        assertEquals(new BigDecimal("-1.25e1"), Json.object(root, "inner").get("x"));
        assertNull("not integral", Json.integer(Json.object(root, "inner"), "x"));
        assertTrue(Json.array(root, "missing").isEmpty());
    }

    @Test
    public void rejectsWhatIsNoStrictJson() {
        for (String text : Arrays.asList("", "{", "{\"a\":1,}", "{'a':1}", "{\"a\":1} x", "[1 2]", "{\"a\":01}",
                "{\"a\":1,\"a\":2}", "{\"a\":\"\t\"}", "tru", "{\"a\":NaN}")) {
            try {
                Json.parse(text);
                fail("accepted " + text);
            } catch (JsonException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("offset"));
            }
        }
    }

    @Test
    public void refusesDeepNesting() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= Json.MAX_DEPTH + 1; i++) {
            text.append('[');
        }
        try {
            Json.parse(text.toString());
            fail("accepted deep nesting");
        } catch (JsonException expected) {
            assertTrue(expected.getMessage().contains("deeper"));
        }
    }

    @Test
    public void writesWhatItReads() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("text", "Zeile 1\nZeile \"2\" \\ \u0001");
        value.put("list", Arrays.<Object>asList(1, 2.5, true, null));
        String json = Json.write(value);

        assertEquals("{\"text\":\"Zeile 1\\nZeile \\\"2\\\" \\\\ \\u0001\",\"list\":[1,2.5,true,null]}", json);
        Map<String, Object> read = Json.parseObject(json);
        assertEquals(value.get("text"), read.get("text"));
        List<Object> list = Json.array(read, "list");
        assertEquals(new BigDecimal("2.5"), list.get(1));
    }
}
