package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.application.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.control.ComposerToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reiter „Modelle“ (wie die zentrale Modellauswahl je Funktion in askai-java8 arch), oben zwei Unterreiter nach
 * Modellquelle: „Cloud-Modelle“ (Enterprise-API, {@code GET /models}) und „Lokale Modelle“ (Chat, Embeddings und
 * Stimmen, die im Client über ONNX Runtime laufen, Hugging-Face-Suche und Modellverzeichnis). Je Kategorie gibt es
 * genau eine Auswahl ({@link ModelSelection}); beide Unterreiter sind Sichten darauf, wer in einem wählt, wählt im
 * anderen ab. Die Listen zeigen nur passende Modelle; sie kommen zuerst aus dem Zwischenspeicher der letzten
 * Abfrage und werden beim ersten Öffnen des Reiters sowie mit „Modelle aktualisieren“ im Hintergrund neu geholt.
 */
final class ModelsTab {

    static final String REFRESH_LABEL = "Modelle aktualisieren";
    static final String REFRESHING_LABEL = "Frage Modellquellen ab …";
    static final String EMPTY_STATUS = "Noch keine Modellliste. „Modelle aktualisieren“ fragt GET /models ab.";
    static final String CLOUD_PAGE = "Cloud-Modelle";
    static final String LOCAL_PAGE = "Lokale Modelle";
    /** Funktionen, für die der Client Modelle im Prozess rechnet. */
    private static final ModelCategory[] LOCAL_CATEGORIES = {ModelCategory.CHAT, ModelCategory.EMBEDDING,
            ModelCategory.TTS, ModelCategory.STT};

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final ComicPalette palette;
    private final Map<ModelCategory, ModelSelection> selections =
            new EnumMap<ModelCategory, ModelSelection>(ModelCategory.class);
    private final List<CategoryModelRow> rows = new ArrayList<CategoryModelRow>();
    private final List<CategoryModelRow> localRows = new ArrayList<CategoryModelRow>();
    private final JPanel statusLines = new JPanel();
    private final ComicButton refresh;
    private final JTextField localModelRoot;
    private final LocalVoiceList voices;
    private final CardLayout pages = new CardLayout();
    private final JPanel deck = new JPanel(pages);
    private final ComposerToggleButton cloudButton;
    private final ComposerToggleButton localButton;
    private final JPanel panel;
    private boolean refreshedOnce;
    private boolean localShown;

