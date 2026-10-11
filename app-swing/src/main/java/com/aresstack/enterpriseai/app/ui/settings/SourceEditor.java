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
import java.util.Collections;
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

    /** Markiert ein Häkchen, dessen Schlüssel in der Datei fehlte. */
    private static final String UNSET_FLAG = "enterprise-ai.unsetFlag";

    private final ComicPalette palette;
    private final List<KnowledgeSourceType> types;
    private final boolean typeSelectable;
    private final Consumer<String> typeChanged;
    private final Consumer<Consumer<List<String>>> vaultEntries;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final Map<String, JComponent> inputs = new LinkedHashMap<String, JComponent>();
    private JTextField id;
    private JComboBox<String> typeChoice;
    private JComboBox<String> accessChoice;
    private JComboBox<String> entryChoice;
    private JButton advancedToggle;
    private final List<FormRows.Row> advancedRows = new ArrayList<FormRows.Row>();
    private KnowledgeSourceType type;
    private SourceDefinition loaded;
    private boolean manual;
    private boolean expanded;
    private boolean configured;
    private List<String> entries;

    /**
     * @param types          die Quelltypen; beim Bearbeiten nur der eine
     * @param typeSelectable ob oben eine Typauswahl steht (Hinzufügen mit mehreren Typen)
     * @param typeChanged    bekommt die Typ-ID, wenn der Benutzer den Typ wechselt
     */
    SourceEditor(ComicPalette palette, List<KnowledgeSourceType> types, boolean typeSelectable,
                 Consumer<String> typeChanged) {
        this(palette, types, typeSelectable, typeChanged, null);
    }

    /**
     * @param vaultEntries liefert die Titel der KeePass-Einträge (asynchron, auf dem EDT); {@code null} = keine
     *                     Liste, das Eintragsfeld bleibt eine freie Eingabe
     */
    SourceEditor(ComicPalette palette, List<KnowledgeSourceType> types, boolean typeSelectable,
                 Consumer<String> typeChanged, Consumer<Consumer<List<String>>> vaultEntries) {
        this.palette = palette;
        this.types = new ArrayList<KnowledgeSourceType>(types);
        this.typeSelectable = typeSelectable && types.size() > 1;
        this.typeChanged = typeChanged;
        this.vaultEntries = vaultEntries;
        panel.setOpaque(false);
    }

    JPanel panel() {
        return panel;
    }

    /** Baut die Felder für den Typ der Quelle und füllt sie. */
    void load(SourceDefinition source) {
        SourceSettingField secret = secretField(typeOf(source.typeId()));
        String stored = secret == null ? null : source.settings().get(secret.key());
        manual = VaultAccess.isManual(stored);
        configured = stored != null && !stored.trim().isEmpty();
        expanded = false;
        build(source);
        requestEntries();
    }

    /**
     * Baut das Formular: oben Typ, Zugang (KeePass/Manuell) und KeePass-Eintrag, dann die Pflichtangaben des Typs
     * mit Erklärung; alles Übrige unter „Erweitert“. Bei „KeePass“ kommt die Adresse aus dem Eintrag, ihr Feld
     * steht als Überschreibung unter „Erweitert“; bei „Manuell“ ist es eine Pflichtangabe oben.
     */
    private void build(SourceDefinition source) {
        this.loaded = source;
        this.type = typeOf(source.typeId());
        inputs.clear();
        advancedRows.clear();
        accessChoice = null;
        entryChoice = null;
        FormRows rows = new FormRows(palette);
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
        List<SourceSettingField> main = new ArrayList<SourceSettingField>();
        List<SourceSettingField> advanced = new ArrayList<SourceSettingField>();
        SourceSettingField secret = secretField(type);
        if (type != null) {
            for (SourceSettingField field : type.fields()) {
                if (field == secret) {
                    continue;
                }
                boolean fromVault = field.vaultValue() != SourceSettingField.VaultValue.NONE && secret != null;
                if (field.isAdvanced() || (fromVault && !manual)) {
                    advanced.add(field);
                } else {
                    main.add(field);
                }
            }
        }
        if (secret != null) {
            accessChoice = VaultAccess.modeChoice(palette);
            rows.component("Zugang", accessChoice, "KeePass: Benutzer, Passwort und URL stehen im KeePass-Eintrag. "
                    + "Manuell: Adresse hier angeben, Zugangsdaten werden beim ersten Zugriff abgefragt.");
            accessChoice.setSelectedItem(manual ? VaultAccess.MODE_MANUAL : VaultAccess.MODE_KEEPASS);
            accessChoice.addActionListener(event -> {
                boolean nowManual = VaultAccess.MODE_MANUAL.equals(accessChoice.getSelectedItem());
                if (nowManual != manual) {
                    SourceDefinition current = toDefinition();
                    manual = nowManual;
                    build(current);
                }
            });
            if (!manual) {
                entryChoice = VaultAccess.entryChoice(palette);
                rows.component("KeePass-Eintrag", entryChoice, secret.hint().isEmpty() ? null : secret.hint());
                VaultAccess.setEntries(entryChoice, entries == null ? Collections.<String>emptyList() : entries);
                VaultAccess.setText(entryChoice, VaultAccess.title(source.settings().get(secret.key())));
            }
        }
        for (SourceSettingField field : main) {
            boolean fromVault = field.vaultValue() != SourceSettingField.VaultValue.NONE && secret != null;
            inputs.put(field.key(), input(rows, field, source.settings().get(field.key()), field.isRequired() || fromVault,
                    null));
            if (field.kind() != SourceSettingField.Kind.FLAG && !field.hint().isEmpty()) {
                rows.note(field.hint());
            }
        }
        advancedToggle = VaultAccess.advancedToggle(palette);
        rows.component(null, advancedToggle, null);
        advancedToggle.addActionListener(event -> {
            expanded = !expanded;
            showAdvanced();
        });
        id = new JTextField(28);
        advancedRows.add(rows.textField(id, "ID",
                "Kurzname der Quelle (Buchstaben, Ziffern, Punkt, Strich); Teil der Ressourcen-IDs"));
        id.setText(source.id());
        for (SourceSettingField field : advanced) {
            boolean fromVault = field.vaultValue() != SourceSettingField.VaultValue.NONE && secret != null;
            String label = fromVault ? field.label() + " (sonst aus KeePass)" : field.label();
            inputs.put(field.key(), input(rows, field, source.settings().get(field.key()), false, label));
        }
        rows.glue();
        showAdvanced();
        panel.removeAll();
        panel.add(rows.panel(), BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    private void showAdvanced() {
        for (FormRows.Row row : advancedRows) {
            row.setVisible(expanded);
        }
        if (advancedToggle != null) {
            advancedToggle.setText(expanded ? VaultAccess.ADVANCED_OPEN : VaultAccess.ADVANCED_CLOSED);
        }
        panel.revalidate();
        panel.repaint();
    }

    /** Holt die KeePass-Titel einmal; ein eindeutiger passender Eintrag wird vorgewählt, solange nichts gesetzt ist. */
    private void requestEntries() {
        if (secretField(type) == null) {
            return;
        }
        if (entries != null) {
            applyEntries();
            return;
        }
        if (vaultEntries == null) {
            return;
        }
        vaultEntries.accept(titles -> {
            entries = titles == null ? Collections.<String>emptyList() : new ArrayList<String>(titles);
            applyEntries();
        });
    }

    private void applyEntries() {
        if (entryChoice == null || entries == null) {
            return;
        }
        VaultAccess.setEntries(entryChoice, entries);
        if (!configured && VaultAccess.text(entryChoice).isEmpty() && type != null) {
            String match = VaultAccess.unique(entries, type.displayName(), type.idPrefix(), type.scheme());
            if (match != null) {
                VaultAccess.setText(entryChoice, match);
            }
        }
    }

    private static SourceSettingField secretField(KnowledgeSourceType type) {
        if (type == null) {
            return null;
        }
        for (SourceSettingField field : type.fields()) {
            if (field.kind() == SourceSettingField.Kind.SECRET_REF) {
                return field;
            }
        }
        return null;
    }

    private JComponent input(FormRows rows, SourceSettingField field, String value, boolean required,
                             String advancedLabel) {
        boolean advanced = advancedLabel != null;
        String label = (advanced ? advancedLabel : field.label()) + (required ? " *" : "");
        String hint = field.hint().isEmpty() ? null : field.hint();
        switch (field.kind()) {
            case FLAG: {
                JCheckBox box = rows.checkBox(field.label(), hint);
                remember(advanced, box);
                String flag = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
                box.setSelected(flag.equals("true") || flag.equals("yes") || flag.equals("ja") || flag.equals("on")
                        || flag.equals("1"));
                if (value == null || value.trim().isEmpty()) {
                    // nicht gesetzt: unverändert bleibt der Schlüssel weg, damit die Vorgabe des Adapters gilt
                    box.putClientProperty(UNSET_FLAG, Boolean.TRUE);
                }
                return box;
            }
            case DIRECTORY: {
                final JTextField text = new JTextField(28);
                remember(advanced, rows.textField(text, label, hint));
                text.setText(value);
                JButton choose = new JButton("Verzeichnis wählen …");
                choose.setFocusPainted(false);
                choose.addActionListener(event -> chooseDirectory(text));
                remember(advanced, rows.component(null, choose, hint));
                return text;
            }
            default: {
                JTextField text = new JTextField(28);
                remember(advanced, rows.textField(text, label, hint));
                text.setText(value);
                return text;
            }
        }
    }

    private void remember(boolean advanced, FormRows.Row row) {
        if (advanced) {
            advancedRows.add(row);
        }
    }

    private void remember(boolean advanced, JCheckBox box) {
        if (advanced) {
            advancedRows.add(new FormRows.Row(null, box));
        }
    }

    /** Der bearbeitete Stand: geladene Einstellungen, überschrieben mit den Feldern des Typs. */
    SourceDefinition toDefinition() {
        SourceSettings settings = loaded == null ? SourceSettings.empty() : loaded.settings();
        for (Map.Entry<String, JComponent> entry : inputs.entrySet()) {
            JComponent input = entry.getValue();
            if (input instanceof JCheckBox && Boolean.TRUE.equals(input.getClientProperty(UNSET_FLAG))
                    && !((JCheckBox) input).isSelected()) {
                continue;
            }
            String value = input instanceof JCheckBox ? (((JCheckBox) input).isSelected() ? "true" : "false")
                    : ((JTextField) input).getText();
            settings = settings.with(entry.getKey(), value);
        }
        SourceSettingField secret = secretField(type);
        String sourceId = id == null ? "" : id.getText().trim();
        if (secret != null) {
            String title = entryChoice == null ? "" : VaultAccess.text(entryChoice);
            settings = settings.with(secret.key(), VaultAccess.stored(manual, title, sourceId));
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
        if (entryChoice != null) {
            entryChoice.requestFocusInWindow();
        } else if (!inputs.isEmpty()) {
            inputs.values().iterator().next().requestFocusInWindow();
        } else if (id != null) {
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

    JComboBox<String> accessChoice() {
        return accessChoice;
    }

    JComboBox<String> entryChoice() {
        return entryChoice;
    }

    JButton advancedToggle() {
        return advancedToggle;
    }

    /** Das Eingabefeld zum Schlüssel des Adapters oder {@code null}. */
    JComponent input(String key) {
        return inputs.get(key);
    }

    static Color muted() {
        return FormRows.MUTED;
    }
}
