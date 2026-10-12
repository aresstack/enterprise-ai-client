package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ColorContrast;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Kategorie „Darstellung“: Hintergrund- und Schriftfarbe der eigenen Blasen und der Antwortblasen, mit
 * Live-Vorschau, Kontrastwarnung (warnt nur, Speichern bleibt möglich) und „Standard wiederherstellen“. Die Farben
 * gelten unabhängig vom Hell-/Dunkel-Thema; nach dem Speichern übernimmt der Verlauf sie ohne Neustart.
 */
final class AppearanceTab {

    static final String RESET_LABEL = "Standard wiederherstellen";
    static final String LOW_CONTRAST_WARNING = "Der Kontrast zwischen Hintergrund und Schrift ist sehr gering";
    static final String INVALID_HEX_WARNING = "Ungültiger Farbwert: erwartet wird #RRGGBB (z. B. #FFFFFF). "
            + "Gespeichert wird die zuletzt gültige Farbe.";
    static final String USER_PREVIEW_TEXT = "Und so sehen meine aus.";
    static final String ASSISTANT_PREVIEW_TEXT = "So sieht eine Antwort aus.";
    private static final String USER_HEADER = "Du";
    /** Kopf der Antwort-Vorschau, bis ein Anzeigename geladen ist ({@link #setAssistantName}). */
    static final String ASSISTANT_HEADER = "Assistent";
    private static final int PREVIEW_PADDING = 8;
    private static final int PAIR_GAP = 14;

    private final ComicPalette palette;
    private final BubbleColorField userBackground;
    private final BubbleColorField userForeground;
    private final BubbleColorField assistantBackground;
    private final BubbleColorField assistantForeground;
    private final JPanel preview = new JPanel();
    private final JLabel warning = new WrappingLabel("");
    private final ComposerButton reset = new ComposerButton(null, RESET_LABEL, false,
            "Blau/Weiß für eigene Nachrichten und Petrol/Weiß für den Assistenten");
    private final JPanel panel;
    private SpeechBubblePanel userPreview;
    private SpeechBubblePanel assistantPreview;
    private String assistantName = ASSISTANT_HEADER;

