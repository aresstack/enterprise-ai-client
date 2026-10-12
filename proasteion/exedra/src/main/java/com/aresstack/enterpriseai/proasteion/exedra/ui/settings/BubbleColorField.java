package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Eine Farbe im Einstellungen-Dialog: Farbfeld und editierbarer Hex-Wert ({@code #RRGGBB}). Ein Klick aufs Farbfeld
 * öffnet eine kleine Comic-Auswahl mit Vorschlägen und „Weitere Farben …“ ({@link JColorChooser}). Ein ungültiger
 * Hex-Wert färbt das Feld rot und ändert die Farbe nicht; gespeichert wird immer die zuletzt gültige.
 */
final class BubbleColorField extends JPanel {

    static final String MORE_COLORS_LABEL = "Weitere Farben …";
    private static final int HEX_COLUMNS = 8;
    private static final int PRESET_COLUMNS = 6;
    private static final int GAP = 6;
    private static final int POPUP_PADDING = 8;

    private final ComicPalette palette;
    private final String name;
    private final ColorSwatch swatch;
    private final JTextField hexField = new JTextField(HEX_COLUMNS);
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private Color color;
    private boolean valid = true;
    private boolean updating;

    BubbleColorField(String name, Color initial, ComicPalette palette) {
        super(new FlowLayout(FlowLayout.LEFT, GAP, 0));
        this.palette = palette;
        this.name = name;
        this.color = initial;
        this.swatch = new ColorSwatch(initial, palette);
        setOpaque(false);
        swatch.getAccessibleContext().setAccessibleName(name + ": Farbe wählen");
        swatch.addActionListener(event -> showChoices());
        hexField.getAccessibleContext().setAccessibleName(name + ": Hex-Wert");
        hexField.setForeground(palette.getInk());
        hexField.setCaretColor(palette.getInk());
        hexField.setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        hexField.setOpaque(true);
        hexField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                hexEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                hexEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                hexEdited();
            }
        });
        add(swatch);
        add(hexField);
        showHex(SettingsColors.hex(initial));
    }

    /** Die zuletzt gültige Farbe. */
    Color color() {
        return color;
    }

    /** Ob der Hex-Wert im Feld eine Farbe ist. */
    boolean isValidInput() {
        return valid;
    }

    /** Der Text im Hex-Feld (für Tests). */
    JTextField hexField() {
        return hexField;
    }

    ColorSwatch swatch() {
        return swatch;
    }

    String fieldName() {
        return name;
    }

    /** Setzt die Farbe (Laden, Auswahl, „Standard wiederherstellen“) und meldet die Änderung. */
    void setColor(Color value) {
        if (value == null) {
            return;
        }
        apply(value);
        showHex(SettingsColors.hex(value));
        fire();
    }

    /**
     * Lädt den Text aus der Datei: eine Farbe übernimmt das Feld, sonst bleibt {@code fallback} gültig und der
     * unlesbare Text steht rot im Feld, bis er korrigiert wird.
     */
    void load(String text, Color fallback) {
        Color parsed = SettingsColors.parse(text);
        apply(parsed == null ? fallback : parsed);
        showHex(parsed == null && text != null && !text.trim().isEmpty() ? text.trim() : SettingsColors.hex(color));
        fire();
    }

    /** Wird nach jeder Änderung (auch einer ungültigen Eingabe) gerufen. */
    void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void apply(Color value) {
        color = value;
        swatch.setColor(value);
    }

    private void showHex(String text) {
        updating = true;
        try {
            hexField.setText(text);
        } finally {
            updating = false;
        }
        validateHex();
    }

    private void hexEdited() {
        if (updating) {
            return;
        }
        Color parsed = validateHex();
        if (parsed != null) {
            apply(parsed);
        }
        fire();
    }

    private Color validateHex() {
        Color parsed = SettingsColors.parse(hexField.getText());
        valid = parsed != null;
        hexField.setBorder(valid ? ComicBorder.roundedBorder(palette, 4)
                : BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ResearchUiPalette.DANGER_RED, 2),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        hexField.setToolTipText(valid ? name + " als #RRGGBB"
                : "Keine Farbe: erwartet wird #RRGGBB, z. B. #FFFFFF für Weiß");
        return parsed;
    }

    private void fire() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /** Vorschläge und „Weitere Farben …“ als Comic-Popup unter dem Farbfeld. */
    private void showChoices() {
        final JPopupMenu popup = new JPopupMenu();
        popup.setBorder(ComicBorder.popupBorder(palette));
        JPanel content = new JPanel(new BorderLayout(0, GAP));
        content.setBackground(palette.getSurface());
        content.setBorder(BorderFactory.createEmptyBorder(POPUP_PADDING, POPUP_PADDING, POPUP_PADDING,
                POPUP_PADDING));
        JPanel grid = new JPanel(new GridLayout(0, PRESET_COLUMNS, 4, 4));
        grid.setOpaque(false);
        for (final Color preset : presets()) {
            ColorSwatch choice = ColorSwatch.small(preset, palette);
            choice.getAccessibleContext().setAccessibleName(SettingsColors.hex(preset));
            choice.addActionListener(event -> {
                popup.setVisible(false);
                setColor(preset);
            });
            grid.add(choice);
        }
        content.add(grid, BorderLayout.CENTER);
        ComposerButton more = new ComposerButton(null, MORE_COLORS_LABEL, false,
                "Eine beliebige Farbe im Farbwähler aussuchen");
        more.addActionListener(event -> {
            popup.setVisible(false);
            Color chosen = JColorChooser.showDialog(this, name, color);
            if (chosen != null) {
                setColor(chosen);
            }
        });
        content.add(more, BorderLayout.SOUTH);
        popup.add(content);
        popup.show(swatch, 0, swatch.getHeight());
    }

    /**
     * Vorschläge: Weiß, Schwarz und Grautöne für gut lesbare Blasen, die bisherigen Blasenfarben und die
     * Akzentfarben der Oberfläche.
     */
    static List<Color> presets() {
        return Arrays.asList(
                Color.WHITE,
                ResearchUiPalette.CLOUD_SURFACE,
                ResearchUiPalette.LIGHT_CONTROL_BORDER,
                ResearchUiPalette.LIGHT_TEXT_MUTED,
                ResearchUiPalette.LIGHT_CONTROL_TEXT,
                Color.BLACK,
                new Color(BubbleColorsConfig.DEFAULT_USER_BACKGROUND),
                new Color(BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND),
                ResearchUiPalette.ACCENT_BLUE,
                ResearchUiPalette.WAVE_GREEN,
                ResearchUiPalette.DANGER_RED,
                ResearchUiPalette.RECORDING_ORANGE,
                ResearchUiPalette.TAG_SUGGESTION_SURFACE,
                ResearchUiPalette.RECORDING_SURFACE,
                ResearchUiPalette.SKY_BAR_SURFACE,
                ResearchUiPalette.TEXT_DARK,
                ResearchUiPalette.SECONDARY_SURFACE,
                ResearchUiPalette.DARK_BASE);
    }
}
