package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Die Felder genau einer Wissensquelle (je nach Typ MediaWiki oder Confluence andere Zeilen), wie sie der
 * {@link SourceDialog} zeigt. Lädt einen {@link SourceForm} und liefert den bearbeiteten Stand zurück; Typ und
 * Häkchen ({@link SourceForm#enabled()}) übernimmt er unverändert.
 */
final class SourceEditor {

    private final JPanel panel;
    private final JTextField id;
    private final JLabel typeLabel = new JLabel();
    private final JTextField url;
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
    private String type = SourceForm.TYPE_MEDIAWIKI;
    private boolean enabled = true;

    SourceEditor(ComicPalette palette) {
        FormRows rows = new FormRows(palette);
        id = rows.textField("ID", "Kurzname der Quelle (Buchstaben, Ziffern, Punkt, Strich); Teil der Ressourcen-IDs");
        rows.component("Typ", typeLabel, null);
        typeLabel.setForeground(palette.getInk());
        url = new JTextField(28);
        rows.textField(url, "URL", null);
        credentialRef = rows.textField("KeePass-Eintrag (optional)",
                "Titel des KeePass-Eintrags mit Benutzername und Passwort; leer = anonym");
        startPoints = rows.textField("Startpunkte",
                "Kommagetrennt: Seitentitel (MediaWiki) bzw. space:KEY oder page:ID (Confluence)");
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
        panel = rows.panel();
    }

    JPanel panel() {
        return panel;
    }

    void load(SourceForm source) {
        type = source.isConfluence() ? SourceForm.TYPE_CONFLUENCE : SourceForm.TYPE_MEDIAWIKI;
        enabled = source.enabled();
        boolean wiki = !source.isConfluence();
        id.setText(source.id());
        typeLabel.setText(wiki ? "MediaWiki (API-URL, z. B. …/w/api.php)" : "Confluence (Basis-URL der Instanz)");
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
        panel.revalidate();
        panel.repaint();
    }

    SourceForm toForm() {
        return SourceForm.builder(id.getText(), type)
                .url(url.getText())
                .credentialRef(credentialRef.getText())
                .startPoints(startPoints.getText())
                .maxDepth(maxDepth.getText())
                .maxResources(maxResources.getText())
                .requiresLogin(requiresLogin.isSelected())
                .siteKey(siteKey.getText())
                .displayName(displayName.getText())
                .searchSpaceKeys(searchSpaceKeys.getText())
                .includeAttachments(includeAttachments.isSelected())
                .enabled(enabled)
                .build();
    }

    void focusFirstField() {
        id.requestFocusInWindow();
    }

    // Für Tests.

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

    JTextField siteKeyField() {
        return siteKey;
    }

    JTextField searchSpaceKeysField() {
        return searchSpaceKeys;
    }
}
