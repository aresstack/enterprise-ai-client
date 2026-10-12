package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.paint.StrokeIcon;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Download display after the AskAI pattern: a thin bar that carries percent and bytes as its text, a small stop
 * cross exactly as high as the bar without a button plate, and the current file as a small secondary line below.
 */
public final class DownloadProgress extends JPanel {

    /** Air above and below the bar text inside the line border; keeps the bar one text line high. */
    private static final int TEXT_PADDING = 1;
    /** Gap between bar and stop cross: close enough that the cross reads as part of the bar, as in AskAI. */
    private static final int CROSS_GAP = 4;

    private final ComicBar bar = new ComicBar();
    private final JButton cancel = new JButton();
    private final JLabel caption = new JLabel(" ");

    public DownloadProgress(String cancelHint) {
        super(new BorderLayout(0, TEXT_PADDING + 1));
        setOpaque(false);
        bar.getAccessibleContext().setAccessibleName("Download-Fortschritt");
        cancel.setIcon(new Cross(bar.getPreferredSize().height));
        cancel.setToolTipText(cancelHint);
        cancel.getAccessibleContext().setAccessibleName(cancelHint);
        cancel.setContentAreaFilled(false);
        cancel.setBorderPainted(false);
        cancel.setFocusPainted(false);
        cancel.setOpaque(false);
        cancel.setMargin(new Insets(0, 0, 0, 0));
        cancel.setBorder(BorderFactory.createEmptyBorder());
        cancel.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        cancel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                cancel.setForeground(ResearchUiPalette.DANGER_RED);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                cancel.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
            }
        });
        int size = bar.getPreferredSize().height;
        cancel.setPreferredSize(new Dimension(size, size));
        JPanel line = new JPanel(new BorderLayout(CROSS_GAP, 0));
        line.setOpaque(false);
        line.add(bar, BorderLayout.CENTER);
        line.add(cancel, BorderLayout.EAST);
        caption.setFont(caption.getFont().deriveFont(Font.PLAIN, caption.getFont().getSize2D() - 1f));
        caption.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        caption.putClientProperty("html.disable", Boolean.TRUE);
        add(line, BorderLayout.NORTH);
        add(caption, BorderLayout.CENTER);
    }

    /** A phase without a known size, e.g. „Vorbereiten …“: the bar runs indeterminate with the text in it. */
    public void preparing(String text) {
        bar.setIndeterminate(true);
        bar.setValue(0);
        show(text);
    }

    /** The file being loaded: percent and bytes in the bar, „Datei n von m · name“ below. */
    public void file(String name, int number, int count, long done, long total) {
        int percent = DownloadText.percent(done, total);
        bar.setIndeterminate(percent < 0);
        bar.setValue(Math.max(0, percent));
        show(DownloadText.amount(done, total));
        caption.setText(DownloadText.file(name, number, count));
        caption.setToolTipText(caption.getText());
    }

    /** The stop was requested; the transfer ends at its next data block. */
    public void cancelling() {
        bar.setIndeterminate(true);
        show("Download wird abgebrochen …");
        cancel.setEnabled(false);
    }

    /** Start over for a new download: stop cross usable again, no file yet. */
    public void reset(String text) {
        cancel.setEnabled(true);
        caption.setText(" ");
        caption.setToolTipText(null);
        preparing(text);
    }

    public JProgressBar bar() {
        return bar;
    }

    public JButton cancelButton() {
        return cancel;
    }

    /** The secondary line below the bar; blank while no file is loading. */
    public String caption() {
        return caption.getText().trim();
    }

    private void show(String text) {
        bar.setString(text);
        bar.setToolTipText(text);
    }

    /** Thin bar in the comic colours, independent of the look and feel. */
    private static final class ComicBar extends JProgressBar {
        ComicBar() {
            super(0, 100);
            setStringPainted(true);
            setOpaque(true);
            setForeground(ResearchUiPalette.ACCENT_BLUE);
            setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
            setBorder(BorderFactory.createLineBorder(ResearchUiPalette.LIGHT_CONTROL_BORDER));
        }

        @Override
        public void updateUI() {
            setUI(new ComicBarUi());
        }
    }

    private static final class ComicBarUi extends BasicProgressBarUI {
        @Override
        protected Color getSelectionForeground() {
            return ResearchUiPalette.SCROLL_PAPER;
        }

        @Override
        protected Color getSelectionBackground() {
            return ResearchUiPalette.LIGHT_CONTROL_TEXT;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension size = super.getPreferredSize(c);
            FontMetrics metrics = c.getFontMetrics(c.getFont());
            Insets insets = c.getInsets();
            size.height = metrics.getHeight() + 2 * TEXT_PADDING + insets.top + insets.bottom;
            return size;
        }
    }

    /** Stop cross scaled to the bar height, drawn in the button foreground. */
    private static final class Cross extends StrokeIcon {
        private final int size;

        Cross(int size) {
            super(size, size);
            this.size = size;
        }

        @Override
        protected void paint(Graphics2D g2) {
            int inset = size / 4;
            int end = size - 1 - inset;
            g2.drawLine(inset, inset, end, end);
            g2.drawLine(end, inset, inset, end);
        }
    }
}
