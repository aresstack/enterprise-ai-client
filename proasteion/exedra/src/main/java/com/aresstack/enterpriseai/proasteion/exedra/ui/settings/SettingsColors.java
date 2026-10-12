package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;

import java.awt.Color;

/** Zwischen {@code #RRGGBB} im Formular und {@link Color} in den Feldern des Dialogs. */
final class SettingsColors {

    private SettingsColors() {
    }

    static String hex(Color color) {
        return BubbleColorsConfig.hex(color.getRGB());
    }

    /** Die Farbe zu {@code #RRGGBB} oder {@code null}, wenn der Text keine ist. */
    static Color parse(String text) {
        Integer rgb = BubbleColorsConfig.parseHex(text);
        return rgb == null ? null : new Color(rgb);
    }
}
