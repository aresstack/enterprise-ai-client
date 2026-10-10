package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sicht auf die Auswahl einer {@link ModelCategory} in einem der Reiter „Cloud-Modelle“ oder „Lokale Modelle“:
 * eine Liste nur der passenden Modelle dieses Reiters. Beide Reiter teilen eine {@link ModelSelection}; liegt die
 * Auswahl im anderen Reiter, steht hier „– lokales Modell gewählt –“ bzw. „– Cloud-Modell gewählt –“, und wer hier
 * ein Modell wählt, wählt das andere ab. Chat und Embeddings haben in „Cloud-Modelle“ zusätzlich ein Textfeld, damit
 * sie auch ohne Verbindung (Erststart) gesetzt werden können; dort ist das Feld der gespeicherte Wert und die Liste
 * schreibt hinein. Eine gespeicherte Auswahl, die der Katalog gerade nicht kennt, bleibt als „nicht im Katalog“
 * stehen. Ohne passende Modelle ist die Liste gesperrt: „kein Modell verfügbar“.
 */
final class CategoryModelRow {

    static final String NO_SELECTION = "– keine Auswahl –";
    static final String NOTHING_AVAILABLE = "kein Modell verfügbar";
    static final String LOCAL_ELSEWHERE = "– lokales Modell gewählt (Reiter „Lokale Modelle“) –";
    static final String CLOUD_ELSEWHERE = "– Cloud-Modell gewählt (Reiter „Cloud-Modelle“) –";

    private final ModelCategory category;
    private final boolean local;
    private final boolean required;
    private final ModelSelection selection;
    private final SettingsDialogActions actions;
    private final JComboBox<ModelOption> combo = FormRows.<ModelOption>fittingComboBox();
    private final JTextField field;
    private boolean filling;
    private boolean syncing;
    private ModelCatalogSnapshot snapshot = ModelCatalogSnapshot.empty();
    private List<ModelOption> installed = Collections.emptyList();

    /**
     * @param local    Sicht im Reiter „Lokale Modelle“ (nur lokale Modelle), sonst „Cloud-Modelle“
     * @param withTextField nur Cloud: Textfeld für die Modellkennung (Chat, Embeddings)
     */
    CategoryModelRow(FormRows rows, final ModelCategory category, boolean local, boolean withTextField,
                     ModelSelection selection, SettingsDialogActions actions, ComicPalette palette) {
        this.category = category;
        this.local = local;
        this.required = category == ModelCategory.CHAT || category == ModelCategory.EMBEDDING;
        this.selection = selection;
        this.actions = actions;
        combo.setForeground(palette.getInk());
        combo.setBackground(Color.WHITE);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                // Modellnamen kommen vom Dienst: nie als Swing-HTML deuten.
                label.putClientProperty("html.disable", Boolean.TRUE);
                return label;
            }
        });
        combo.getAccessibleContext().setAccessibleName((local ? "Lokales Modell für " : "Cloud-Modell für ")
                + category.displayName());
        String source = local ? "Lokale Modelle des Sidecars und installierte Stimmen"
                : "Passende Modelle aus GET /models der Enterprise-API";
        if (withTextField && !local) {
            field = rows.textField(category.displayName(), "Modellkennung, wie sie der Dienst nennt; optional mit "
                    + "Katalog davor (local:…). Die Liste darunter setzt sie.");
            rows.component("", combo, source);
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    typed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    typed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    typed();
                }
            });
        } else {
            field = null;
            rows.component(category.displayName(), combo, source);
        }
        combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object selected = combo.getSelectedItem();
                if (filling || !(selected instanceof ModelOption)) {
                    return;
                }
                ModelOption option = (ModelOption) selected;
                if (field != null) {
                    if (option.descriptor() != null) {
                        field.setText(option.stored());
                    }
                } else {
                    selection.set(option.stored());
                }
            }
        });
        selection.onChange(new Runnable() {
            @Override
            public void run() {
                if (field != null && !syncing && !field.getText().trim().equals(selection.get())) {
                    syncing = true;
                    try {
                        field.setText(selection.get());
                    } finally {
                        syncing = false;
                    }
                }
                rebuild();
            }
        });
        rebuild();
    }

    private void typed() {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            selection.set(field.getText());
        } finally {
            syncing = false;
        }
    }

    ModelCategory category() {
        return category;
    }

    /** Neue Modellliste (EDT); die Auswahl bleibt erhalten, auch wenn ihr Modell fehlt. */
    void show(ModelCatalogSnapshot snapshot) {
        this.snapshot = snapshot == null ? ModelCatalogSnapshot.empty() : snapshot;
        rebuild();
    }

    /** Lokal installierte Modelle, die der Katalog nicht melden muss (Stimmen ohne laufenden Sidecar). */
    void showInstalled(List<ModelOption> options) {
        this.installed = options == null ? Collections.<ModelOption>emptyList()
                : new ArrayList<ModelOption>(options);
        rebuild();
    }

    private void rebuild() {
        filling = true;
        try {
            String current = selection.get();
            ModelReference reference = actions.parseModel(current);
            boolean here = reference == null || actions.isLocal(reference) == local;
            List<ModelOption> options = new ArrayList<ModelOption>();
            for (ModelDescriptor descriptor : snapshot.modelsFor(category)) {
                if (actions.isLocal(descriptor.reference()) == local) {
                    options.add(ModelOption.of(actions.storedModel(descriptor.reference()), descriptor));
                }
            }
            for (ModelOption extra : installed) {
                if (!contains(options, extra.stored())) {
                    options.add(extra);
                }
            }
            DefaultComboBoxModel<ModelOption> model = new DefaultComboBoxModel<ModelOption>();
            ModelOption selected = null;
            if (field != null) {
                if (options.isEmpty() && current.isEmpty()) {
                    model.addElement(ModelOption.none(NOTHING_AVAILABLE));
                    combo.setModel(model);
                    combo.setEnabled(false);
                    return;
                }
                if (options.isEmpty() || !here) {
                    ModelOption none = ModelOption.none(here ? NOTHING_AVAILABLE : LOCAL_ELSEWHERE);
                    model.addElement(none);
                    selected = none;
                }
            } else {
                if (!required || current.isEmpty()) {
                    ModelOption none = ModelOption.none(NO_SELECTION);
                    model.addElement(none);
                    selected = none;
                }
                if (!here) {
                    ModelOption elsewhere = ModelOption.elsewhere(current, local ? CLOUD_ELSEWHERE : LOCAL_ELSEWHERE);
                    model.addElement(elsewhere);
                    selected = elsewhere;
                }
            }
            for (ModelOption option : options) {
                model.addElement(option);
                if (here && reference != null && reference.equals(actions.parseModel(option.stored()))) {
                    selected = option;
                }
            }
            if (here && reference != null && (selected == null || !selected.available())) {
                ModelOption missing = ModelOption.missing(current);
                model.addElement(missing);
                selected = missing;
            }
            model.setSelectedItem(selected);
            combo.setModel(model);
            combo.setEnabled(!options.isEmpty() || (field == null && !required && here && reference != null));
        } finally {
            filling = false;
        }
    }

    private static boolean contains(List<ModelOption> options, String stored) {
        for (ModelOption option : options) {
            if (option.stored().equals(stored)) {
                return true;
            }
        }
        return false;
    }

    JComboBox<ModelOption> combo() {
        return combo;
    }

    /** Das Textfeld (nur Chat und Embeddings im Reiter „Cloud-Modelle“), sonst {@code null}. */
    JTextField field() {
        return field;
    }

    JComponent focusTarget() {
        return field != null ? field : combo;
    }
}
