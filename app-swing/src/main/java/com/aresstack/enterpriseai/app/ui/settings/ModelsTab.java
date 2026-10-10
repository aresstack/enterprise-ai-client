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
 * Modellquelle: „Cloud-Modelle“ (Enterprise-API, {@code GET /models}) und „Lokale Modelle“ (optionaler
 * Java-21-Sidecar mit installierten Hugging-Face-Stimmen, Laufzeit und Modellverzeichnis). Je Kategorie gibt es
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

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final ComicPalette palette;
    private final Map<ModelCategory, ModelSelection> selections =
            new EnumMap<ModelCategory, ModelSelection>(ModelCategory.class);
    private final List<CategoryModelRow> rows = new ArrayList<CategoryModelRow>();
    private final List<CategoryModelRow> localRows = new ArrayList<CategoryModelRow>();
    private final JPanel statusLines = new JPanel();
    private final ComicButton refresh;
    private final JavaRuntimeRow localJava;
    private final JTextField localSidecarJar;
    private final JLabel detectedSidecarJar;
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
            boolean text = category == ModelCategory.CHAT || category == ModelCategory.EMBEDDING;
            rows.add(new CategoryModelRow(selection, category, false, text, selections.get(category), actions,
                    palette));
        }

        FormRows sources = new FormRows(palette);
        refresh = new ComicButton(REFRESH_LABEL, null, ComicButton.Accent.ACTION, palette);
        refresh.setToolTipText("Fragt mit dem aktuellen Entwurf GET /models der Enterprise-API und, falls "
                + "konfiguriert, den lokalen Sidecar ab");
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
        localSelection.note("Je Funktion ein Modell des lokalen Sidecars. Installierte Stimmen stehen unter TTS; "
                + "eine Wahl hier ersetzt die Wahl unter „Cloud-Modelle“ und umgekehrt.");
        for (ModelCategory category : new ModelCategory[] {ModelCategory.CHAT, ModelCategory.EMBEDDING,
                ModelCategory.TTS}) {
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
                ModelSelection tts = selections.get(ModelCategory.TTS);
                ModelReference removed = ModelsTab.this.actions.parseModel(
                        ModelsTab.this.actions.localVoiceSelection(voiceId));
                if (removed != null && removed.equals(ModelsTab.this.actions.parseModel(tts.get()))) {
                    tts.set("");
                }
            }
        }, palette);
        FormRows voiceNote = new FormRows(palette);
        voiceNote.note("Stimmen für den lokalen Sidecar, geladen vom Hugging Face Hub über die Netzwerkeinstellungen "
                + "ins Modellverzeichnis. Ohne externes Programm; die deutsche Aussprache ist regelbasiert. "
                + "Installieren wählt nichts aus: gewählt wird unter TTS oben.");
        JPanel voiceContent = new JPanel(new BorderLayout(0, 4));
        voiceContent.setOpaque(false);
        voiceContent.add(voiceNote.panel(), BorderLayout.NORTH);
        voiceContent.add(voices.panel(), BorderLayout.CENTER);

        FormRows local = new FormRows(palette);
        local.note("Optional: lokale Modelle über den Java-21-Sidecar (local-model-runtime-sidecar-java21). Java 21 "
                + "oder neuer wird automatisch gefunden; ohne Java 21 bleiben lokale Modelle aus, die Cloud-Modelle "
                + "funktionieren unabhängig davon.");
        localJava = new JavaRuntimeRow(local, actions, palette);
        localSidecarJar = local.textField("Sidecar-Jar (optional)", "Pfad zu local-model-runtime-sidecar.jar; leer = "
                + "automatisch neben dem Client, im Anwendungs- oder Modellverzeichnis suchen");
        detectedSidecarJar = local.note(" ");
        localModelRoot = local.textField("Modellverzeichnis (optional)",
                "Verzeichnis der lokal installierten Modelle; leer = <Anwendungsverzeichnis>/local-models");

        JPanel cloudPage = FormRows.column(palette,
                FormRows.plate("Modelle je Funktion", palette.getNavigationBlue(), selection.panel(), palette),
                FormRows.plate("Modellquellen", palette.getAgentPetrol(), sources.panel(), palette));
        JPanel localPage = FormRows.column(palette,
                FormRows.plate("Lokale Modelle je Funktion", palette.getNavigationBlue(), localSelection.panel(),
                        palette),
                FormRows.plate("Lokale Stimmen", palette.getAccentOrange(), voiceContent, palette),
                FormRows.plate("Laufzeit", palette.getAgentPetrol(), local.panel(), palette));
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
            localJava.shown();
            voices.shown();
        }
    }

    private void showInstalledVoices(List<LocalVoiceOffer> offers) {
        List<ModelOption> installed = new ArrayList<ModelOption>();
        for (LocalVoiceOffer offer : offers) {
            if (offer.isInstalled()) {
                installed.add(ModelOption.installed(actions.localVoiceSelection(offer.id()), offer.displayName()));
            }
        }
        localRow(ModelCategory.TTS).showInstalled(installed);
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        for (Map.Entry<ModelCategory, ModelSelection> entry : selections.entrySet()) {
            entry.getValue().set(valueOf(form, entry.getKey()));
        }
        localJava.load(form.localJava());
        localSidecarJar.setText(form.localSidecarJar());
        showDetectedSidecarJar(form);
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
        b.localJava(localJava.stored()).localSidecarJar(localSidecarJar.getText())
                .localModelRoot(localModelRoot.getText());
    }

    private void showDetectedSidecarJar(SettingsForm form) {
        if (!form.localSidecarJar().trim().isEmpty()) {
            detectedSidecarJar.setText("Eingetragener Pfad übersteuert die automatische Suche.");
            return;
        }
        String found = actions.detectedSidecarJar(form.localModelRoot());
        detectedSidecarJar.setText(found.isEmpty()
                ? "Kein Sidecar-Jar gefunden: Release-Zip neben dem Client entpacken oder Pfad eintragen."
                : "✓ Automatisch gefunden: " + found);
        detectedSidecarJar.setToolTipText(found.isEmpty() ? null : found);
    }

    /** Beim ersten Anzeigen des Reiters: einmal im Hintergrund abfragen. */
    void shown() {
        if (!refreshedOnce) {
            refresh();
        }
        if (localShown) {
            localJava.shown();
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

    /** Das Textfeld des Chat-Modells. */
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
