package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Auswahlliste unter einem Modellfeld: bleibt verborgen, bis der Verbindungstest Modelle geliefert hat. Eine Wahl
 * schreibt die Kennung ins Textfeld; gespeichert wird weiterhin nur das Textfeld.
 */
final class ModelPicker {

    private final JComboBox<ModelChoice> combo = new JComboBox<ModelChoice>();
    private final FormRows.Row row;
    private final JTextField target;
    private boolean filling;

    ModelPicker(FormRows rows, final JTextField target, String tooltip, ComicPalette palette) {
        this.target = target;
        combo.setForeground(palette.getInk());
        combo.setBackground(Color.WHITE);
        row = rows.component("Verfügbar", combo, tooltip);
        row.setVisible(false);
        combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object selected = combo.getSelectedItem();
                if (!filling && selected instanceof ModelChoice) {
                    target.setText(((ModelChoice) selected).id());
                }
            }
        });
    }

    /** Füllt die Liste (EDT); wählt das Modell aus dem Textfeld vor, ohne das Feld zu ändern. */
    void show(List<ModelChoice> models) {
        filling = true;
        try {
            DefaultComboBoxModel<ModelChoice> model = new DefaultComboBoxModel<ModelChoice>();
            ModelChoice current = null;
            String configured = target.getText() == null ? "" : target.getText().trim();
            if (models != null) {
                for (ModelChoice choice : models) {
                    model.addElement(choice);
                    if (choice.id().equals(configured)) {
                        current = choice;
                    }
                }
            }
            model.setSelectedItem(current);
            combo.setModel(model);
            row.setVisible(model.getSize() > 0);
        } finally {
            filling = false;
        }
        if (target.getParent() != null) {
            target.getParent().revalidate();
            target.getParent().repaint();
        }
    }

    JComboBox<ModelChoice> combo() {
        return combo;
    }
}
