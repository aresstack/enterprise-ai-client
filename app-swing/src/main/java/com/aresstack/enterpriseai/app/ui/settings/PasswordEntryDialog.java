package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowShape;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * „Neuen Eintrag anlegen“ / „Eintrag bearbeiten“ wie MainframeMate {@code ShowPasswordsMenuCommand.showEntryEditor}:
 * Kategorie, Netzwerk, ID (KeePass-Titel, beim Bearbeiten fest), Anzeigename, Benutzer, Passwort mit „anzeigen“,
 * URL (Pflicht bei Wiki, SP und Confluence), bei Confluence „Zertifikat verwenden“ mit Alias, dann die Optionen.
 * OK schreibt über den {@link PasswordStore}; scheitert das, steht die Meldung im Dialog und er bleibt offen.
 */
final class PasswordEntryDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final String EXTERN_LABEL = "🌐 Extern";
    static final String INTERN_LABEL = "🏢 Intern";

    private final PasswordStore store;
    private final PasswordEntry existing;
    private final ComicPalette palette;
    private final JComboBox<String> category = new JComboBox<String>(PasswordEntry.categories().toArray(new String[0]));
    private final JComboBox<String> network = new JComboBox<String>(new String[] {EXTERN_LABEL, INTERN_LABEL});
    private final JTextField id = new JTextField(28);
    private final JTextField displayName = new JTextField(28);
    private final JTextField userName = new JTextField(28);
    private final JPasswordField password = new JPasswordField(28);
    private final JCheckBox showPassword = new JCheckBox("anzeigen");
    private final JTextField url = new JTextField(28);
    private final FormRows.Row urlRow;
    private final JCheckBox useCertificate = new JCheckBox("Zertifikat verwenden");
    private final JTextField certAlias = new JTextField(28);
    private final FormRows.Row certificateRow;
    private final FormRows.Row certAliasRow;
    private final JCheckBox requiresLogin = new JCheckBox("Login erforderlich");
    private final JCheckBox useProxy = new JCheckBox("Proxy verwenden");
    private final JCheckBox autoIndex = new JCheckBox("Auto-Index");
    private final JCheckBox savePassword = new JCheckBox("PW speichern");
    private final JCheckBox sessionCache = new JCheckBox("Session-Cache");
    private final JLabel error = new JLabel(" ");
    private final ComposerButton ok;
    private final ComposerButton testButton;
    private String savedTitle;

    private PasswordEntryDialog(Window owner, PasswordStore store, PasswordEntry existing, String presetCategory,
                                ComicPalette palette) {
        super(owner, existing == null ? "Neuen Eintrag anlegen" : "Eintrag bearbeiten", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.existing = existing;
        this.palette = palette;
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        FormRows rows = new FormRows(palette);
        category.setEditable(true);
        rows.component("Kategorie:", category, null);
        rows.component("Netzwerk:", network, null);
        rows.textField(id, "ID:", "Titel des Eintrags in KeePass; eindeutig");
        rows.textField(displayName, "Anzeigename:", null);
        rows.textField(userName, "Benutzername:", null);
        JPanel passwordRow = new JPanel(new BorderLayout(6, 0));
        passwordRow.setOpaque(false);
        passwordRow.add(password, BorderLayout.CENTER);
        showPassword.setOpaque(false);
        showPassword.setForeground(palette.getInk());
        passwordRow.add(showPassword, BorderLayout.EAST);
        rows.component("Passwort:", passwordRow, null);
        final char echo = password.getEchoChar();
        showPassword.addActionListener(event -> password.setEchoChar(showPassword.isSelected() ? (char) 0 : echo));
        urlRow = rows.textField(url, "URL:", null);
        useCertificate.setOpaque(false);
        useCertificate.setForeground(palette.getInk());
        certificateRow = rows.component(null, useCertificate, null);
        certAlias.setEditable(false);
        ComposerButton chooseCertificate = new ComposerButton(null, "🔒 Zertifikat…", false,
                "Zertifikat aus dem Windows-Zertifikatspeicher wählen");
        chooseCertificate.addActionListener(event -> {
            String chosen = CertificateChooser.showChooserDialog(this, certAlias.getText().trim());
            if (chosen != null) {
                certAlias.setText(chosen);
            }
        });
        JPanel certLine = new JPanel(new BorderLayout(6, 0));
        certLine.setOpaque(false);
        certLine.add(certAlias, BorderLayout.CENTER);
        certLine.add(chooseCertificate, BorderLayout.EAST);
        certAliasRow = rows.component("Zertifikat:", certLine, "Alias im Windows-Zertifikatspeicher");
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.setOpaque(false);
        for (JCheckBox box : Arrays.asList(requiresLogin, useProxy, autoIndex, savePassword, sessionCache)) {
            box.setOpaque(false);
            box.setForeground(palette.getInk());
            options.add(box);
        }
        rows.component("Optionen:", options, null);
        rows.glue();

        error.setForeground(ResearchUiPalette.DANGER_RED);
        JLabel heading = new JLabel(getTitle());
        heading.setFont(ResearchUiTypography.semiBold(15f));
        heading.setForeground(palette.getInk());
        ok = ComposerButton.primary(null, "OK", palette.getNavigationBlue(), null);
        ComposerButton cancel = new ComposerButton(null, "Abbrechen", false);
        ok.addActionListener(event -> save());
        cancel.addActionListener(event -> dispose());
        testButton = new ComposerButton(null, "🔗 Verbindung testen", false,
                "Verbindung mit URL, Benutzer, Passwort und Zertifikat prüfen, ohne zu speichern");
        testButton.addActionListener(event -> testConnection());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(testButton);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        buttons.add(cancel);
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.setOpaque(false);
        buttonBar.add(left, BorderLayout.WEST);
        buttonBar.add(buttons, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(error, BorderLayout.NORTH);
        south.add(buttonBar, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(palette.getSurface());
        content.setBorder(BorderFactory.createCompoundBorder(
                ComicBorder.windowBorder(palette, SourceDialog.WINDOW_PADDING),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        content.add(heading, BorderLayout.NORTH);
        content.add(rows.panel(), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        content.registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        ComicWindowDragger.install(heading);
        setContentPane(content);

        load(existing, presetCategory);
        category.addActionListener(event -> categoryChanged());
        useCertificate.addActionListener(event -> categoryChanged());
        categoryChanged();
        pack();
        setMinimumSize(new Dimension(Math.max(560, getWidth()), getHeight()));
        ComicWindowShape.install(this, content, palette, SourceDialog.WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        setLocationRelativeTo(owner);
    }

    /**
     * Zeigt den Editor modal (nur auf dem EDT). {@code existing} trägt beim Bearbeiten das gelesene Passwort.
     *
     * @param presetCategory Kategorie eines neuen Eintrags (z. B. aus dem Quellentyp), {@code null} = erste
     * @return der Titel des gespeicherten Eintrags oder {@code null}, wenn abgebrochen
     */
    static String show(Component parent, PasswordStore store, PasswordEntry existing, String presetCategory,
                       ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        Window owner = parent == null ? null : (parent instanceof Window ? (Window) parent
                : javax.swing.SwingUtilities.getWindowAncestor(parent));
        PasswordEntryDialog dialog = new PasswordEntryDialog(owner, store, existing, presetCategory, colors);
        try {
            dialog.setVisible(true);
            return dialog.savedTitle;
        } finally {
            dialog.wipe();
        }
    }

    /** Die MainframeMate-Kategorie zu einem Quellennamen (z. B. „Confluence“, „MediaWiki“, „FTP“). */
    static String categoryFor(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.contains("confluence")) {
            return "Confluence";
        }
        if (lower.contains("sharepoint")) {
            return "SP";
        }
        if (lower.contains("wiki")) {
            return "Wiki";
        }
        if (lower.contains("ftp") || lower.contains("ndv") || lower.contains("mainframe") || lower.contains("natural")) {
            return "Mainframe";
        }
        return name == null ? "" : name.trim();
    }

    private void load(PasswordEntry entry, String presetCategory) {
        if (entry == null) {
            String preset = presetCategory == null ? "" : presetCategory.trim();
            VaultAccess.setText(category, preset.isEmpty() ? PasswordEntry.categories().get(0) : preset);
            network.setSelectedItem(EXTERN_LABEL);
            requiresLogin.setSelected(true);
            savePassword.setSelected(true);
            useCertificate.setSelected(true);
            return;
        }
        VaultAccess.setText(category, entry.category().isEmpty() ? PasswordEntry.categories().get(0)
                : entry.category());
        network.setSelectedItem(entry.intern() ? INTERN_LABEL : EXTERN_LABEL);
        id.setText(entry.title());
        id.setEditable(false);
        displayName.setText(entry.displayName());
        userName.setText(entry.userName());
        char[] secret = entry.copyPassword();
        if (secret != null) {
            password.setText(new String(secret));
            Arrays.fill(secret, '\0');
        }
        url.setText(entry.url());
        certAlias.setText(entry.field(PasswordEntry.CERT_ALIAS));
        useCertificate.setSelected(!entry.field(PasswordEntry.CERT_ALIAS).trim().isEmpty() || !isConfluence());
        requiresLogin.setSelected(entry.flag(PasswordEntry.REQUIRES_LOGIN, true));
        useProxy.setSelected(entry.flag(PasswordEntry.USE_PROXY, false));
        autoIndex.setSelected(entry.flag(PasswordEntry.AUTO_INDEX, false));
        savePassword.setSelected(entry.flag(PasswordEntry.SAVE_PASSWORD, true));
        sessionCache.setSelected(entry.flag(PasswordEntry.SESSION_CACHE, false));
    }

    private String categoryText() {
        return VaultAccess.text(category);
    }

    private boolean isConfluence() {
        return "confluence".equalsIgnoreCase(categoryText());
    }

    private boolean urlRequired() {
        String value = categoryText();
        return "Wiki".equalsIgnoreCase(value) || "SP".equalsIgnoreCase(value) || "Confluence".equalsIgnoreCase(value);
    }

    private void categoryChanged() {
        certificateRow.setVisible(isConfluence());
        certAliasRow.setVisible(isConfluence() && useCertificate.isSelected());
        urlRow.setLabelText(urlRequired() ? "URL: *" : "URL:");
        testButton.setVisible(isConfluence());
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /** Wie MainframeMate: Confluence mit den eingegebenen Werten erreichen, ohne zu speichern. */
    private void testConnection() {
        if (url.getText().trim().isEmpty()) {
            error.setForeground(ResearchUiPalette.DANGER_RED);
            error.setText("Für " + categoryText() + "-Einträge ist die URL ein Pflichtfeld.");
            return;
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put(PasswordEntry.CERT_ALIAS, useCertificate.isSelected() ? certAlias.getText().trim() : "");
        char[] secret = password.getPassword();
        final PasswordEntry probe = new PasswordEntry(id.getText().trim().isEmpty() ? "test" : id.getText().trim(),
                userName.getText().trim(), url.getText().trim(), fields, secret);
        Arrays.fill(secret, '\0');
        testButton.setEnabled(false);
        error.setForeground(palette.getInk());
        error.setText("Teste Verbindung …");
        store.testConnection(probe, (message, failure) -> {
            probe.wipe();
            testButton.setEnabled(true);
            error.setForeground(failure == null ? palette.getInk() : ResearchUiPalette.DANGER_RED);
            error.setText(FormRows.html(failure == null ? message : failure));
            pack();
        });
    }

    private void save() {
        error.setForeground(ResearchUiPalette.DANGER_RED);
        String title = id.getText().trim();
        if (title.isEmpty()) {
            error.setText("Die ID darf nicht leer sein.");
            return;
        }
        if (urlRequired() && url.getText().trim().isEmpty()) {
            error.setText("Für " + categoryText() + "-Einträge ist die URL ein Pflichtfeld.");
            return;
        }
        if (isConfluence() && useCertificate.isSelected() && certAlias.getText().trim().isEmpty()
                && !PasswordManagerPanel.confirm(this, "Kein Zertifikat gewählt",
                "„Zertifikat verwenden“ ist an, aber kein Zertifikat eingetragen.",
                "Ohne Zertifikat speichern", "Zurück zum Bearbeiten", palette)) {
            return;
        }
        Map<String, String> fields = new LinkedHashMap<String, String>(
                existing == null ? new LinkedHashMap<String, String>() : existing.fields());
        fields.put(PasswordEntry.DISPLAY_NAME, displayName.getText().trim());
        fields.put(PasswordEntry.CATEGORY, categoryText());
        fields.put(PasswordEntry.NETWORK_ZONE, INTERN_LABEL.equals(network.getSelectedItem())
                ? PasswordEntry.ZONE_INTERN : PasswordEntry.ZONE_EXTERN);
        fields.put(PasswordEntry.REQUIRES_LOGIN, Boolean.toString(requiresLogin.isSelected()));
        fields.put(PasswordEntry.USE_PROXY, Boolean.toString(useProxy.isSelected()));
        fields.put(PasswordEntry.AUTO_INDEX, Boolean.toString(autoIndex.isSelected()));
        fields.put(PasswordEntry.SAVE_PASSWORD, Boolean.toString(savePassword.isSelected()));
        fields.put(PasswordEntry.SESSION_CACHE, Boolean.toString(sessionCache.isSelected()));
        fields.put(PasswordEntry.CERT_ALIAS, isConfluence() && useCertificate.isSelected()
                ? certAlias.getText().trim() : "");
        char[] secret = password.getPassword();
        final PasswordEntry entry = new PasswordEntry(title, userName.getText().trim(), url.getText().trim(), fields,
                secret);
        Arrays.fill(secret, '\0');
        ok.setEnabled(false);
        error.setText("Speichere in KeePass …");
        store.save(entry, (value, failure) -> {
            entry.wipe();
            ok.setEnabled(true);
            if (failure != null) {
                error.setText(FormRows.html(failure));
                pack();
                return;
            }
            savedTitle = entry.title();
            dispose();
        });
    }

    private void wipe() {
        password.setText("");
    }

    // Für Tests.

    JTextField idField() {
        return id;
    }

    JPasswordField passwordField() {
        return password;
    }

    JLabel errorLabel() {
        return error;
    }
}