    ModelsTab(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.palette = palette;
        for (ModelCategory category : ModelCategory.values()) {
            selections.put(category, new ModelSelection());
        }

        FormRows selection = new FormRows(palette);
        selection.note("Je Funktion ein Modell der Enterprise-API. Die Listen zeigen nur Modelle, deren gemeldete "
                + "Fähigkeiten passen. Eine Wahl unter „Lokale Modelle“ ersetzt die Wahl hier und umgekehrt.");
        for (ModelCategory category : ModelCategory.values()) {
            boolean editable = category == ModelCategory.CHAT || category == ModelCategory.EMBEDDING;
            rows.add(new CategoryModelRow(selection, category, false, editable, selections.get(category), actions,
                    palette));
        }

        FormRows sources = new FormRows(palette);
        refresh = new ComicButton(REFRESH_LABEL, null, ComicButton.Accent.ACTION, palette);
        refresh.setToolTipText("Fragt mit dem aktuellen Entwurf GET /models der Enterprise-API ab");
        refresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        statusLines.setOpaque(false);
        statusLines.setLayout(new BoxLayout(statusLines, BoxLayout.Y_AXIS));
        JPanel sourceRow = new JPanel(new BorderLayout(0, 4));
        sourceRow.setOpaque(false);
        sourceRow.add(refresh, BorderLayout.NORTH);
        sourceRow.add(statusLines, BorderLayout.CENTER);
        sources.component(null, sourceRow, null);

        FormRows localSelection = new FormRows(palette);
        localSelection.note("Installierte Modelle je Funktion, gerechnet im Client über ONNX Runtime; eine Wahl hier "
                + "ersetzt die Wahl unter „Cloud-Modelle“ und umgekehrt. Ein anderes Embedding-Modell heißt: "
                + "Wissensquellen neu indexieren.");
        for (ModelCategory category : LOCAL_CATEGORIES) {
            localRows.add(new CategoryModelRow(localSelection, category, true, false, selections.get(category),
                    actions, palette));
        }

        voices = new LocalVoiceList(actions, form, new LocalVoiceList.Listener() {
            @Override
            public void offers(List<LocalVoiceOffer> offers) {
                showInstalledVoices(offers);
            }

            @Override
            public void removed(String voiceId) {
                ModelReference removed = ModelsTab.this.actions.parseModel(
                        ModelsTab.this.actions.localVoiceSelection(voiceId));
                for (ModelSelection selection : selections.values()) {
                    if (removed != null && removed.equals(ModelsTab.this.actions.parseModel(selection.get()))) {
                        selection.set("");
                    }
                }
            }
        }, palette);
        FormRows voiceNote = new FormRows(palette);
        voiceNote.note("Installierte Modelle im Modellverzeichnis, geladen vom Hugging Face Hub ohne Umwandlung. Sie "
                + "laufen im Client selbst (ONNX Runtime), ohne externes Programm. Installieren wählt nichts aus: "
                + "gewählt wird oben je Funktion.");
        FormRows suggestionNote = new FormRows(palette);
        suggestionNote.note("Vorgeschlagene Modelle zum Installieren über die Netzwerkeinstellungen: deutsche Stimmen "
                + "(Aussprache regelbasiert) und Gemma 4 E2B für die Spracheingabe. Installiert stehen sie oben.");
        JPanel suggestionContent = new JPanel(new BorderLayout(0, 4));
        suggestionContent.setOpaque(false);
        suggestionContent.add(suggestionNote.panel(), BorderLayout.NORTH);
        suggestionContent.add(voices.suggestionsPanel(), BorderLayout.CENTER);
        FormRows searchNote = new FormRows(palette);
        searchNote.note("Angeboten werden nur Modelle, die der Client ohne Umwandlung lädt: Sprachausgabe als "
                + "Piper-Stimme (.onnx mit .onnx.json, deutsch oder Text-Eingabe) oder VITS (config.json, vocab.json, "
                + "onnx/model.onnx); Embeddings als ONNX-Export mit config.json und tokenizer.json (WordPiece, BPE "
                + "oder Unigram); Chat als Decoder-ONNX-Export mit KV-Cache (past_key_values), config.json und "
                + "tokenizer.json; Spracheingabe als Gemma-Export mit Audio-Eingang (audio_encoder, embed_tokens, "
                + "decoder_model_merged).");
        JPanel searchContent = new JPanel(new BorderLayout(0, 4));
        searchContent.setOpaque(false);
        searchContent.add(searchNote.panel(), BorderLayout.NORTH);
        searchContent.add(voices.searchPanel(), BorderLayout.CENTER);
        JPanel voiceContent = new JPanel(new BorderLayout(0, 4));
        voiceContent.setOpaque(false);
        voiceContent.add(voiceNote.panel(), BorderLayout.NORTH);
        voiceContent.add(voices.panel(), BorderLayout.CENTER);

        FormRows local = new FormRows(palette);
        localModelRoot = local.textField("Modellverzeichnis (optional)",
                "Verzeichnis der lokal installierten Modelle; leer = <Anwendungsverzeichnis>/local-models");

        JPanel cloudPage = FormRows.column(palette,
                FormRows.plate("Modelle je Funktion", palette.getNavigationBlue(), selection.panel(), palette),
                FormRows.plate("Modellquellen", palette.getAgentPetrol(), sources.panel(), palette));
        JPanel localPage = FormRows.column(palette,
                FormRows.plate("Lokale Modelle je Funktion", palette.getNavigationBlue(), localSelection.panel(),
                        palette),
                FormRows.plate("Installiert", palette.getAccentOrange(), voiceContent, palette),
                FormRows.plate("Vorschläge", palette.getAccentOrange(), suggestionContent, palette),
                FormRows.plate("Hugging Face durchsuchen", palette.getAccentOrange(), searchContent, palette),
                FormRows.plate("Modellverzeichnis", palette.getAgentPetrol(), local.panel(), palette));
        deck.setOpaque(false);
        deck.add(cloudPage, CLOUD_PAGE);
        deck.add(localPage, LOCAL_PAGE);

        JPanel switcher = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        switcher.setOpaque(false);
        switcher.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        ButtonGroup group = new ButtonGroup();
        cloudButton = pageButton(CLOUD_PAGE, false, group, switcher);
        localButton = pageButton(LOCAL_PAGE, true, group, switcher);
        cloudButton.setSelected(true);

        panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(palette.getSurface());
        panel.add(switcher, BorderLayout.NORTH);
        panel.add(deck, BorderLayout.CENTER);
        apply(actions.cachedModels());
    }

