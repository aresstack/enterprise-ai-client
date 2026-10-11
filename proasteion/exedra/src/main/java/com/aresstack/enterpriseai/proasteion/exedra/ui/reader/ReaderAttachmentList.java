package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Die Anhangsliste hinter der Büroklammer unten im Lesebereich, wie die Anhänge einer E-Mail: je Anhang ein
 * gezeichnetes Dateisymbol (Bild oder Blatt), der Dateiname und darunter „Typ · Größe“. Ein Klick öffnet den Anhang
 * im Overlay. Comic-Rahmen wie die Menüs, die Zeile unter der Maus bekommt die gelbe Platte. Mehr als
 * {@link #VISIBLE_ROWS} Einträge rollen.
 */
final class ReaderAttachmentList {

    static final int VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT = 40;

    private ReaderAttachmentList() {
    }

    /** Tooltip der Büroklammer: „Anhänge (4)“. */
    static String tooltip(int count) {
        return count == 1 ? "1 Anhang" : count + " Anhänge";
    }

    static JPopupMenu menu(List<ReaderAttachment> attachments, ComicPalette palette, final Consumer<Integer> open) {
        final JPopupMenu menu = new JPopupMenu();
        menu.setBorder(ComicBorder.popupBorder(palette));
        JLabel header = new JLabel(tooltip(attachments.size()));
        header.setFont(ResearchUiTypography.semiBold(12f));
        header.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        for (int i = 0; i < attachments.size(); i++) {
            final int index = i;
            rows.add(new Row(attachments.get(i), palette, () -> {
                menu.setVisible(false);
                open.accept(index);
            }));
        }
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        content.add(header, BorderLayout.NORTH);
        if (attachments.size() > VISIBLE_ROWS) {
            JScrollPane scroll = new ComicScrollPane(rows, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, palette);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT / 2);
            scroll.setPreferredSize(new Dimension(Math.max(260, rows.getPreferredSize().width + 16),
                    VISIBLE_ROWS * ROW_HEIGHT));
            content.add(scroll, BorderLayout.CENTER);
        } else {
            content.add(rows, BorderLayout.CENTER);
        }
        menu.add(content);
        return menu;
    }

    /** Die Zeilen eines Menüs aus {@link #menu} (für Tests und Tastaturbedienung). */
    static List<Row> rows(Container root) {
        List<Row> found = new ArrayList<Row>();
        collect(root, found);
        return found;
    }

    private static void collect(Container root, List<Row> found) {
        for (Component child : root.getComponents()) {
            if (child instanceof Row) {
                found.add((Row) child);
            } else if (child instanceof Container) {
                collect((Container) child, found);
            }
        }
    }

    /** Ein Eintrag: Symbol, Name, „Typ · Größe“. */
    static final class Row extends JPanel {
        private final ReaderAttachment attachment;
        private final Runnable action;
        private final JLabel name;
        private final JLabel details;

        Row(ReaderAttachment attachment, final ComicPalette palette, Runnable action) {
            super(new BorderLayout(8, 0));
            this.attachment = attachment;
            this.action = action;
            setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(attachment.fileName());
            JLabel icon = new JLabel(iconFor(attachment));
            icon.setForeground(palette.getInk());
            name = new JLabel(attachment.fileName());
            name.setFont(ResearchUiTypography.semiBold(13f));
            name.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
            details = new JLabel(attachment.summary());
            details.setFont(ResearchUiTypography.regular(11f));
            details.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(name);
            text.add(details);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(palette.getAccentYellow());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!contains(e.getPoint())) {
                        setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    activate();
                }
            };
            addMouseListener(mouse);
        }

        ReaderAttachment attachment() {
            return attachment;
        }

        String nameText() {
            return name.getText();
        }

        String detailsText() {
            return details.getText();
        }

        /** Wie ein Klick: Liste zu, Anhang im Overlay. */
        void activate() {
            action.run();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(Math.max(240, Math.min(size.width, 520)), Math.max(ROW_HEIGHT, size.height));
        }
    }

    /** Gezeichnetes Symbol (keine Unicode-Zeichen): Bild für Bilder, sonst das Blatt. */
    static Icon iconFor(ReaderAttachment attachment) {
        return attachment.kind() == ReaderAttachmentKind.IMAGE ? ComposerIcons.picture() : ComposerIcons.document();
    }
}
