package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Displays Merlin's cave with a lightweight, purely decorative Java2D overlay.
 * The image is never modified and no AI service is contacted.
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
        final CrystalScene scene = image == null ? null : new CrystalScene(image);
        JPanel picture = scene == null ? missingImage(palette) : scene;
        picture.setPreferredSize(new Dimension(480, 280));
        add(picture, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JLabel status = new JLabel("Aufenthaltsort: Kristallhöhle  ·  Status: Standby seit Einführung der KI",
                SwingConstants.CENTER);
        status.setForeground(palette.getInk());
        footer.add(status, BorderLayout.NORTH);

        JButton ask = new JButton("Merlin um Rat fragen");
        ask.addActionListener(event -> {
            if (scene != null && scene.isShowing()) {
                scene.awaken(() -> showMerlinReply());
            } else {
                showMerlinReply();
            }
        });
        footer.add(ask, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
    }

    private void showMerlinReply() {
        JOptionPane.showMessageDialog(this,
                "Ach, jetzt erinnert ihr euch wieder an mich?\n"
                + "Ruft mich, wenn eure künstliche Intelligenz versagt.",
                "Merlin antwortet", JOptionPane.INFORMATION_MESSAGE);
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

    /**
     * Paints light in image-relative coordinates, preserving alignment at every window size.
     * Crystal positions are normalized and can be refined against the final artwork.
     */
    private static final class CrystalScene extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int FRAME_DELAY_MS = 40;
        private static final long AWAKEN_DURATION_NS = 1100000000L;

        // Selected image-relative glints; never scatter random stars across Merlin's face.
        private static final double[][] CRYSTALS = {
                {0.09, 0.24, 0.78}, {0.16, 0.52, 0.95}, {0.23, 0.17, 0.72},
                {0.30, 0.72, 0.90}, {0.37, 0.31, 0.65}, {0.44, 0.11, 0.75},
                {0.55, 0.15, 0.78}, {0.64, 0.31, 0.85}, {0.73, 0.13, 0.76},
                {0.83, 0.35, 1.00}, {0.91, 0.19, 0.82}, {0.94, 0.71, 0.87},
                {0.75, 0.81, 0.83}, {0.57, 0.89, 0.70}, {0.20, 0.86, 0.84}
        };

        private final BufferedImage image;
        private final Timer timer;
        private final long origin = System.nanoTime();
        private long awakenStart = -1L;
        private Runnable afterAwaken;

        private CrystalScene(BufferedImage image) {
            this.image = image;
            setOpaque(false);
            timer = new Timer(FRAME_DELAY_MS, event -> {
                if (awakenStart > 0L
                        && System.nanoTime() - awakenStart >= AWAKEN_DURATION_NS) {
                    awakenStart = -1L;
                    Runnable callback = afterAwaken;
                    afterAwaken = null;
                    if (callback != null) {
                        callback.run();
                    }
                }
                repaint();
            });
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) {
                        timer.start();
                    } else {
                        timer.stop();
                        awakenStart = -1L;
                        afterAwaken = null;
                    }
                }
            });
        }

        private void awaken(Runnable callback) {
            if (awakenStart > 0L) {
                return;
            }
            afterAwaken = callback;
            awakenStart = System.nanoTime();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.min((double) getWidth() / image.getWidth(),
                        (double) getHeight() / image.getHeight());
                int width = (int) Math.round(image.getWidth() * scale);
                int height = (int) Math.round(image.getHeight() * scale);
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;
                g.drawImage(image, x, y, width, height, this);

                Graphics2D overlay = (Graphics2D) g.create(x, y, width, height);
                try {
                    overlay.clipRect(0, 0, width, height);
                    paintCrystalLights(overlay, width, height);
                } finally {
                    overlay.dispose();
                }
            } finally {
                g.dispose();
            }
        }

        private void paintCrystalLights(Graphics2D g, int width, int height) {
            double seconds = (System.nanoTime() - origin) / 1000000000.0;
            double surge = 0.0;
            if (awakenStart > 0L) {
                double progress = Math.min(1.0,
                        (double) (System.nanoTime() - awakenStart) / AWAKEN_DURATION_NS);
                surge = Math.sin(Math.PI * progress);
            }
            double unit = Math.max(1.0, Math.min(width, height) / 360.0);
            for (int i = 0; i < CRYSTALS.length; i++) {
                double[] crystal = CRYSTALS[i];
                // Different slow rhythms create intermittent rather than synchronized sparkles.
                double wave = Math.sin(seconds * (0.75 + (i % 4) * 0.13) + i * 2.43);
                double pulse = Math.pow(Math.max(0.0, wave), 8.0);
                double strength = Math.min(1.0, (0.12 + 0.82 * pulse + surge * 0.8)
                        * crystal[2]);
                double cx = crystal[0] * width;
                double cy = crystal[1] * height;
                float radius = (float) ((12.0 + 6.0 * crystal[2]) * unit);
                float alpha = (float) (0.22 * strength);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), radius,
                        new float[] {0f, 0.35f, 1f},
                        new Color[] {new Color(235, 251, 255),
                                new Color(111, 198, 255, 140),
                                new Color(111, 198, 255, 0)}));
                g.fillOval((int) (cx - radius), (int) (cy - radius),
                        (int) (radius * 2), (int) (radius * 2));

                if (pulse > 0.24 || surge > 0.15) {
                    float glint = (float) Math.min(0.9, (pulse + surge) * 0.76);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glint));
                    g.setColor(new Color(242, 252, 255));
                    g.setStroke(new BasicStroke((float) Math.max(0.7, unit * 0.8),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    double ray = (3.0 + 5.0 * strength) * unit;
                    g.drawLine((int) (cx - ray), (int) cy, (int) (cx + ray), (int) cy);
                    g.drawLine((int) cx, (int) (cy - ray * 1.4),
                            (int) cx, (int) (cy + ray * 1.4));
                }
            }
            g.setComposite(AlphaComposite.SrcOver);
        }
    }
}
