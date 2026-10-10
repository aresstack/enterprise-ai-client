package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Baut Formularzeilen im Comic-Stil: links die Beschriftung in Tinte, rechts das Feld auf weißer Platte mit
 * Tintenkontur ({@link ComicBorder}); Hinweise als gedämpfter Text über beide Spalten. Jede Zeile lässt sich
 * als Ganzes ein- und ausblenden (Quellen zeigen je Typ andere Felder).
 */
final class FormRows {

    /** Gedämpfter, aber lesbarer Hinweistext (wie der Platzhalter des Composers). */
    static final Color MUTED = new Color(0x6B6F76);
    private static final int NOTE_WIDTH = 460;

    private final ComicPalette palette;
    private final JPanel panel = new JPanel(new GridBagLayout());
    private int row;

    FormRows(ComicPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        this.palette = palette;
        panel.setOpaque(false);
    }

    JPanel panel() {
        return panel;
    }

    /** Eine Zeile: Beschriftung und Feld; {@link #setVisible} blendet beide aus. */
    static final class Row {
        private final JLabel label;
        private final JComponent field;

        Row(JLabel label, JComponent field) {
            this.label = label;
            this.field = field;
        }

        void setVisible(boolean visible) {
            if (label != null) {
                label.setVisible(visible);
            }
            field.setVisible(visible);
        }

        JComponent field() {
            return field;
        }
    }

    Row textField(JTextField field, String label, String tooltip) {
        style(field);
        return component(label, field, tooltip);
    }

    JTextField textField(String label, String tooltip) {
        JTextField field = new JTextField(28);
        textField(field, label, tooltip);
        return field;
    }

    JTextArea textArea(String label, int rows, String tooltip) {
        JTextArea area = new JTextArea(rows, 28);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        style(area);
        component(label, area, tooltip);
        return area;
    }

    JCheckBox checkBox(String label, String tooltip) {
        JCheckBox box = new JCheckBox(label);
        box.setOpaque(false);
        box.setForeground(palette.getInk());
        box.setFocusPainted(false);
        component(null, box, tooltip);
        return box;
    }

    JComboBox<String> comboBox(String label, String tooltip, String... values) {
        JComboBox<String> combo = new JComboBox<String>(values);
        combo.setForeground(palette.getInk());
        combo.setBackground(Color.WHITE);
        component(label, combo, tooltip);
        return combo;
    }

    /** Beschriftung links (leer erlaubt), Komponente rechts über die volle Breite. */
    Row component(String label, JComponent component, String tooltip) {
        JLabel caption = null;
        if (label != null) {
            caption = new JLabel(label);
            caption.setForeground(palette.getInk());
            caption.setLabelFor(component);
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0;
            left.gridy = row;
            left.anchor = GridBagConstraints.NORTHWEST;
            left.insets = new Insets(4, 0, 4, 10);
            panel.add(caption, left);
        }
        if (tooltip != null) {
            component.setToolTipText(tooltip);
            if (caption != null) {
                caption.setToolTipText(tooltip);
            }
        }
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.NORTHWEST;
        right.insets = new Insets(4, 0, 4, 0);
        panel.add(component, right);
        row++;
        return new Row(caption, component);
    }

    /** Hinweis über beide Spalten; bricht um. */
    JLabel note(String text) {
        JLabel note = new JLabel(html(text));
        note.setForeground(MUTED);
        note.setFont(note.getFont().deriveFont(Font.PLAIN, Math.max(11f, note.getFont().getSize2D() - 1f)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(2, 0, 6, 0);
        panel.add(note, c);
        row++;
        return note;
    }

    /** Lässt den Rest der Spalte nach unten frei (damit Plates oben bleiben). */
    void glue() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), c);
        row++;
    }

    void style(JTextComponent field) {
        field.setBorder(ComicBorder.roundedBorder(palette, 4));
        field.setForeground(palette.getInk());
        field.setCaretColor(palette.getInk());
        field.setBackground(Color.WHITE);
        field.setOpaque(true);
    }

    /** Eine Comic-Platte mit Überschrift und Akzentstreifen um einen Inhalt. */
    static ComicSectionPanel plate(String heading, Color stripe, JComponent content, ComicPalette palette) {
        ComicSectionPanel plate = new ComicSectionPanel(palette);
        plate.setAccentStripe(stripe);
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        plate.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 12));
        JLabel title = new JLabel(heading);
        title.setForeground(palette.getInk());
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        plate.add(title);
        plate.add(Box.createVerticalStrut(6));
        content.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        plate.add(content);
        return plate;
    }

    /** Eine Spalte aus Platten mit Abstand, oben ausgerichtet. */
    static JPanel column(ComicPalette palette, JComponent... plates) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(true);
        column.setBackground(palette.getSurface());
        column.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        for (JComponent plate : plates) {
            // BoxLayout dehnt Platten auf die freie Höhe; der Rahmen hält jede auf ihrer Wunschhöhe am oberen Rand.
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(plate, BorderLayout.NORTH);
            top.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            column.add(top);
            column.add(Box.createVerticalStrut(10));
        }
        column.add(Box.createVerticalGlue());
        return column;
    }

    /** Umbrechender Hinweistext in der Breite von {@link #note}. */
    static String html(String text) {
        return "<html><body style='width: " + NOTE_WIDTH + "px'>" + escape(text) + "</body></html>";
    }

    static String escape(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
