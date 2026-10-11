package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Grenzen von {@link PdfPages}: kein PDF, Passwortschutz, Pixelgrenze beim Zoomen, Mindestskala. */
public class PdfPagesTest {

    @Test
    public void bytesThatAreNoPdfFailWithAnIoException() {
        try {
            PdfPages.open("kein PDF".getBytes(StandardCharsets.US_ASCII)).close();
            fail("kein PDF");
        } catch (IOException expected) {
            // lesbarer Grund kommt von PDFBox
        }
    }

    @Test
    public void aPasswordProtectedPdfSaysSo() throws Exception {
        try {
            PdfPages.open(pdf(PDRectangle.A4, true)).close();
            fail("Passwort verlangt");
        } catch (IOException e) {
            assertEquals("Das PDF ist mit einem Passwort geschützt", e.getMessage());
        }
    }

    @Test
    public void renderingStaysWithinThePixelLimitHoweverFarItZooms() throws Exception {
        PdfPages pages = PdfPages.open(pdf(PDRectangle.A4, false));
        try {
            assertEquals(1, pages.pageCount());
            double max = pages.maxScale(0);
            assertEquals(PdfPages.MAX_PIXELS, PDRectangle.A4.getWidth() * PDRectangle.A4.getHeight() * max * max,
                    PdfPages.MAX_PIXELS * 1e-6);
            BufferedImage huge = pages.render(0, 1000);
            assertTrue("höchstens die Grenze (Rundung eingerechnet)",
                    (double) huge.getWidth() * huge.getHeight() <= PdfPages.MAX_PIXELS * 1.001);
        } finally {
            pages.close();
        }
    }

    @Test
    public void aTinyScaleIsRaisedToTheMinimum() throws Exception {
        PdfPages pages = PdfPages.open(pdf(PDRectangle.A4, false));
        try {
            BufferedImage image = pages.render(0, 0.0001);
            assertEquals(Math.round(PDRectangle.A4.getWidth() * 0.1), image.getWidth(), 1);
        } finally {
            pages.close();
        }
    }

    private static byte[] pdf(PDRectangle size, boolean protect) throws IOException {
        PDDocument document = new PDDocument();
        try {
            document.addPage(new PDPage(size));
            if (protect) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("besitzer", "nutzer",
                        new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } finally {
            document.close();
        }
    }
}
