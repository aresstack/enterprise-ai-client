package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.application.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reiter „Modelle“ (wie die zentrale Modellauswahl je Funktion in askai-java8 arch): je Kategorie genau eine
 * Auswahl, gleich ob das Modell von der Enterprise-API (KIPITZ, {@code GET /models}) oder vom optionalen lokalen
 * Java-21-Sidecar kommt. Die Listen zeigen nur passende Modelle; die Liste kommt zuerst aus dem Zwischenspeicher
 * der letzten Abfrage und wird beim ersten Öffnen des Reiters sowie mit „Modelle aktualisieren“ im Hintergrund
 * neu geholt. Darunter die Pfade des lokalen Sidecars.
 */
final class ModelsTab {

    static final String REFRESH_LABEL = "Modelle aktualisieren";
    static final String REFRESHING_LABEL = "Frage Modellquellen ab …";
    static final String EMPTY_STATUS = "Noch keine Modellliste. „Modelle aktualisieren“ fragt GET /models ab.";

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final ComicPalette palette;
    private final List<CategoryModelRow> rows = new ArrayList<CategoryModelRow>();
    private final JPanel statusLines = new JPanel();
    private final ComicButton refresh;
    private final JavaRuntimeRow localJava;
    private final JTextField localSidecarJar;
    private final JLabel detectedSidecarJar;
    private final JTextField localModelRoot;
    private final JPanel panel;
    private boolean refreshedOnce;

    ModelsTab(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.palette = palette;

        FormRows selection = new FormRows(palette);
        selection.note("Je Funktion ein Modell. Die Listen zeigen nur Modelle, deren gemeldete Fähigkeiten passen; "
                + "Modelle der Enterprise-API und lokale Modelle stehen gemeinsam darin. Eine Auswahl bleibt "
                + "erhalten, auch wenn ihre Quelle gerade nicht erreichbar ist.");
        for (ModelCategory category : ModelCategory.values()) {
            boolean text = category == ModelCategory.CHAT || category == ModelCategory.EMBEDDING;
            rows.add(new CategoryModelRow(selection, category, text, actions, palette));
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

        FormRows local = new FormRows(palette);
        local.note("Optional: lokale Modelle über den Java-21-Sidecar (local-model-runtime-sidecar-java21). Java 21 "
                + "oder neuer wird automatisch gefunden; ohne Java 21 bleiben lokale Modelle aus, die Modelle der "
                + "Enterprise-API funktionieren unabhängig davon. Es wird nichts heruntergeladen oder installiert.");
        localJava = new JavaRuntimeRow(local, actions, palette);
        localSidecarJar = local.textField("Sidecar-Jar (optional)", "Pfad zu local-model-runtime-sidecar.jar; leer = "
                + "automatisch neben dem Client, im Anwendungs- oder Modellverzeichnis suchen");
        detectedSidecarJar = local.note(" ");
        localModelRoot = local.textField("Modellverzeichnis (optional)",
                "Verzeichnis der lokal installierten Modelle; leer = <Anwendungsverzeichnis>/local-models");

        panel = FormRows.column(palette,
                FormRows.plate("Modelle je Funktion", palette.getNavigationBlue(), selection.panel(), palette),
                FormRows.plate("Modellquellen", palette.getAgentPetrol(), sources.panel(), palette),
                FormRows.plate("Lokale Modelle", palette.getAccentOrange(), local.panel(), palette));
        apply(actions.cachedModels());
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        for (CategoryModelRow row : rows) {
            row.load(valueOf(form, row.category()));
        }
        localJava.load(form.localJava());
        localSidecarJar.setText(form.localSidecarJar());
        showDetectedSidecarJar(form);
        localModelRoot.setText(form.localModelRoot());
    }

    void store(SettingsForm.Builder b) {
        for (CategoryModelRow row : rows) {
            switch (row.category()) {
                case CHAT:
                    b.chatModel(row.stored());
                    break;
                case EMBEDDING:
                    b.embeddingModel(row.stored());
                    break;
                default:
                    b.modelSelection(row.category().key(), row.stored());
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
        localJava.shown();
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
        JLabel label = new JLabel(text);
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

    CategoryModelRow row(ModelCategory category) {
        for (CategoryModelRow row : rows) {
            if (row.category() == category) {
                return row;
            }
        }
        throw new IllegalArgumentException("unknown category " + category);
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
