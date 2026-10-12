package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Presents Merlin's retirement cave without accessing an AI provider or changing settings.
 */
public final class MerlinCavePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String IMAGE_RESOURCE = "/merlin-crystal-cave.jpg";

    public MerlinCavePanel(ComicPalette palette) {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Merlin – Legacy Intelligence", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(palette.getInk());
        add(title, BorderLayout.NORTH);

        BufferedImage image = loadCaveImage();
        JPanel picture = image == null ? missingImage(palette) : new ScaledImagePanel(image);
        picture.setPreferredSize(new Dimension(480, 280));
        add(picture, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JLabel status = new JLabel("Aufenthaltsort: Kristallhöhle  ·  Status: Standby seit Einführung der KI",
                SwingConstants.CENTER);
        status.setForeground(palette.getInk());
        footer.add(status, BorderLayout.NORTH);
        JButton ask = new JButton("Merlin um Rat fragen");
        ask.addActionListener(event -> JOptionPane.showMessageDialog(this,
                "Ach, jetzt erinnert ihr euch wieder an mich?\n"
                + "Ruft mich, wenn eure künstliche Intelligenz versagt.",
                "Merlin antwortet", JOptionPane.INFORMATION_MESSAGE));
        footer.add(ask, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
    }

    private BufferedImage loadCaveImage() {
        try (InputStream stream = getClass().getResourceAsStream(IMAGE_RESOURCE)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException exception) {
            return null;
        }
    }

    private JPanel missingImage(ComicPalette palette) {
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setOpaque(false);
        JLabel message = new JLabel("Merlins Kristallhöhle wartet auf ihr Bild.", SwingConstants.CENTER);
        message.setForeground(palette.getInk());
        placeholder.add(message, BorderLayout.CENTER);
        return placeholder;
    }

    private static final class ScaledImagePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Image image;

        private ScaledImagePanel(BufferedImage image) {
            this.image = new ImageIcon(image).getImage();
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int imageWidth = image.getWidth(this);
            int imageHeight = image.getHeight(this);
            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }
            double scale = Math.min((double) getWidth() / imageWidth,
                    (double) getHeight() / imageHeight);
            int width = (int) Math.round(imageWidth * scale);
            int height = (int) Math.round(imageHeight * scale);
            graphics.drawImage(image, (getWidth() - width) / 2,
                    (getHeight() - height) / 2, width, height, this);
        }
    }
}
