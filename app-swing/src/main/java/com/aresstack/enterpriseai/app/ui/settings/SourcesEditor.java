package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Liste der Wissensquellen mit Formular zur gewählten Quelle: links die IDs, rechts die Felder (je nach
 * Typ MediaWiki oder Confluence andere Zeilen). Hinzufügen legt eine Quelle mit freier ID an, Entfernen nimmt
 * sie aus der Liste; gespeichert wird erst mit dem Dialog.
 */
final class SourcesEditor extends JPanel {

    static final String ADD_WIKI_LABEL = "MediaWiki hinzufügen";
    static final String ADD_CONFLUENCE_LABEL = "Confluence hinzufügen";
    static final String REMOVE_LABEL = "Entfernen";

    /** Veränderlicher Entwurf einer Quelle, bis der Dialog speichert. */
    static final class Draft {
        SourceForm.Builder values;

        Draft(SourceForm source) {
            this.values = source.toBuilder();
        }

        SourceForm build() {
            return values.build();
        }
    }

    private final ComicPalette palette;
    private final DefaultListModel<Draft> listModel = new DefaultListModel<Draft>();
    private final JList<Draft> list = new JList<Draft>(listModel);
    private final ComicButton addWiki;
    private final ComicButton addConfluence;
    private final ComicButton remove;
    private final CardLayout cards = new CardLayout();
    private final JPanel editor = new JPanel(cards);
    private final JTextField id;
    private final JLabel typeLabel = new JLabel();
    private final JTextField url;
    private final FormRows.Row urlRow;
    private final JTextField credentialRef;
    private final JTextField startPoints;
    private final JTextField maxDepth;
    private final JTextField maxResources;
    private final JCheckBox requiresLogin;
    private final FormRows.Row requiresLoginRow;
    private final JTextField siteKey;
    private final FormRows.Row siteKeyRow;
    private final JTextField displayName;
    private final FormRows.Row displayNameRow;
    private final JTextField searchSpaceKeys;
    private final FormRows.Row searchSpaceKeysRow;
    private final JCheckBox includeAttachments;
    private final FormRows.Row includeAttachmentsRow;
    private Draft shown;
    private boolean loading;

