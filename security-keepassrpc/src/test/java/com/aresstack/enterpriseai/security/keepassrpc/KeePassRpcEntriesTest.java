package com.aresstack.enterpriseai.security.keepassrpc;

import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeePassRpcEntriesTest {

    @Test
    public void prefersDirectFieldsAndMatchesTitleExactly() {
        KeePassEntry entry = KeePassRpcEntries.exactTitleMatch(JsonParser.parseString("["
                + "{\"title\":\"Confluence Prod Archiv\",\"usernameValue\":\"old\",\"password\":\"old\"},"
                + "{\"title\":\" confluence prod \",\"usernameValue\":\"alice\",\"password\":\"pw\"}]"),
                "Confluence Prod");
        assertEquals("alice", entry.userName());
        assertArrayEquals("pw".toCharArray(), entry.password());
    }

    @Test
    public void fallsBackToFormFieldsAndUsernameField() {
        KeePassEntry entry = KeePassRpcEntries.exactTitleMatch(JsonParser.parseString("[{\"title\":\"Wiki\","
                + "\"formFieldList\":[{\"type\":\"FFTusername\",\"value\":\"bob\"},"
                + "{\"type\":\"FFTpassword\",\"value\":\"pw\"}]}]"), "Wiki");
        assertEquals("bob", entry.userName());
        assertArrayEquals("pw".toCharArray(), entry.password());

        KeePassEntry legacy = KeePassRpcEntries.exactTitleMatch(JsonParser.parseString(
                "[{\"title\":\"Wiki\",\"username\":\"carol\"}]"), "Wiki");
        assertEquals("carol", legacy.userName());
        assertEquals(0, legacy.password().length);
    }

    @Test
    public void noMatchOrNoArrayGivesNull() {
        assertNull(KeePassRpcEntries.exactTitleMatch(JsonParser.parseString("[{\"title\":\"A\"}]"), "B"));
        assertNull(KeePassRpcEntries.exactTitleMatch(JsonParser.parseString("{}"), "B"));
        assertNull(KeePassRpcEntries.exactTitleMatch(null, "B"));
    }

    @Test
    public void closeWipesThePassword() {
        KeePassEntry entry = new KeePassEntry("A", "u", "pw".toCharArray());
        entry.close();
        assertArrayEquals(new char[] {'\0', '\0'}, entry.password());
        assertEquals("KeePassEntry[A, ***]", entry.toString());
    }
}
