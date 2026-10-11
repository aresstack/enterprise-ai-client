package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Zugang zu einem Dienst wie in MainframeMate: „KeePass“ (Standard) mit Auswahl des Eintrags, der Benutzer,
 * Passwort und URL trägt, oder „Manuell“ (Zugangsdaten werden beim ersten Gebrauch abgefragt und nicht
 * gespeichert). Der Benutzer tippt nie ein Präfix; gespeichert wird der Titel des Eintrags bzw. für „Manuell“ eine
 * interne Referenz {@code ask:<Name>}.
 */
final class VaultAccess {

    static final String MODE_KEEPASS = "KeePass";
    static final String MODE_MANUAL = "Manuell";
    static final String NEW_ENTRY = "➕ Neu";
    static final String ADVANCED_CLOSED = "▸ Erweitert";
    static final String ADVANCED_OPEN = "▾ Erweitert";
    private static final String ASK = "ask:";
    private static final String KEEPASS = "keepass:";

    private VaultAccess() {
    }

    /** {@code true}, wenn der gespeicherte Wert „Manuell“ bedeutet. */
    static boolean isManual(String stored) {
        return stored != null && stored.trim().regionMatches(true, 0, ASK, 0, ASK.length());
    }

    /** Der Titel des KeePass-Eintrags aus dem gespeicherten Wert, ohne Präfix; leer bei „Manuell“. */
    static String title(String stored) {
        String value = stored == null ? "" : stored.trim();
        if (isManual(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).startsWith(KEEPASS) ? value.substring(KEEPASS.length()).trim() : value;
    }

    /** Was gespeichert wird: der Titel (KeePass) oder {@code ask:<name>} (Manuell). */
    static String stored(boolean manual, String title, String name) {
        if (manual) {
            String label = name == null || name.trim().isEmpty() ? "zugang" : name.trim();
            return ASK + label;
        }
        return title(title);
    }

    /** Auswahl „KeePass“/„Manuell“. */
    static JComboBox<String> modeChoice(ComicPalette palette) {
        JComboBox<String> combo = new JComboBox<String>(new String[] {MODE_KEEPASS, MODE_MANUAL});
        combo.setForeground(palette.getInk());
        return combo;
    }

    /** Eintragsauswahl: aufklappbare Liste der KeePass-Titel, auch frei beschreibbar (KeePass gerade zu). */
    static JComboBox<String> entryChoice(ComicPalette palette) {
        JComboBox<String> combo = new JComboBox<String>();
        combo.setEditable(true);
        combo.setForeground(palette.getInk());
        return combo;
    }

    static String text(JComboBox<String> entry) {
        Object item = entry.isEditable() ? entry.getEditor().getItem() : entry.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    static void setText(JComboBox<String> entry, String text) {
        entry.getEditor().setItem(text == null ? "" : text);
        entry.setSelectedItem(text == null ? "" : text);
    }

    /** Füllt die Liste, ohne die Eingabe zu verlieren. */
    static void setEntries(JComboBox<String> entry, List<String> titles) {
        String current = text(entry);
        List<String> items = new ArrayList<String>(titles == null ? new ArrayList<String>() : titles);
        if (!current.isEmpty() && !items.contains(current)) {
            items.add(0, current);
        }
        entry.setModel(new DefaultComboBoxModel<String>(items.toArray(new String[0])));
        setText(entry, current);
    }

    /**
     * Ein eindeutiger Eintrag, dessen Titel einen der Namen enthält (z. B. „Confluence“), oder {@code null}, wenn
     * keiner oder mehrere passen.
     */
    static String unique(List<String> titles, String... names) {
        String found = null;
        for (String title : titles) {
            String lower = title.toLowerCase(Locale.ROOT);
            boolean matches = false;
            for (String name : names) {
                matches |= name != null && name.trim().length() > 1
                        && lower.contains(name.trim().toLowerCase(Locale.ROOT));
            }
            if (matches) {
                if (found != null) {
                    return null;
                }
                found = title;
            }
        }
        return found;
    }

    /** Knopf „▸ Erweitert“, der Felder auf- und zuklappt. */
    static JButton advancedToggle(ComicPalette palette) {
        JButton toggle = new JButton(ADVANCED_CLOSED);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setFocusPainted(false);
        toggle.setOpaque(false);
        toggle.setForeground(palette.getNavigationBlue());
        toggle.setHorizontalAlignment(JButton.LEFT);
        toggle.setToolTipText("Weitere Angaben; leer lassen genügt in der Regel");
        return toggle;
    }

    /**
     * Eintragsauswahl mit „➕ Neu“ dahinter: legt über die Passwortverwaltung einen KeePass-Eintrag an (Kategorie
     * vorbelegt) und wählt ihn danach aus.
     */
    static JPanel withNewEntry(final JComboBox<String> entry, final Supplier<PasswordStore> store,
                               final Supplier<String> category, final ComicPalette palette) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(entry, BorderLayout.CENTER);
        ComposerButton add = new ComposerButton(null, NEW_ENTRY, false, "Neuen KeePass-Eintrag anlegen");
        add.addActionListener(event -> {
            String saved = PasswordEntryDialog.show(row, store.get(), null,
                    PasswordEntryDialog.categoryFor(category.get()), palette);
            if (saved != null) {
                List<String> titles = new ArrayList<String>();
                for (int i = 0; i < entry.getItemCount(); i++) {
                    titles.add(entry.getItemAt(i));
                }
                if (!titles.contains(saved)) {
                    titles.add(saved);
                }
                setText(entry, saved);
                setEntries(entry, titles);
            }
        });
        row.add(add, BorderLayout.EAST);
        return row;
    }

    /** Für Tests: das Eingabefeld der Eintragsauswahl. */
    static JTextComponent editorField(JComboBox<String> entry) {
        return entry.getEditor().getEditorComponent() instanceof JTextComponent
                ? (JTextComponent) entry.getEditor().getEditorComponent() : new JTextField();
    }
}