    AppearanceTab(ComicPalette palette) {
        this.palette = palette;
        BubbleColorsConfig defaults = BubbleColorsConfig.defaults();
        userBackground = new BubbleColorField("Eigene Nachrichten, Hintergrund",
                new Color(defaults.userBackground()), palette);
        userForeground = new BubbleColorField("Eigene Nachrichten, Schrift",
                new Color(defaults.userForeground()), palette);
        assistantBackground = new BubbleColorField("Assistent, Hintergrund",
                new Color(defaults.assistantBackground()), palette);
        assistantForeground = new BubbleColorField("Assistent, Schrift",
                new Color(defaults.assistantForeground()), palette);

        FormRows rows = new FormRows(palette);
        rows.note("Farben der Sprechblasen im Verlauf, unabhängig vom Hell-/Dunkel-Thema. Für gute Lesbarkeit "
                + "etwa weiße Blasen mit schwarzer Schrift wählen; Seiten und Schwänze der Blasen bleiben erhalten. "
                + "Gilt nach dem Speichern sofort, auch für Antworten mit Überschriften, Listen, Tabellen und Code.");
        rows.component("Eigene Nachrichten", pair(userBackground, userForeground),
                "Hintergrund (" + BubbleColorsConfig.USER_BACKGROUND_KEY + ") und Schrift samt Kopfzeile und "
                        + "Zeitstempel (" + BubbleColorsConfig.USER_FOREGROUND_KEY + ") der eigenen Blasen");
        rows.component("Assistent", pair(assistantBackground, assistantForeground),
                "Hintergrund (" + BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY + ") und Schrift der Antworten, auch in "
                        + "Überschriften, Listen, Tabellen, Zitaten und Code ("
                        + BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY + ")");

        preview.setLayout(new BoxLayout(preview, BoxLayout.Y_AXIS));
        preview.setOpaque(true);
        preview.setBackground(BubblePalette.windowsPhoneInspired().getTranscriptBackground());
        preview.setBorder(BorderFactory.createCompoundBorder(ComicBorder.roundedBorder(palette, 0),
                BorderFactory.createEmptyBorder(PREVIEW_PADDING, PREVIEW_PADDING, PREVIEW_PADDING, PREVIEW_PADDING)));
        preview.getAccessibleContext().setAccessibleName("Vorschau der Sprechblasen");
        rows.component("Vorschau", preview, "So erscheinen die Blasen im Verlauf");

        warning.setForeground(ResearchUiPalette.DANGER_RED);
        warning.setFont(warning.getFont().deriveFont(Font.BOLD));
        warning.setVisible(false);
        rows.component(null, warning, null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(reset);
        rows.component(null, buttons, null);
        reset.addActionListener(event -> restoreDefaults());

        for (BubbleColorField field : fields()) {
            field.addChangeListener(this::refresh);
        }
        panel = FormRows.column(palette,
                FormRows.plate("Sprechblasen", ResearchUiPalette.ACCENT_BLUE, rows.panel(), palette));
        refresh();
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        setAssistantName(form.assistantDisplayName());
        BubbleColorsConfig defaults = BubbleColorsConfig.defaults();
        userBackground.load(form.bubbleUserBackground(), new Color(defaults.userBackground()));
        userForeground.load(form.bubbleUserForeground(), new Color(defaults.userForeground()));
        assistantBackground.load(form.bubbleAssistantBackground(), new Color(defaults.assistantBackground()));
        assistantForeground.load(form.bubbleAssistantForeground(), new Color(defaults.assistantForeground()));
    }

    /** Schreibt die zuletzt gültigen Farben; ein ungültiger Hex-Text blockiert das Speichern nicht. */
    void store(SettingsForm.Builder b) {
        b.bubbleUserBackground(SettingsColors.hex(userBackground.color()));
        b.bubbleUserForeground(SettingsColors.hex(userForeground.color()));
        b.bubbleAssistantBackground(SettingsColors.hex(assistantBackground.color()));
        b.bubbleAssistantForeground(SettingsColors.hex(assistantForeground.color()));
    }

    /**
     * Der Name über der Antwort-Vorschau ({@code chat.assistant.displayName}); folgt dem Feld unter „Sprachausgabe“,
     * während der Dialog offen ist. Leer heißt {@link #ASSISTANT_HEADER}.
     */
    void setAssistantName(String name) {
        assistantName = name == null || name.trim().isEmpty() ? ASSISTANT_HEADER : name.trim();
        if (assistantPreview != null) {
            assistantPreview.setHeader(assistantName);
        }
    }

    String assistantName() {
        return assistantName;
    }

    /** Die vier bisherigen Farben (Blau/Weiß, Petrol/Weiß). */
    void restoreDefaults() {
        BubbleColorsConfig defaults = BubbleColorsConfig.defaults();
        userBackground.setColor(new Color(defaults.userBackground()));
        userForeground.setColor(new Color(defaults.userForeground()));
        assistantBackground.setColor(new Color(defaults.assistantBackground()));
        assistantForeground.setColor(new Color(defaults.assistantForeground()));
    }

    BubbleColorField userBackground() {
        return userBackground;
    }

    BubbleColorField userForeground() {
        return userForeground;
    }

    BubbleColorField assistantBackground() {
        return assistantBackground;
    }

    BubbleColorField assistantForeground() {
        return assistantForeground;
    }

    ComposerButton resetButton() {
        return reset;
    }

    /** Die Warnung unter der Vorschau (für Tests); unsichtbar, solange alles lesbar ist. */
    JLabel warning() {
        return warning;
    }

    /** Die Vorschaufläche mit beiden Blasen (für Tests). */
    JPanel preview() {
        return preview;
    }

    SpeechBubblePanel userPreview() {
        return userPreview;
    }

    SpeechBubblePanel assistantPreview() {
        return assistantPreview;
    }

    private List<BubbleColorField> fields() {
        List<BubbleColorField> fields = new ArrayList<BubbleColorField>();
        fields.add(userBackground);
        fields.add(userForeground);
        fields.add(assistantBackground);
        fields.add(assistantForeground);
        return fields;
    }

    /** „Hintergrund [■ #RRGGBB]  Schrift [■ #RRGGBB]“ in einer Zeile. */
    private JPanel pair(BubbleColorField background, BubbleColorField foreground) {
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.setOpaque(false);
        line.add(caption("Hintergrund", background));
        line.add(background);
        line.add(Box.createHorizontalStrut(PAIR_GAP));
        line.add(caption("Schrift", foreground));
        line.add(foreground);
        return line;
    }

    private JLabel caption(String text, BubbleColorField field) {
        JLabel label = new JLabel(text);
        label.setForeground(palette.getInk());
        label.setLabelFor(field.hexField());
        return label;
    }

    /** Vorschau neu aufbauen (die Blasenfarben sind fest je Blase) und die Warnungen prüfen. */
    private void refresh() {
        assistantPreview = new SpeechBubblePanel(BubbleSide.LEFT, assistantBackground.color(),
                assistantForeground.color(), assistantName, ASSISTANT_PREVIEW_TEXT);
        userPreview = new SpeechBubblePanel(BubbleSide.RIGHT, userBackground.color(), userForeground.color(),
                USER_HEADER, USER_PREVIEW_TEXT);
        preview.removeAll();
        preview.add(row(assistantPreview, BubbleSide.LEFT));
        preview.add(Box.createVerticalStrut(PREVIEW_PADDING));
        preview.add(row(userPreview, BubbleSide.RIGHT));
        preview.revalidate();
        preview.repaint();

        List<String> problems = new ArrayList<String>();
        if (!ColorContrast.isReadable(userForeground.color(), userBackground.color())) {
            problems.add(LOW_CONTRAST_WARNING + " (eigene Nachrichten).");
        }
        if (!ColorContrast.isReadable(assistantForeground.color(), assistantBackground.color())) {
            problems.add(LOW_CONTRAST_WARNING + " (Assistent).");
        }
        for (BubbleColorField field : fields()) {
            if (!field.isValidInput()) {
                problems.add(field.fieldName() + ": " + INVALID_HEX_WARNING);
            }
        }
        warning.setText(FormRows.flow(String.join(" ", problems)));
        warning.setVisible(!problems.isEmpty());
        warning.revalidate();
    }

    /** Eine Zeile der Vorschau: die Blase in ihrer natürlichen Größe links bzw. rechts, wie im Verlauf. */
    private static JComponent row(SpeechBubblePanel bubble, BubbleSide side) {
        PreviewRow row = new PreviewRow(bubble, side);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return row;
    }

    /** Hält eine Vorschau-Blase auf ihrer natürlichen Breite und der Höhe, die sie bei dieser Breite braucht. */
    private static final class PreviewRow extends JPanel {

        private final SpeechBubblePanel bubble;
        private final BubbleSide side;

        PreviewRow(SpeechBubblePanel bubble, BubbleSide side) {
            super(null);
            this.bubble = bubble;
            this.side = side;
            setOpaque(false);
            add(bubble);
        }

        private Dimension bubbleSize() {
            int width = bubble.getPreferredSize().width;
            return new Dimension(width, bubble.preferredHeightForWidth(width));
        }

        @Override
        public Dimension getPreferredSize() {
            return bubbleSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return bubbleSize(); // die Formular-Spalte fällt bei Platzmangel auf Mindestgrößen zurück
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, bubbleSize().height);
        }

        @Override
        public void doLayout() {
            Dimension size = bubbleSize();
            int x = side == BubbleSide.RIGHT ? Math.max(0, getWidth() - size.width) : 0;
            bubble.setBounds(x, 0, size.width, size.height);
        }
    }

    /** Die Warnung als reiner Text (für Tests). */
    String warningText() {
        return warning.isVisible() ? warning.getText().replaceAll("<[^>]+>", "") : "";
    }
}
