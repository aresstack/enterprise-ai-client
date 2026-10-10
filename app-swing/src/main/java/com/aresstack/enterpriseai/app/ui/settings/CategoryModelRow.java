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
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Auswahl einer {@link ModelCategory}: eine Liste nur der passenden Modelle aller Kataloge (entfernt und lokal
 * gemischt). Chat und Embeddings haben zusätzlich ein Textfeld, damit sie auch ohne Verbindung (Erststart) gesetzt
 * werden können; dort ist das Feld der gespeicherte Wert und die Liste schreibt hinein. Eine gespeicherte Auswahl,
 * die der Katalog gerade nicht kennt, bleibt als „nicht im Katalog“ stehen. Ohne passende Modelle ist die Liste
 * gesperrt: „kein Modell verfügbar“.
 */
final class CategoryModelRow {

    static final String NO_SELECTION = "– keine Auswahl –";
    static final String NOTHING_AVAILABLE = "kein Modell verfügbar";

    private final ModelCategory category;
    private final SettingsDialogActions actions;
    private final JComboBox<ModelOption> combo = new JComboBox<ModelOption>();
    private final JTextField field;
    private String stored = "";
    private boolean filling;
    private ModelCatalogSnapshot snapshot = ModelCatalogSnapshot.empty();

    CategoryModelRow(FormRows rows, final ModelCategory category, boolean withTextField,
                     SettingsDialogActions actions, ComicPalette palette) {
        this.category = category;
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
        combo.getAccessibleContext().setAccessibleName("Modell für " + category.displayName());
        if (withTextField) {
            field = rows.textField(category.displayName(), "Modellkennung, wie sie der Dienst nennt; optional mit "
                    + "Katalog davor (local:…). Die Liste darunter setzt sie.");
            rows.component("", combo, "Passende Modelle aus GET /models und dem lokalen Sidecar");
        } else {
            field = null;
            rows.component(category.displayName(), combo, "Passende Modelle aus GET /models und dem lokalen Sidecar");
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
                    stored = option.stored();
                }
            }
        });
        rebuild();
    }

    ModelCategory category() {
        return category;
    }

    /** Lädt den gespeicherten Wert ({@code [<katalog>:]<modell>} oder leer). */
    void load(String value) {
        String text = value == null ? "" : value.trim();
        if (field != null) {
            field.setText(text);
        } else {
            stored = text;
        }
        rebuild();
    }

    /** Der Wert zum Speichern. */
    String stored() {
        return field != null ? field.getText().trim() : stored;
    }

    /** Neue Modellliste (EDT); die Auswahl bleibt erhalten, auch wenn ihr Modell fehlt. */
    void show(ModelCatalogSnapshot snapshot) {
        this.snapshot = snapshot == null ? ModelCatalogSnapshot.empty() : snapshot;
        rebuild();
    }

    private void rebuild() {
        filling = true;
        try {
            String current = stored();
            ModelReference reference = actions.parseModel(current);
            List<ModelDescriptor> models = snapshot.modelsFor(category);
            DefaultComboBoxModel<ModelOption> model = new DefaultComboBoxModel<ModelOption>();
            ModelOption selected = null;
            if (models.isEmpty() && current.isEmpty()) {
                model.addElement(ModelOption.none(NOTHING_AVAILABLE));
                combo.setModel(model);
                combo.setEnabled(false);
                return;
            }
            if (field == null || models.isEmpty()) {
                ModelOption none = ModelOption.none(field == null ? NO_SELECTION : NOTHING_AVAILABLE);
                model.addElement(none);
                selected = none;
            }
            for (ModelDescriptor descriptor : models) {
                ModelOption option = ModelOption.of(actions.storedModel(descriptor.reference()), descriptor);
                model.addElement(option);
                if (descriptor.reference().equals(reference)) {
                    selected = option;
                }
            }
            if (reference != null && (selected == null || selected.descriptor() == null)) {
                ModelOption missing = ModelOption.missing(current);
                model.addElement(missing);
                selected = missing;
            }
            model.setSelectedItem(selected);
            combo.setModel(model);
            combo.setEnabled(!models.isEmpty() || field == null);
        } finally {
            filling = false;
        }
    }

    JComboBox<ModelOption> combo() {
        return combo;
    }

    /** Das Textfeld (nur Chat und Embeddings), sonst {@code null}. */
    JTextField field() {
        return field;
    }

    JComponent focusTarget() {
        return field != null ? field : combo;
    }
}
