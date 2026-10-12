package com.aresstack.enterpriseai.proasteion.application.config;

import org.junit.Test;

import java.io.StringReader;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** {@code chat.bubble.*}: Vorgaben ohne Schlüssel, eigene Farben, unlesbare Werte halten den Start nicht auf. */
public class BubbleColorsConfigTest {

    private static Properties minimal(String extra) throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(""
                + "chat.baseUrl=https://ki.example/v1\n"
                + "chat.model=test-chat\n"
                + "chat.apiKeyRef=Enterprise AI API\n"
                + "embedding.model=test-embedding\n"
                + "embedding.dimension=8\n"
                + "security.keepass.enabled=false\n"
                + extra));
        return p;
    }

    @Test
    public void oldPropertiesWithoutBubbleKeysKeepTheFormerColors() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(minimal(""));
        BubbleColorsConfig colors = config.bubbleColors();
        assertEquals(0x1676D2, colors.userBackground());
        assertEquals(0xFFFFFF, colors.userForeground());
        assertEquals(0x15827A, colors.assistantBackground());
        assertEquals(0xFFFFFF, colors.assistantForeground());
        assertEquals(BubbleColorsConfig.defaults(), colors);
        for (String warning : config.warnings()) {
            assertTrue(warning, !warning.contains("chat.bubble"));
        }
    }

    @Test
    public void whiteBubblesWithBlackTextAreRead() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(minimal(""
                + "chat.bubble.user.background=#FFFFFF\n"
                + "chat.bubble.user.foreground=#000000\n"
                + "chat.bubble.assistant.background=ffffff\n"
                + "chat.bubble.assistant.foreground=#000000\n"));
        assertEquals(new BubbleColorsConfig(0xFFFFFF, 0x000000, 0xFFFFFF, 0x000000), config.bubbleColors());
        assertEquals(config.warnings().toString(), 0, countContaining(config, "chat.bubble"));
    }

    @Test
    public void invalidHexFallsBackToTheDefaultWithAWarning() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(minimal(""
                + "chat.bubble.user.background=blau\n"
                + "chat.bubble.assistant.foreground=#12345G\n"));
        assertEquals(BubbleColorsConfig.DEFAULT_USER_BACKGROUND, config.bubbleColors().userBackground());
        assertEquals(BubbleColorsConfig.DEFAULT_ASSISTANT_FOREGROUND, config.bubbleColors().assistantForeground());
        assertEquals(config.warnings().toString(), 2, countContaining(config, "#RRGGBB"));
    }

    @Test
    public void hexIsParsedStrictlyAndWrittenUpperCase() {
        assertEquals(Integer.valueOf(0x1676D2), BubbleColorsConfig.parseHex("#1676d2"));
        assertEquals(Integer.valueOf(0x1676D2), BubbleColorsConfig.parseHex(" 1676D2 "));
        assertNull(BubbleColorsConfig.parseHex(null));
        assertNull(BubbleColorsConfig.parseHex(""));
        assertNull(BubbleColorsConfig.parseHex("#FFF"));
        assertNull(BubbleColorsConfig.parseHex("#FFFFFFF"));
        assertNull(BubbleColorsConfig.parseHex("#GGGGGG"));
        assertNull(BubbleColorsConfig.parseHex("-12345"));
        assertEquals("#15827A", BubbleColorsConfig.hex(0x15827A));
        assertEquals("#000000", BubbleColorsConfig.hex(0xFF000000));
    }

    private static int countContaining(AppConfig config, String text) {
        int count = 0;
        for (String warning : config.warnings()) {
            if (warning.contains(text)) {
                count++;
            }
        }
        return count;
    }
}
