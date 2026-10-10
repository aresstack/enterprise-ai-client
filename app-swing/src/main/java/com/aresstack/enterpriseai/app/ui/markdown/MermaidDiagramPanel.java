package com.aresstack.enterpriseai.app.ui.markdown;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Rendert Mermaid abseits des EDT und zeichnet das Ergebnis mit Java2D, mit Aktionen „Bild kopieren“ und
 * „Code kopieren“ (aus askai-java8 {@code MermaidDiagramPanel}). Ein Klick auf das Bild öffnet den vergrößerten
 * Betrachter {@link MermaidViewerDialog}.
 */
public final class MermaidDiagramPanel extends JPanel {

    private static final int PADDING = 12;
    private static final int MIN_RENDER_WIDTH = 640;
    private static final int MAX_RENDER_WIDTH = 2400;

    private final String diagramCode;
    private final MarkdownTheme theme;
    private final MermaidImageRenderer imageRenderer;
    private final DiagramCanvas canvas;
    private final MarkdownActionButton copyImageButton;
    private BufferedImage image;
    private String errorMessage;
    private boolean rendering;

    public MermaidDiagramPanel(String diagramCode, MarkdownTheme theme,
                               MermaidImageRenderer imageRenderer) {
        this.diagramCode = diagramCode == null ? "" : diagramCode;
        this.theme = theme;
        this.imageRenderer = imageRenderer;
        this.canvas = new DiagramCanvas();
        this.copyImageButton = new MarkdownActionButton(new MarkdownActionButton.ImageIcon(),
                "Diagramm als Bild kopieren", theme.getMutedForeground(), this::copyImageToClipboard);
        this.copyImageButton.setEnabled(false);

        setOpaque(false);
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.getSeparatorColor()),
                BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)));
        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        renderAsync();
    }

    /** @return der unveränderte Mermaid-Quelltext dieses Diagramms */
    public String getDiagramCode() {
        return diagramCode;
    }

    private JComponent buildToolbar() {
        MarkdownActionButton copyCodeButton = new MarkdownActionButton(new MarkdownActionButton.CopyIcon(),
                "Mermaid-Code kopieren", theme.getMutedForeground(), this::copyCodeToClipboard);
        copyCodeButton.setEnabled(diagramCode.trim().length() > 0);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbar.setOpaque(false);
        toolbar.add(copyImageButton);
        toolbar.add(copyCodeButton);
        return toolbar;
    }

    private void renderAsync() {
        if (rendering) {
            return;
        }
        if (diagramCode.trim().isEmpty()) {
            errorMessage = "Das Mermaid-Diagramm ist leer.";
            canvas.repaint();
            return;
        }
        rendering = true;
        int availableWidth = Math.max(1, canvas.getWidth());
        final int targetWidth = Math.max(MIN_RENDER_WIDTH,
                Math.min(MAX_RENDER_WIDTH, availableWidth * 2));
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return imageRenderer.render(diagramCode, targetWidth);
            }

            @Override
            protected void done() {
                try {
                    image = get();
                    if (image == null) {
                        errorMessage = "Das Mermaid-Diagramm konnte nicht gerendert werden.";
                    }
                } catch (Exception ex) {
                    errorMessage = "Mermaid-Fehler: " + ex.getMessage();
                } finally {
                    rendering = false;
                    copyImageButton.setEnabled(image != null);
                    canvas.setCursor(Cursor.getPredefinedCursor(
                            image != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    canvas.updatePreferredSize();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }
        }.execute();
    }

    /** Öffnet den vergrößerten, zoombaren Betrachter; er rendert das Diagramm aus dem Mermaid-Quelltext neu. */
    private void openEnlargedViewer() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        MermaidViewerDialog.open(owner, diagramCode, imageRenderer, image);
    }

    private void copyImageToClipboard() {
        if (image == null) {
            return;
        }
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(imageTransferable(image), null);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Das Bild des Diagramms konnte nicht kopiert werden: "
                    + ex.getMessage(), "Kopieren fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void copyCodeToClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(diagramCode), null);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Der Mermaid-Code konnte nicht kopiert werden: "
                    + ex.getMessage(), "Kopieren fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Ein Zwischenablage-Transferable, das ein gerendertes Diagramm als Bild anbietet. */
    static Transferable imageTransferable(final BufferedImage image) {
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.imageFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.imageFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return image;
            }
        };
    }

    /** Die Fläche, die das gerenderte Diagramm (oder eine Statuszeile) zeichnet und ihre Höhe vom Bild ableitet. */
    private final class DiagramCanvas extends JComponent {

        DiagramCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(600, 140));
            setToolTipText("Klicken, um das Diagramm vergrößert zu öffnen");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1 && image != null) {
                        openEnlargedViewer();
                    }
                }
            });
        }

        void updatePreferredSize() {
            if (image == null) {
                setPreferredSize(new Dimension(600, 140));
                return;
            }
            int width = Math.max(1, getWidth());
            if (width <= 1) {
                width = Math.min(900, image.getWidth());
            }
            // Nie über die native Größe vergrößern, sonst aber die verfügbare Breite füllen, damit das Diagramm
            // lesbar bleibt.
            double scale = Math.min(1.0d, width / (double) image.getWidth());
            int height = (int) Math.ceil(image.getHeight() * scale);
            setPreferredSize(new Dimension(width, Math.max(80, height)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(80, height)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (image != null) {
                    paintImage(g2);
                    return;
                }
                paintStatus(g2, errorMessage == null ? "Mermaid wird gerendert …" : errorMessage,
                        errorMessage == null ? theme.getMutedForeground() : theme.getErrorForeground());
            } finally {
                g2.dispose();
            }
        }

        private void paintImage(Graphics2D graphics) {
            double scale = Math.min(1.0d, getWidth() / (double) image.getWidth());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            // Linksbündig; das Diagramm füllt die verfügbare Breite und bleibt so lesbar.
            graphics.drawImage(image, 0, 0, drawWidth, drawHeight, null);
        }

        private void paintStatus(Graphics2D graphics, String text, Color color) {
            graphics.setFont(theme.getBodyFont());
            graphics.setColor(color);
            FontMetrics metrics = graphics.getFontMetrics();
            int y = Math.max(metrics.getAscent(), (getHeight() + metrics.getAscent()) / 2);
            graphics.drawString(text, 0, y);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }
    }
}
