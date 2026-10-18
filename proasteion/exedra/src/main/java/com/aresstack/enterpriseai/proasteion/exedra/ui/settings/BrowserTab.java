package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab "Browser" (issue #141): the global access policy of the chat browsers. Mode (block by default with a
 * whitelist, or allow by default with a blacklist) and both rule lists, one rule per line
 * ({@code browser.access.mode}, {@code browser.access.allow}, {@code browser.access.block}).
 */
final class BrowserTab {

    static final String BLOCK_LABEL = "Standardmäßig blockieren — nur Websites aus der Whitelist sind erreichbar.";
    static final String ALLOW_LABEL = "Standardmäßig erlauben — nur Websites aus der Blacklist werden blockiert.";
    static final String RULE_HINT = "Eine Regel je Zeile: example.org = genau dieser Host, *.example.org = "
            + "Subdomains; Blacklist gewinnt immer";
    static final String NOTE = "Gilt für alle Chat-Browser. Bei „Standardmäßig blockieren“ fragt der Chat bei "
            + "unbekannten Websites nach: Einmal erlauben, Immer erlauben, Immer verbieten oder Abbrechen. "
            + "„Immer“ trägt den Host hier ein. Auch das Ziel einer Weiterleitung wird geprüft.";

    private final JRadioButton blockByDefault;
    private final JRadioButton allowByDefault;
    private final JTextArea whitelist;
    private final JTextArea blacklist;
    private final JPanel panel;

    BrowserTab(ComicPalette palette) {
        FormRows mode = new FormRows(palette);
        mode.note(NOTE);
        blockByDefault = radio(BLOCK_LABEL, palette);
        allowByDefault = radio(ALLOW_LABEL, palette);
        ButtonGroup group = new ButtonGroup();
        group.add(blockByDefault);
        group.add(allowByDefault);
        blockByDefault.setSelected(true);
        mode.component(null, blockByDefault, null);
        mode.component(null, allowByDefault, null);

        FormRows lists = new FormRows(palette);
        lists.note(RULE_HINT);
        whitelist = lists.textArea("Whitelist", 6, RULE_HINT);
        whitelist.setLineWrap(false);
        whitelist.getAccessibleContext().setAccessibleName("Whitelist");
        blacklist = lists.textArea("Blacklist", 6, RULE_HINT);
        blacklist.setLineWrap(false);
        blacklist.getAccessibleContext().setAccessibleName("Blacklist");

        panel = FormRows.column(palette,
                FormRows.plate("Zugriffsrichtlinie", palette.getAccentOrange(), mode.panel(), palette),
                FormRows.plate("Whitelist und Blacklist", palette.getNavigationBlue(), lists.panel(), palette));
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
        if (form.browserAllowByDefault()) {
            allowByDefault.setSelected(true);
        } else {
            blockByDefault.setSelected(true);
        }
        whitelist.setText(lines(form.browserAllow()));
        blacklist.setText(lines(form.browserBlock()));
    }

    void store(SettingsForm.Builder b) {
        b.browserAllowByDefault(allowByDefault.isSelected())
                .browserAllow(rules(whitelist.getText()))
                .browserBlock(rules(blacklist.getText()));
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

    JRadioButton blockByDefault() {
        return blockByDefault;
    }

    JRadioButton allowByDefault() {
        return allowByDefault;
    }

    JTextArea whitelist() {
        return whitelist;
    }

    JTextArea blacklist() {
        return blacklist;
    }
}
