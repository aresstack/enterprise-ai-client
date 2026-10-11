package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Das kleine Popup hinter dem Info-Symbol unten im Lesebereich: Titel und vollständige Adresse des aktiven Reiters
 * (markierbar), darunter „Adresse kopieren“ (immer) und „Im Standardbrowser öffnen“ (nur für {@code http}/{@code https},
 * wie MainframeMates „Im Browser anzeigen“ im {@code LeftDrawer}; {@link ReaderLink#location()} kann auch eine andere
 * Fundstelle sein).
 */
final class ReaderPageInfo {

    static final String TOOLTIP = "Seiteninformationen";
    static final String COPY = "Adresse kopieren";
    static final String OPEN_IN_BROWSER = "Im Standardbrowser öffnen";
    private static final int ADDRESS_COLUMNS = 40;

    /** Schreibt in die Zwischenablage des Systems. */
    static final Consumer<String> SYSTEM_CLIPBOARD = text -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(text), null);

    private ReaderPageInfo() {
    }

    /** Die Adresse, die das Popup zeigt und kopiert. */
    static String address(ReaderLink link) {
        return link.location().isEmpty() ? link.resourceId() : link.location();
    }

    /** Ob die Adresse eine Webadresse ist, die der Standardbrowser öffnen kann. */
    static boolean isBrowsable(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        try {
            String scheme = new URI(address.trim()).getScheme();
            if (scheme == null) {
                return false;
            }
            String normalized = scheme.toLowerCase(Locale.ENGLISH);
            return "http".equals(normalized) || "https".equals(normalized);
        } catch (Exception notAUri) {
            return false;
        }
    }

    static JPopupMenu menu(ReaderLink link, final DesktopLinkOpener opener, final Consumer<String> clipboard) {
        final String address = address(link);
        JPopupMenu menu = new JPopupMenu();
        JTextArea text = new JTextArea(link.title() + "\n" + address);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(false);
        text.setColumns(ADDRESS_COLUMNS);
        text.setFont(ResearchUiTypography.regular(12f));
        text.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        text.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        menu.add(text);
        menu.addSeparator();
        JMenuItem copy = new JMenuItem(COPY);
        copy.addActionListener(event -> clipboard.accept(address));
        menu.add(copy);
        if (isBrowsable(address)) {
            JMenuItem open = new JMenuItem(OPEN_IN_BROWSER);
            open.addActionListener(event -> opener.open(address.trim()));
            menu.add(open);
        }
        return menu;
    }
}