    SourcesEditor(ComicPalette palette) {
        super(new BorderLayout(10, 0));
        this.palette = palette;
        setOpaque(false);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(l, value, index, selected, focus);
                if (value instanceof Draft) {
                    SourceForm source = ((Draft) value).build();
                    setText((source.id().isEmpty() ? "(ohne ID)" : source.id()) + "  ·  "
                            + (source.isConfluence() ? "Confluence" : "MediaWiki"));
                }
                setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                return this;
            }
        });
        list.setSelectionBackground(palette.getAccentYellow());
        list.setSelectionForeground(palette.getInk());
        list.setBackground(Color.WHITE);
        list.getAccessibleContext().setAccessibleName("Wissensquellen");
        ComicScrollPane listScroll = new ComicScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, palette);
        listScroll.setBorder(ComicBorder.roundedBorder(palette, 2));
        listScroll.setPreferredSize(new Dimension(190, 150));

        addWiki = new ComicButton(ADD_WIKI_LABEL, null, ComicButton.Accent.ACTION, palette);
        addConfluence = new ComicButton(ADD_CONFLUENCE_LABEL, null, ComicButton.Accent.ACTION, palette);
        remove = new ComicButton(REMOVE_LABEL, null, ComicButton.Accent.CRITICAL, palette);
        // Untereinander, damit die linke Spalte so schmal bleibt wie die Liste und das Formular rechts Platz hat.
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
        buttons.setOpaque(false);
        buttons.add(addWiki);
        buttons.add(addConfluence);
        buttons.add(remove);

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setOpaque(false);
        left.add(listScroll, BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);

        FormRows rows = new FormRows(palette);
        id = rows.textField("ID", "Kurzname der Quelle (Buchstaben, Ziffern, Punkt, Strich); Teil der Ressourcen-IDs");
        rows.component("Typ", typeLabel, null);
        typeLabel.setForeground(palette.getInk());
        url = new JTextField(28);
        urlRow = rows.textField(url, "URL", null);
        credentialRef = rows.textField("KeePass-Eintrag (optional)",
                "Titel des KeePass-Eintrags mit Benutzername und Passwort; leer = anonym");
        startPoints = rows.textField("Startpunkte", "Kommagetrennt: Seitentitel (MediaWiki) bzw. space:KEY oder page:ID (Confluence)");
        maxDepth = rows.textField("Tiefe", "Wie viele Linkebenen ab den Startpunkten verfolgt werden (0 = nur Startpunkte)");
        maxResources = rows.textField("Höchstzahl Seiten (optional)", "Obergrenze je Lauf; leer = Standard");
        requiresLogin = new JCheckBox("Anmeldung erforderlich");
        requiresLogin.setOpaque(false);
        requiresLogin.setForeground(palette.getInk());
        requiresLogin.setFocusPainted(false);
        requiresLoginRow = rows.component(null, requiresLogin, "MediaWiki: vor dem Lesen anmelden");
        siteKey = new JTextField(28);
        siteKeyRow = rows.textField(siteKey, "Site-Schlüssel (optional)",
                "Stabiler Schlüssel in den Ressourcen-IDs; nach dem ersten Indexieren nicht ändern");
        displayName = new JTextField(28);
        displayNameRow = rows.textField(displayName, "Anzeigename (optional)", "Name der Quelle in Quellenangaben");
        searchSpaceKeys = new JTextField(28);
        searchSpaceKeysRow = rows.textField(searchSpaceKeys, "Space-Schlüssel (optional)",
                "Confluence: kommagetrennte Spaces, auf die Suche und Crawl beschränkt werden");
        includeAttachments = new JCheckBox("Anhänge mitlesen");
        includeAttachments.setOpaque(false);
        includeAttachments.setForeground(palette.getInk());
        includeAttachments.setFocusPainted(false);
        includeAttachmentsRow = rows.component(null, includeAttachments, "Confluence: Anhänge als Dokumente indexieren");
        rows.glue();

        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        JLabel hint = new JLabel("<html><body style='width: 300px'>Keine Quelle gewählt. „MediaWiki hinzufügen“ "
                + "oder „Confluence hinzufügen“ legt eine neue Wissensquelle an; ohne Quellen bleibt die "
                + "Wissensbasis leer und RAG liefert keine Treffer.</body></html>");
        hint.setForeground(FormRows.MUTED);
        empty.add(hint, BorderLayout.NORTH);
        editor.setOpaque(false);
        editor.add(empty, "empty");
        editor.add(rows.panel(), "form");

        add(left, BorderLayout.WEST);
        add(editor, BorderLayout.CENTER);

        wire();
        showSelection();
    }

    private void wire() {
        addWiki.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                add(SourceForm.TYPE_MEDIAWIKI);
            }
        });
        addConfluence.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                add(SourceForm.TYPE_CONFLUENCE);
            }
        });
        remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelected();
            }
        });
        list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    showSelection();
                }
            }
        });
        id.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                idChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                idChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                idChanged();
            }
        });
    }

    private void idChanged() {
        if (loading || shown == null) {
            return;
        }
        commit();
        int index = listModel.indexOf(shown);
        if (index >= 0) {
            listModel.set(index, shown); // Renderer aktualisieren
        }
    }

    /** Übernimmt die Felder in den gezeigten Entwurf. */
    private void commit() {
        if (shown == null) {
            return;
        }
        SourceForm current = shown.values.build();
        shown.values = SourceForm.builder(id.getText(), current.type())
                .url(url.getText())
                .credentialRef(credentialRef.getText())
                .startPoints(startPoints.getText())
                .maxDepth(maxDepth.getText())
                .maxResources(maxResources.getText())
                .requiresLogin(requiresLogin.isSelected())
                .siteKey(siteKey.getText())
                .displayName(displayName.getText())
                .searchSpaceKeys(searchSpaceKeys.getText())
                .includeAttachments(includeAttachments.isSelected());
    }

    private void showSelection() {
        commit();
        Draft selected = list.getSelectedValue();
        shown = selected;
        remove.setEnabled(selected != null);
        if (selected == null) {
            cards.show(editor, "empty");
            return;
        }
        loading = true;
        try {
            SourceForm source = selected.build();
            boolean wiki = !source.isConfluence();
            id.setText(source.id());
            typeLabel.setText(wiki ? "MediaWiki (API-URL, z. B. …/w/api.php)" : "Confluence (Basis-URL der Instanz)");
            urlRow.setVisible(true);
            url.setText(source.url());
            url.setToolTipText(wiki ? "Die api.php der MediaWiki-Installation" : "Basis-URL von Confluence, ohne /rest");
            credentialRef.setText(source.credentialRef());
            startPoints.setText(source.startPoints());
            maxDepth.setText(source.maxDepth());
            maxResources.setText(source.maxResources());
            requiresLogin.setSelected(source.requiresLogin());
            siteKey.setText(source.siteKey());
            displayName.setText(source.displayName());
            searchSpaceKeys.setText(source.searchSpaceKeys());
            includeAttachments.setSelected(source.includeAttachments());
            requiresLoginRow.setVisible(wiki);
            siteKeyRow.setVisible(wiki);
            displayNameRow.setVisible(wiki);
            searchSpaceKeysRow.setVisible(!wiki);
            includeAttachmentsRow.setVisible(!wiki);
            cards.show(editor, "form");
        } finally {
            loading = false;
        }
        editor.revalidate();
        editor.repaint();
    }

    void add(String type) {
        commit();
        Draft draft = new Draft(SourceForm.builder(freeId(type), type).build());
        listModel.addElement(draft);
        list.setSelectedValue(draft, true);
        showSelection();
        id.requestFocusInWindow();
    }

    private String freeId(String type) {
        Set<String> taken = new HashSet<String>();
        for (int i = 0; i < listModel.size(); i++) {
            taken.add(listModel.get(i).build().id());
        }
        String base = SourceForm.TYPE_CONFLUENCE.equals(type) ? "confluence" : "wiki";
        String candidate = base;
        int n = 2;
        while (taken.contains(candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }

    void removeSelected() {
        int index = list.getSelectedIndex();
        if (index < 0) {
            return;
        }
        shown = null; // nicht mehr in den entfernten Entwurf zurückschreiben
        listModel.remove(index);
        if (listModel.size() > 0) {
            list.setSelectedIndex(Math.min(index, listModel.size() - 1));
        } else {
            list.clearSelection();
        }
        showSelection();
    }

    void setSources(List<SourceForm> sources) {
        shown = null;
        listModel.clear();
        for (SourceForm source : sources) {
            listModel.addElement(new Draft(source));
        }
        if (listModel.size() > 0) {
            list.setSelectedIndex(0);
        } else {
            list.clearSelection();
        }
        showSelection();
    }

    List<SourceForm> sources() {
        commit();
        List<SourceForm> sources = new ArrayList<SourceForm>();
        for (int i = 0; i < listModel.size(); i++) {
            sources.add(listModel.get(i).build());
        }
        return sources;
    }

    // Für Tests.

    JList<Draft> sourceList() {
        return list;
    }

    ComicButton addWikiButton() {
        return addWiki;
    }

    ComicButton addConfluenceButton() {
        return addConfluence;
    }

    ComicButton removeButton() {
        return remove;
    }

    JTextField idField() {
        return id;
    }

    JTextField urlField() {
        return url;
    }

    JTextField startPointsField() {
        return startPoints;
    }

    JTextField credentialRefField() {
        return credentialRef;
    }

    JComponent editorCards() {
        return editor;
    }
}
