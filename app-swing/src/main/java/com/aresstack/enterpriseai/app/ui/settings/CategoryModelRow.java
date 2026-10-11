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
import javax.swing.plaf.basic.BasicComboBoxEditor;
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
 * ein Modell wählt, wählt das andere ab. Chat und Embeddings sind in „Cloud-Modelle“ editierbare Listen, damit sie
 * auch ohne Verbindung (Erststart) gesetzt werden können: das Eingabefeld zeigt die gespeicherte Modellkennung, die
 * aufgeklappte Liste die Modelle des Katalogs. Eine gespeicherte Auswahl, die der Katalog gerade nicht kennt, bleibt
 * als „nicht im Katalog“ stehen. Ohne passende Modelle ist eine nicht editierbare Liste gesperrt.
 */
final class CategoryModelRow {

    static final String NO_SELECTION = "– keine Auswahl –";
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
     * @param editable nur Cloud: Modellkennung auch frei eintippbar (Chat, Embeddings)
     */
    CategoryModelRow(FormRows rows, final ModelCategory category, boolean local, boolean editable,
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
        String source = local ? "Installierte lokale Modelle"
                : "Passende Modelle aus GET /models der Enterprise-API";
        if (editable && !local) {
            combo.setEditable(true);
            combo.setEditor(new StoredValueEditor());
            field = (JTextField) combo.getEditor().getEditorComponent();
            field.setForeground(palette.getInk());
            field.getAccessibleContext().setAccessibleName(combo.getAccessibleContext().getAccessibleName());
            rows.component(category.displayName(), combo, "Modellkennung, wie sie der Dienst nennt, eintippen "
                    + "(optional mit Katalog davor, local:…) oder aus der Liste wählen: " + source);
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
                if (field == null || option.descriptor() != null) {
                    selection.set(option.stored());
                }
            }
        });
        selection.onChange(new Runnable() {
            @Override
            public void run() {
                rebuild();
            }
        });
        rebuild();
    }

    private void typed() {
        if (syncing || filling) {
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

    /** Lokal installierte Modelle, die der Katalog nicht melden muss (installierte Stimmen). */
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
                if (!here) {
                    ModelOption elsewhere = ModelOption.elsewhere(current, LOCAL_ELSEWHERE);
                    model.addElement(elsewhere);
                    selected = elsewhere;
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
                // Editierbar: nur der wörtlich gleiche Eintrag, damit das Eingabefeld beim Tippen stehen bleibt.
                if (here && reference != null && (field != null ? option.stored().equals(current)
                        : reference.equals(actions.parseModel(option.stored())))) {
                    selected = option;
                }
            }
            if (here && (reference != null || (field != null && !current.isEmpty()))
                    && (selected == null || !selected.available())) {
                ModelOption missing = ModelOption.missing(current);
                model.addElement(missing);
                selected = missing;
            }
            model.setSelectedItem(selected);
            combo.setModel(model);
            combo.setEnabled(field != null || !options.isEmpty() || (!required && here && reference != null));
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

    /** Das Eingabefeld der editierbaren Liste (nur Chat und Embeddings unter „Cloud-Modelle“), sonst {@code null}. */
    JTextField field() {
        return field;
    }

    JComponent focusTarget() {
        return field != null ? field : combo;
    }

    /**
     * Eingabefeld der editierbaren Liste: zeigt von einem Listeneintrag die gespeicherte Modellkennung, nicht seine
     * Beschriftung, und lässt den Text stehen, solange er schon passt (kein Sprung des Cursors beim Tippen).
     */
    private static final class StoredValueEditor extends BasicComboBoxEditor {

        @Override
        public void setItem(Object item) {
            String text = item instanceof ModelOption ? ((ModelOption) item).stored()
                    : item == null ? "" : item.toString();
            if (!text.trim().equals(editor.getText().trim())) {
                editor.setText(text);
            }
        }

        @Override
        public Object getItem() {
            return editor.getText();
        }
    }
}
