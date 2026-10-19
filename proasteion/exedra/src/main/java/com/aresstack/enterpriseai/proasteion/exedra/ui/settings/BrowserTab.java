package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab "Browser" (issue #141): the access policy of the chat browsers per profile. Each chat uses Extern (default) or
 * Intern (switch in the globe popup); both profiles have identical fields: mode (block by default with a whitelist,
 * or allow by default with a blacklist), both rule lists and the search URLs the chat gets in its system prompt
 * ({@code browser.access.*} and {@code browser.search.urls} for Extern, {@code browser.access.internal.*} and
 * {@code browser.search.internal.urls} for Intern).
 */
final class BrowserTab {

    static final String BLOCK_LABEL = "Standardmäßig blockieren — nur Websites aus der Whitelist sind erreichbar.";
    static final String ALLOW_LABEL = "Standardmäßig erlauben — nur Websites aus der Blacklist werden blockiert.";
    static final String RULE_HINT = "Eine Regel je Zeile: example.org = genau dieser Host, *.example.org = "
            + "Subdomains; Blacklist gewinnt immer";
    static final String SEARCH_HINT = "Eine Such-URL je Zeile, {query} steht für die Suchbegriffe. "
            + "Der Chat bekommt sie bei eingeschaltetem Browser.";
    static final String NOTE = "Jeder Chat nutzt das Profil Extern oder Intern (Umschalter im Browser-Popup am "
            + "Globus, Standard Extern). Bei „Standardmäßig blockieren“ fragt der Chat bei unbekannten Websites nach: "
            + "Einmal erlauben, Immer erlauben, Immer verbieten oder Abbrechen. „Immer“ trägt den Host in das aktive "
            + "Profil ein. Auch das Ziel einer Weiterleitung wird geprüft.";

    /** The identical fields of one profile. */
    static final class Profile {

        final JRadioButton blockByDefault;
        final JRadioButton allowByDefault;
        final JTextArea whitelist;
        final JTextArea blacklist;
        final JTextArea searchUrls;
        final JPanel panel;

        Profile(String name, Color accent, ComicPalette palette) {
            FormRows rows = new FormRows(palette);
            blockByDefault = radio(BLOCK_LABEL, palette);
            allowByDefault = radio(ALLOW_LABEL, palette);
            ButtonGroup group = new ButtonGroup();
            group.add(blockByDefault);
            group.add(allowByDefault);
            blockByDefault.setSelected(true);
            rows.component(null, blockByDefault, null);
            rows.component(null, allowByDefault, null);
            rows.note(RULE_HINT);
            whitelist = area(rows, "Whitelist", name, RULE_HINT);
            blacklist = area(rows, "Blacklist", name, RULE_HINT);
            searchUrls = area(rows, "Such-URLs", name, SEARCH_HINT);
            panel = FormRows.plate("Profil " + name, accent, rows.panel(), palette);
        }

        private static JTextArea area(FormRows rows, String label, String profile, String hint) {
            JTextArea area = rows.textArea(label, 5, hint);
            area.setLineWrap(false);
            area.getAccessibleContext().setAccessibleName(label + " " + profile);
            return area;
        }

        void load(boolean allowMode, List<String> allow, List<String> block, List<String> search) {
            if (allowMode) {
                allowByDefault.setSelected(true);
            } else {
                blockByDefault.setSelected(true);
            }
            whitelist.setText(lines(allow));
            blacklist.setText(lines(block));
            searchUrls.setText(lines(search));
        }
    }

    private final Profile external;
    private final Profile internal;
    private final JPanel panel;

    BrowserTab(ComicPalette palette) {
        FormRows intro = new FormRows(palette);
        intro.note(NOTE);
        external = new Profile("Extern", palette.getNavigationBlue(), palette);
        internal = new Profile("Intern", palette.getAccentOrange(), palette);
        panel = FormRows.column(palette,
                FormRows.plate("Browserprofile", palette.getAccentOrange(), intro.panel(), palette),
                external.panel, internal.panel);
    }

    private static JRadioButton radio(String label, ComicPalette palette) {
        JRadioButton button = new JRadioButton(label);
        button.setOpaque(false);
        button.setForeground(palette.getInk());
        button.setFocusPainted(false);
        return button;
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        external.load(form.browserAllowByDefault(), form.browserAllow(), form.browserBlock(),
                form.browserSearchUrls());
        internal.load(form.browserInternalAllowByDefault(), form.browserInternalAllow(), form.browserInternalBlock(),
                form.browserInternalSearchUrls());
    }

    void store(SettingsForm.Builder b) {
        b.browserAllowByDefault(external.allowByDefault.isSelected())
                .browserAllow(rules(external.whitelist.getText()))
                .browserBlock(rules(external.blacklist.getText()))
                .browserSearchUrls(urls(external.searchUrls.getText()))
                .browserInternalAllowByDefault(internal.allowByDefault.isSelected())
                .browserInternalAllow(rules(internal.whitelist.getText()))
                .browserInternalBlock(rules(internal.blacklist.getText()))
                .browserInternalSearchUrls(urls(internal.searchUrls.getText()));
    }

    private static String lines(List<String> rules) {
        StringBuilder text = new StringBuilder();
        for (String rule : rules) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(rule);
        }
        return text.toString();
    }

    /** One rule per line; commas also separate, blanks are dropped. */
    static List<String> rules(String text) {
        List<String> rules = new ArrayList<String>();
        if (text != null) {
            for (String part : text.split("[,\\r\\n]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !rules.contains(trimmed)) {
                    rules.add(trimmed);
                }
            }
        }
        return rules;
    }

    /** One search URL per line (URLs may contain commas); blanks and duplicates are dropped. */
    static List<String> urls(String text) {
        List<String> urls = new ArrayList<String>();
        if (text != null) {
            for (String part : text.split("\\s+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !urls.contains(trimmed)) {
                    urls.add(trimmed);
                }
            }
        }
        return urls;
    }

    /** The fields of profile Extern. */
    Profile external() {
        return external;
    }

    /** The fields of profile Intern. */
    Profile internal() {
        return internal;
    }

    JRadioButton blockByDefault() {
        return external.blockByDefault;
    }

    JRadioButton allowByDefault() {
        return external.allowByDefault;
    }

    JTextArea whitelist() {
        return external.whitelist;
    }

    JTextArea blacklist() {
        return external.blacklist;
    }
}
