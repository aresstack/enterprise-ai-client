package com.aresstack.enterpriseai.acp.research;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/** STDOUT des Agenten ist reines ASCII; Nicht-ASCII wird zu JSON-Escapes, die jede Kodierung gleich liest. */
public class AsciiJsonOutputStreamTest {

    private static String through(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AsciiJsonOutputStream out = new AsciiJsonOutputStream(bytes);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
    }

    @Test
    public void umlautsBecomeUnicodeEscapes() throws Exception {
        assertEquals("{\"t\":\"Gr\\u00fc\\u00dfe \\u00c4\\u00d6\\u00dc\"}", through("{\"t\":\"Grüße ÄÖÜ\"}"));
    }

    @Test
    public void typographicCharactersAndSupplementaryCharactersSurvive() throws Exception {
        assertEquals("a\\u2011b\\u2026\\u20ac", through("a‑b…€"));
        assertEquals("\\ud83d\\ude00", through(new String(Character.toChars(0x1F600))));
    }

    @Test
    public void asciiIsUntouched() throws Exception {
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1}\n", through("{\"jsonrpc\":\"2.0\",\"id\":1}\n"));
    }

    @Test
    public void splitWritesOfOneCharacterAreJoined() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AsciiJsonOutputStream out = new AsciiJsonOutputStream(bytes);
        byte[] umlaut = "ü".getBytes(StandardCharsets.UTF_8);
        out.write(umlaut[0]);
        out.write(umlaut[1]);
        out.flush();
        assertEquals("\\u00fc", new String(bytes.toByteArray(), StandardCharsets.US_ASCII));
    }
}
