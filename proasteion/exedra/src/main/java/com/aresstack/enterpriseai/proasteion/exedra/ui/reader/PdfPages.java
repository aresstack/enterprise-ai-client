package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

/**
 * Ein PDF für das Anhang-Overlay: Apache PDFBox zeichnet jede Seite in ein {@link BufferedImage}. Nicht threadsicher;
 * das Overlay öffnet, zeichnet und schließt auf seinem einen Zeichen-Thread.
 */
final class PdfPages implements Closeable {

    /** Pixel einer gezeichneten Seite höchstens (begrenzt die Schärfe beim starken Zoomen, schützt den Speicher). */
    static final double MAX_PIXELS = 40_000_000d;

    private final PDDocument document;
    private final PDFRenderer renderer;

    private PdfPages(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    /** Öffnet das PDF; wirft mit lesbarem Grund (verschlüsselt, beschädigt). */
    static PdfPages open(byte[] pdf) throws IOException {
        try {
            return new PdfPages(PDDocument.load(pdf, "", null, null, MemoryUsageSetting.setupMainMemoryOnly()));
        } catch (InvalidPasswordException e) {
            throw new IOException("Das PDF ist mit einem Passwort geschützt", e);
        }
    }

    int pageCount() {
        return document.getNumberOfPages();
    }

    /** Die größte Zeichenskala für die Seite innerhalb von {@link #MAX_PIXELS}. */
    double maxScale(int page) {
        PDRectangle box = document.getPage(page).getCropBox();
        double area = Math.max(1d, box.getWidth() * box.getHeight());
        return Math.sqrt(MAX_PIXELS / area);
    }

    /** Die Seite (0-basiert) mit {@code scale} (1 = 72 dpi). */
    BufferedImage render(int page, double scale) throws IOException {
        float effective = (float) Math.max(0.1, Math.min(scale, maxScale(page)));
        return renderer.renderImage(page, effective, ImageType.RGB);
    }

    @Override
    public void close() throws IOException {
        document.close();
    }
}
