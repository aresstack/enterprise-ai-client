package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * Platte „Assistent“: der Name, mit dem Antworten im Verlauf, im Zitat- und im Word-/Markdown-Export überschrieben
 * sind ({@code chat.assistant.displayName}), mit Knopf „Standard“ und einer kleinen Vorschau-Blase. Nur Beschriftung:
 * die Rolle bleibt intern {@code ASSISTANT}, das Modell erfährt den Namen nicht, und ein Wechsel der Stimme ändert ihn
 * nie. Ein leeres Feld gilt beim Speichern als Standard.
 */
final class AssistantNameSection {

    static final String HEADING = "Assistent";
    static final String DEFAULT_LABEL = "Standard";
    static final String PREVIEW_TEXT = "Hallo! Was kann ich für dich tun?";

    private final JTextField name;
    private final ComposerButton reset;
    private final SpeechBubblePanel preview;
    private final JPanel panel;
    private String defaultName = "";
    private Consumer<String> nameListener;

    AssistantNameSection(ComicPalette palette) {
        FormRows rows = new FormRows(palette);
        name = new JTextField(20);
        rows.style(name);
        reset = new ComposerButton(null, DEFAULT_LABEL, false, "Den Standardnamen wieder einsetzen");
        reset.addActionListener(e -> name.setText(defaultName));
        JPanel field = new JPanel(new BorderLayout(8, 0));
        field.setOpaque(false);
        field.add(name, BorderLayout.CENTER);
        field.add(reset, BorderLayout.EAST);
        rows.component("Name", field, "Steht über jeder Antwort im Verlauf und in Exporten "
                + "(chat.assistant.displayName); leer = Standard");
        BubblePalette bubbles = BubblePalette.windowsPhoneInspired();
        preview = new SpeechBubblePanel(BubbleSide.LEFT, bubbles.getAssistantBackground(),
                bubbles.getAssistantForeground(), "", PREVIEW_TEXT);
        JPanel previewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        previewRow.setOpaque(false);
        previewRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        previewRow.add(preview);
        rows.component("Vorschau", previewRow, null);
        rows.note("Der Name gehört dir: ein Wechsel der Stimme unter „Modelle“ ändert ihn nicht. Merlin im "
                + "Einrichtungsassistenten bleibt, wer er ist.");
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
            }
        });
        panel = FormRows.plate(HEADING, palette.getAgentPetrol(), rows.panel(), palette);
    }

    JComponent panel() {
        return panel;
    }

    void load(SettingsForm form) {
        defaultName = form.defaultAssistantDisplayName();
        reset.setEnabled(!defaultName.isEmpty());
        String loaded = form.assistantDisplayName();
        name.setText(loaded.isEmpty() ? defaultName : loaded);
        updatePreview();
    }

    void store(SettingsForm.Builder b) {
        b.assistantDisplayName(name.getText());
    }

    /** Der Name, den die Vorschau gerade zeigt (leeres Feld: der Standard). */
    String previewHeader() {
        return shownName();
    }

    /** Meldet jeden angezeigten Namen weiter (etwa an die Blasen-Vorschau unter „Darstellung“); nur einer. */
    void onNameChanged(Consumer<String> listener) {
        nameListener = listener;
        if (listener != null) {
            listener.accept(shownName());
        }
    }

    JTextField nameField() {
        return name;
    }

    ComposerButton defaultButton() {
        return reset;
    }

    private void updatePreview() {
        String shown = shownName();
        preview.setHeader(shown);
        if (nameListener != null) {
            nameListener.accept(shown);
        }
    }

    private String shownName() {
        String typed = name.getText().trim();
        return typed.isEmpty() ? defaultName : typed;
    }
}