    private ComposerToggleButton pageButton(String name, final boolean local, ButtonGroup group, JPanel bar) {
        ComposerToggleButton button = new ComposerToggleButton(null, name, null);
        button.setAccent(ResearchUiPalette.SECONDARY_SURFACE);
        button.getAccessibleContext().setAccessibleName("Modelle: " + name);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPage(local);
            }
        });
        group.add(button);
        bar.add(button);
        return button;
    }

    /** Zeigt „Lokale Modelle“ oder „Cloud-Modelle“. */
    void showPage(boolean local) {
        (local ? localButton : cloudButton).setSelected(true);
        pages.show(deck, local ? LOCAL_PAGE : CLOUD_PAGE);
        if (local) {
            localShown = true;
            voices.shown();
        }
    }

    private void showInstalledVoices(List<LocalVoiceOffer> offers) {
        for (ModelCategory category : LOCAL_CATEGORIES) {
            List<ModelOption> installed = new ArrayList<ModelOption>();
            for (LocalVoiceOffer offer : offers) {
                if (offer.isInstalled() && offer.category() == category) {
                    installed.add(ModelOption.installed(actions.localVoiceSelection(offer.id()),
                            offer.displayName()));
                }
            }
            localRow(category).showInstalled(installed);
        }
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        for (Map.Entry<ModelCategory, ModelSelection> entry : selections.entrySet()) {
            entry.getValue().set(valueOf(form, entry.getKey()));
        }
        localModelRoot.setText(form.localModelRoot());
    }

    void store(SettingsForm.Builder b) {
        for (Map.Entry<ModelCategory, ModelSelection> entry : selections.entrySet()) {
            String value = entry.getValue().get();
            switch (entry.getKey()) {
                case CHAT:
                    b.chatModel(value);
                    break;
                case EMBEDDING:
                    b.embeddingModel(value);
                    break;
                default:
                    b.modelSelection(entry.getKey().key(), value);
            }
        }
        b.localModelRoot(localModelRoot.getText());
    }

    /** Beim ersten Anzeigen des Reiters: einmal im Hintergrund abfragen. */
    void shown() {
        if (!refreshedOnce) {
            refresh();
        }
        if (localShown) {
            voices.shown();
        }
    }

    void refresh() {
        refreshedOnce = true;
        refresh.setEnabled(false);
        refresh.setText(REFRESHING_LABEL);
        try {
            actions.refreshModels(form.get(), new Consumer<ModelCatalogSnapshot>() {
                @Override
                public void accept(ModelCatalogSnapshot snapshot) {
                    refresh.setEnabled(true);
                    refresh.setText(REFRESH_LABEL);
                    apply(snapshot);
                }
            });
        } catch (RuntimeException e) {
            refresh.setEnabled(true);
            refresh.setText(REFRESH_LABEL);
            statusLines.removeAll();
            statusLines.add(line("Abfrage konnte nicht gestartet werden: " + e.getClass().getSimpleName(), false));
            panel.revalidate();
            panel.repaint();
        }
    }

    private void apply(ModelCatalogSnapshot snapshot) {
        ModelCatalogSnapshot shown = snapshot == null ? ModelCatalogSnapshot.empty() : snapshot;
        for (CategoryModelRow row : rows) {
            row.show(shown);
        }
        for (CategoryModelRow row : localRows) {
            row.show(shown);
        }
        statusLines.removeAll();
        if (shown.statuses().isEmpty()) {
            statusLines.add(line(EMPTY_STATUS, true));
        }
        for (CatalogStatus status : shown.statuses()) {
            statusLines.add(line((status.available() ? "✓ " : "! ") + status.displayName() + ": " + status.message(),
                    status.available()));
        }
        statusLines.revalidate();
        statusLines.repaint();
    }

    private JLabel line(String text, boolean ok) {
        JLabel label = new WrappingLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, Math.max(11f, label.getFont().getSize2D() - 1f)));
        label.setForeground(ok ? palette.getAgentPetrol() : palette.getAccentOrange());
        label.setToolTipText(text);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    private static String valueOf(SettingsForm form, ModelCategory category) {
        switch (category) {
            case CHAT:
                return form.chatModel();
            case EMBEDDING:
                return form.embeddingModel();
            default:
                return form.modelSelection(category.key());
        }
    }

    /** Die Sicht einer Kategorie unter „Cloud-Modelle“. */
    CategoryModelRow row(ModelCategory category) {
        return find(rows, category);
    }

    /** Die Sicht einer Kategorie unter „Lokale Modelle“ (Chat, Embeddings, TTS). */
    CategoryModelRow localRow(ModelCategory category) {
        return find(localRows, category);
    }

    private static CategoryModelRow find(List<CategoryModelRow> rows, ModelCategory category) {
        for (CategoryModelRow row : rows) {
            if (row.category() == category) {
                return row;
            }
        }
        throw new IllegalArgumentException("no row for " + category);
    }

    /** Die gemeinsame Auswahl einer Kategorie. */
    ModelSelection selection(ModelCategory category) {
        return selections.get(category);
    }

    LocalVoiceList voices() {
        return voices;
    }

    /** Das Eingabefeld der editierbaren Chat-Modell-Liste. */
    JTextField chatModel() {
        return row(ModelCategory.CHAT).field();
    }

    JTextField embeddingModel() {
        return row(ModelCategory.EMBEDDING).field();
    }

    ComicButton refreshButton() {
        return refresh;
    }

    /** Die angezeigten Statuszeilen der Modellquellen. */
    List<String> statusTexts() {
        List<String> texts = new ArrayList<String>();
        for (java.awt.Component component : statusLines.getComponents()) {
            if (component instanceof JLabel) {
                texts.add(((JLabel) component).getText());
            }
        }
        return texts;
    }
}
