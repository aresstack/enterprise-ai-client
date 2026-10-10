package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.application.localruntime.JavaRuntimeOverview;
import com.aresstack.enterpriseai.domain.localruntime.JavaRuntimeInstallation;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

/**
 * „Java-Runtime für Sidecar“: Dropdown der gefundenen Java-Installationen (Java 21+ wählbar, ältere ausgegraut mit
 * „nicht kompatibel“), darunter die gewählte Version mit Pfad und „Neu suchen“. Gesucht wird beim ersten Anzeigen
 * und auf Knopfdruck im Hintergrund; ohne gespeicherte Wahl wird die automatische Wahl übernommen (exakt Java 21,
 * sonst die kleinste höhere Version).
 */
final class JavaRuntimeRow {

    static final String SEARCH_LABEL = "Neu suchen";
    static final String SEARCHING_LABEL = "Suche Java-Installationen …";
    static final String NONE_FOUND = "Kein Java 21 oder neuer gefunden; lokale Modelle bleiben aus.";

    private final SettingsDialogActions actions;
    private final ComicPalette palette;
    private final JComboBox<Choice> combo = new JComboBox<Choice>();
    private final JLabel status = new JLabel(" ");
    private final ComicButton search;
    private String stored = "";
    private Choice previous;
    private boolean filling;
    private boolean searchedOnce;

    JavaRuntimeRow(FormRows rows, SettingsDialogActions actions, ComicPalette palette) {
        this.actions = actions;
        this.palette = palette;
        combo.setForeground(palette.getInk());
        combo.setBackground(Color.WHITE);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                Choice choice = (Choice) value;
                boolean usable = choice == null || choice.usable();
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                        selected && usable, focused && usable);
                label.putClientProperty("html.disable", Boolean.TRUE);
                if (!usable) {
                    label.setForeground(FormRows.MUTED);
                }
                return label;
            }
        });
        combo.getAccessibleContext().setAccessibleName("Java-Runtime für den Sidecar");
        combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (filling) {
                    return;
                }
                Choice selected = (Choice) combo.getSelectedItem();
                if (selected != null && !selected.usable()) {
                    // Zu alte Versionen stehen nur zur Erklärung in der Liste.
                    filling = true;
                    combo.setSelectedItem(previous);
                    filling = false;
                    return;
                }
                previous = selected;
                stored = selected == null ? "" : selected.path();
                showStatus();
            }
        });
        search = new ComicButton(SEARCH_LABEL, null, ComicButton.Accent.ACTION, palette);
        search.setToolTipText("Sucht Java in JAVA_HOME, PATH und den üblichen Installationsverzeichnissen");
        search.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });
        status.setFont(status.getFont().deriveFont(Font.PLAIN, Math.max(11f, status.getFont().getSize2D() - 1f)));
        status.putClientProperty("html.disable", Boolean.TRUE);
        JPanel below = new JPanel(new BorderLayout(8, 0));
        below.setOpaque(false);
        below.add(status, BorderLayout.CENTER);
        below.add(search, BorderLayout.EAST);
        rows.component("Java-Runtime für Sidecar", combo, "Java 21 oder neuer; wird automatisch erkannt");
        rows.component("", below, null);
    }

    void load(String javaPath) {
        stored = javaPath == null ? "" : javaPath.trim();
        filling = true;
        combo.removeAllItems();
        if (!stored.isEmpty()) {
            combo.addItem(new Choice(stored, null));
        }
        previous = (Choice) combo.getSelectedItem();
        filling = false;
        showStatus();
    }

    String stored() {
        return stored;
    }

    /** Beim ersten Anzeigen des Reiters einmal suchen. */
    void shown() {
        if (!searchedOnce) {
            search();
        }
    }

    void search() {
        searchedOnce = true;
        search.setEnabled(false);
        search.setText(SEARCHING_LABEL);
        try {
            actions.discoverJavaRuntimes(new Consumer<JavaRuntimeOverview>() {
                @Override
                public void accept(JavaRuntimeOverview overview) {
                    search.setEnabled(true);
                    search.setText(SEARCH_LABEL);
                    apply(overview);
                }
            });
        } catch (RuntimeException e) {
            search.setEnabled(true);
            search.setText(SEARCH_LABEL);
        }
    }

    private void apply(JavaRuntimeOverview overview) {
        filling = true;
        combo.removeAllItems();
        Choice match = null;
        Choice recommended = null;
        for (JavaRuntimeInstallation installation : overview.installations()) {
            Choice choice = new Choice(installation.executable().toString(), installation);
            combo.addItem(choice);
            if (!stored.isEmpty() && samePath(stored, choice.path())) {
                match = choice;
            }
            if (installation.equals(overview.recommended())) {
                recommended = choice;
            }
        }
        Choice selected;
        if (match != null && match.usable()) {
            selected = match;
        } else if (!stored.isEmpty() && match == null) {
            // Eigener Eintrag, den die Suche nicht kennt: stehen lassen.
            selected = new Choice(stored, null);
            combo.insertItemAt(selected, 0);
        } else {
            // Keine oder ungültige Wahl: automatisch die empfohlene übernehmen.
            selected = recommended;
        }
        combo.setSelectedItem(selected);
        previous = selected;
        stored = selected == null ? "" : selected.path();
        filling = false;
        showStatus();
    }

    private void showStatus() {
        Choice selected = (Choice) combo.getSelectedItem();
        if (selected == null) {
            status.setText(searchedOnce && search.isEnabled() ? NONE_FOUND : " ");
            status.setForeground(palette.getAccentOrange());
        } else if (selected.installation == null) {
            status.setText(selected.path());
            status.setForeground(FormRows.MUTED);
        } else {
            status.setText("✓ " + selected.installation.displayName() + "  " + selected.path());
            status.setForeground(palette.getAgentPetrol());
        }
        status.setToolTipText(status.getText().trim().isEmpty() ? null : status.getText());
    }

    private static boolean samePath(String a, String b) {
        return a.replace('/', '\\').equalsIgnoreCase(b.replace('/', '\\'));
    }

    JComboBox<Choice> combo() {
        return combo;
    }

    /** Ein Eintrag: gefundene Installation oder ein eingetragener Pfad ohne Prüfung. */
    static final class Choice {

        private final String path;
        private final JavaRuntimeInstallation installation;

        Choice(String path, JavaRuntimeInstallation installation) {
            this.path = path;
            this.installation = installation;
        }

        String path() {
            return path;
        }

        boolean usable() {
            return installation == null || installation.isCompatible();
        }

        @Override
        public String toString() {
            if (installation == null) {
                return path;
            }
            return installation.displayName() + "   " + path
                    + (installation.isCompatible() ? "" : "   nicht kompatibel");
        }
    }
}
