package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Die Felder genau einer Wissensquelle, gebaut aus den {@link SourceSettingField Feldern} ihres Quelltyps: der
 * Editor kennt keinen Typ, er zeigt, was der Adapter beschreibt (Text, Zahl, Schalter, Verzeichnis mit Auswahl,
 * KeePass-Eintrag). Beim Hinzufügen steht oben die Auswahl des Quelltyps; ein Wechsel baut die Felder neu.
 * Schlüssel, die der Typ nicht als Feld zeigt (Timeouts, Zertifikat ...), bleiben beim Speichern unverändert.
 */
final class SourceEditor {

    private final ComicPalette palette;
    private final List<KnowledgeSourceType> types;
    private final boolean typeSelectable;
    private final Consumer<String> typeChanged;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final Map<String, JComponent> inputs = new LinkedHashMap<String, JComponent>();
    private JTextField id;
    private JComboBox<String> typeChoice;
    private KnowledgeSourceType type;
    private SourceDefinition loaded;

    /**
     * @param types          die Quelltypen; beim Bearbeiten nur der eine
     * @param typeSelectable ob oben eine Typauswahl steht (Hinzufügen mit mehreren Typen)
     * @param typeChanged    bekommt die Typ-ID, wenn der Benutzer den Typ wechselt
     */
    SourceEditor(ComicPalette palette, List<KnowledgeSourceType> types, boolean typeSelectable,
                 Consumer<String> typeChanged) {
        this.palette = palette;
        this.types = new ArrayList<KnowledgeSourceType>(types);
        this.typeSelectable = typeSelectable && types.size() > 1;
        this.typeChanged = typeChanged;
        panel.setOpaque(false);
    }

    JPanel panel() {
        return panel;
    }

    /** Baut die Felder für den Typ der Quelle und füllt sie. */
    void load(SourceDefinition source) {
        this.loaded = source;
        this.type = typeOf(source.typeId());
        inputs.clear();
        FormRows rows = new FormRows(palette);
        id = rows.textField("ID", "Kurzname der Quelle (Buchstaben, Ziffern, Punkt, Strich); Teil der Ressourcen-IDs");
        id.setText(source.id());
        if (typeSelectable) {
            String[] names = new String[types.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = types.get(i).displayName();
            }
            typeChoice = rows.comboBox("Typ", "Welche Art von Quelle", names);
            typeChoice.setSelectedIndex(Math.max(0, types.indexOf(type)));
            typeChoice.addActionListener(event -> {
                int index = typeChoice.getSelectedIndex();
                if (index >= 0 && !types.get(index).equals(type) && typeChanged != null) {
                    typeChanged.accept(types.get(index).id());
                }
            });
        } else {
            JLabel typeLabel = new JLabel(type == null ? "Unbekannter Typ „" + source.typeId() + "“"
                    : type.displayName());
            typeLabel.setForeground(palette.getInk());
            rows.component("Typ", typeLabel, null);
        }
        if (type != null && !type.description().isEmpty()) {
            rows.note(type.description());
        }
        if (type != null) {
            for (SourceSettingField field : type.fields()) {
                inputs.put(field.key(), input(rows, field, source.settings().get(field.key())));
            }
        }
        rows.glue();
        panel.removeAll();
        panel.add(rows.panel(), BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    private JComponent input(FormRows rows, SourceSettingField field, String value) {
        String label = field.label() + (field.isRequired() ? " *" : "");
        String hint = field.hint().isEmpty() ? null : field.hint();
        switch (field.kind()) {
            case FLAG: {
                JCheckBox box = rows.checkBox(field.label(), hint);
                box.setSelected("true".equalsIgnoreCase(value));
                return box;
            }
            case DIRECTORY: {
                final JTextField text = new JTextField(28);
                rows.textField(text, label, hint);
                text.setText(value);
                JButton choose = new JButton("Verzeichnis wählen …");
                choose.setFocusPainted(false);
                choose.addActionListener(event -> chooseDirectory(text));
                rows.component(null, choose, hint);
                return text;
            }
            default: {
                JTextField text = rows.textField(label, hint);
                text.setText(value);
                return text;
            }
        }
    }

    /** Der bearbeitete Stand: geladene Einstellungen, überschrieben mit den Feldern des Typs. */
    SourceDefinition toDefinition() {
        SourceSettings settings = loaded == null ? SourceSettings.empty() : loaded.settings();
        for (Map.Entry<String, JComponent> entry : inputs.entrySet()) {
            JComponent input = entry.getValue();
            String value = input instanceof JCheckBox ? (((JCheckBox) input).isSelected() ? "true" : "false")
                    : ((JTextField) input).getText();
            settings = settings.with(entry.getKey(), value);
        }
        String typeId = type == null ? (loaded == null ? "" : loaded.typeId()) : type.id();
        boolean enabled = loaded == null || loaded.enabled();
        return new SourceDefinition(id == null ? "" : id.getText(), typeId, enabled, settings);
    }

    String currentId() {
        return id == null ? "" : id.getText().trim();
    }

    KnowledgeSourceType type() {
        return type;
    }

    private KnowledgeSourceType typeOf(String typeId) {
        for (KnowledgeSourceType candidate : types) {
            if (candidate.id().equals(typeId)) {
                return candidate;
            }
        }
        return null;
    }

    private void chooseDirectory(JTextField target) {
        String current = target.getText().trim();
        JFileChooser chooser = new JFileChooser(current.isEmpty() ? null : current);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Verzeichnis mit Dokumenten wählen");
        if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    void focusFirstField() {
        if (id != null) {
            id.requestFocusInWindow();
        }
    }

    // Für Tests.

    JTextField idField() {
        return id;
    }

    JComboBox<String> typeChoice() {
        return typeChoice;
    }

    /** Das Eingabefeld zum Schlüssel des Adapters oder {@code null}. */
    JComponent input(String key) {
        return inputs.get(key);
    }

    static Color muted() {
        return FormRows.MUTED;
    }
}
