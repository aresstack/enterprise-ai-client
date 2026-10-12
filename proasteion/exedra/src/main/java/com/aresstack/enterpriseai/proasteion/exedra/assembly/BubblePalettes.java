package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;

import java.awt.Color;

/**
 * Baut aus den Einstellungen {@code chat.bubble.*} die {@link BubblePalette} des Verlaufs: Nutzer- und
 * Assistentenblasen in den gewählten Farben, alles andere (Wolken, Fehler, Hinweise) wie im Standard.
 */
public final class BubblePalettes {

    private BubblePalettes() {
    }

    public static BubblePalette from(BubbleColorsConfig colors) {
        BubbleColorsConfig c = colors == null ? BubbleColorsConfig.defaults() : colors;
        return BubblePalette.windowsPhoneInspired().withConversationColors(
                new Color(c.userBackground()), new Color(c.userForeground()),
                new Color(c.assistantBackground()), new Color(c.assistantForeground()));
    }
}
