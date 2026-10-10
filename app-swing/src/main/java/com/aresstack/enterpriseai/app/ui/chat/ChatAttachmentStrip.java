package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Reihe der vorgemerkten Anhänge über dem Editor (nach askai-java8 arch, {@code ChatAttachmentStrip}): je
 * Datei ein Chip mit Namen und ✕. Hält nur Pfade, nie Dateiinhalte; doppelte Dateien werden übergangen.
 */
final class ChatAttachmentStrip extends JPanel {

    /** Nach jeder Änderung (hinzufügen, entfernen, leeren). */
    interface ChangeListener {
        void onAttachmentsChanged();
    }

    private static final Color CHIP_BG = new Color(0xECEFF3);
    private static final Color CHIP_BORDER = ResearchUiPalette.LIGHT_CONTROL_BORDER;

    private final List<Path> attachments = new ArrayList<Path>();
    private final ChangeListener changeListener;

    ChatAttachmentStrip(ChangeListener changeListener) {
        this.changeListener = changeListener;
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        setVisible(false);
    }

    void addAttachments(List<Path> toAdd) {
        boolean changed = false;
        for (Path file : toAdd) {
            Path normalized = file.toAbsolutePath().normalize();
            if (!attachments.contains(normalized)) {
                attachments.add(normalized);
                changed = true;
            }
        }
        if (changed) {
            rebuild();
        }
    }

    void removeAttachment(Path file) {
        if (attachments.remove(file)) {
            rebuild();
        }
    }

    void clear() {
        if (!attachments.isEmpty()) {
            attachments.clear();
            rebuild();
        }
    }

    List<Path> getAttachments() {
        return Collections.unmodifiableList(new ArrayList<Path>(attachments));
    }

    int count() {
        return attachments.size();
    }

    private void rebuild() {
        removeAll();
        for (Path file : attachments) {
            add(createChip(file));
        }
        setVisible(!attachments.isEmpty());
        revalidate();
        repaint();
        if (changeListener != null) {
            changeListener.onAttachmentsChanged();
        }
    }

    private Component createChip(final Path file) {
        JPanel chip = new JPanel(new BorderLayout(4, 0)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CHIP_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(CHIP_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
        String fileName = file.getFileName().toString();
        JLabel name = new JLabel(ellipsize(fileName, 28));
        name.setForeground(ResearchUiPalette.TEXT_DARK);
        name.setToolTipText(file.toString());
        chip.add(name, BorderLayout.CENTER);

        JButton remove = new JButton("\u00D7");
        remove.setToolTipText(fileName + " entfernen");
        remove.getAccessibleContext().setAccessibleName(fileName + " entfernen");
        remove.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        remove.setRequestFocusEnabled(false); // per Tab erreichbar, ein Klick nimmt dem Editor nicht den Fokus
        remove.setBorderPainted(false);
        remove.setContentAreaFilled(false);
        remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        remove.setMargin(new Insets(0, 4, 0, 2));
        remove.addActionListener(event -> removeAttachment(file));
        chip.add(remove, BorderLayout.EAST);
        return chip;
    }

    private static String ellipsize(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
