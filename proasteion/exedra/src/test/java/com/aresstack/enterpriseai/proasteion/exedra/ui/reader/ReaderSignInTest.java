package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Angelos OTP-Ansicht: Verlangt die Quelle einen Einmalcode, wird der Anzeigebereich selbst zur Abfrage (kein
 * Dialog); falscher Code zeigt die Meldung der Seite, nach dem richtigen lädt die Datei an derselben Stelle.
 */
public class ReaderSignInTest {

    private static final String SRC = "https://confluence.invalid/download/attachments/7/bild.png";

    private boolean unlocked;
    private final List<String> sent = new ArrayList<String>();

    @Test
    public void anImageAsksInPlaceAndLoadsAfterTheCode() throws Exception {
        final ImageSlideshowPanel[] slideshow = new ImageSlideshowPanel[1];
        ReaderImageLoader loader = new ReaderImageLoader() {
            @Override
            public void load(ReaderImage image, Consumer<byte[]> done) {
                done.accept(null);
            }

            @Override
            public void load(ReaderImage image, Consumer<byte[]> done, Consumer<String> failed,
                             Consumer<ReaderSignIn> signIn) {
                if (unlocked) {
                    done.accept(png());
                } else {
                    signIn.accept(new FakeSignIn(""));
                }
            }
        };
        onEdt(() -> slideshow[0] = new ImageSlideshowPanel(new ReaderImageStore(
                Collections.singletonList(new ReaderImage(SRC, SRC, "Bild")), loader), 0));
        ReaderSignInPanel panel = slideshow[0].signInPanel();
        assertNotNull("Abfrage an der Stelle des Bildes", panel);

        onEdt(() -> {
            panel.codeField().setText("000000");
            panel.submitButton().doClick();
        });
        assertEquals("Ungültiger Code.", panel.errorText());
        assertNotNull(slideshow[0].signInPanel());

        onEdt(() -> {
            panel.codeField().setText("482913");
            panel.submitButton().doClick();
        });
        assertNull("nach der Bestätigung wieder das Bild", slideshow[0].signInPanel());
        assertTrue(slideshow[0].statusText(), unlocked);
        assertEquals(2, sent.size());
        assertEquals("", new String(panel.codeField().getPassword()));
    }

    @Test
    public void anAttachmentAsksInPlaceAndResendWorks() throws Exception {
        final ImageSlideshowPanel[] viewer = new ImageSlideshowPanel[1];
        final int[] loads = new int[1];
        ReaderAttachmentLoader loader = new ReaderAttachmentLoader() {
            @Override
            public void load(ReaderAttachment attachment, Consumer<byte[]> done, Consumer<String> failed) {
                failed.accept("ohne Abfrage");
            }

            @Override
            public void load(ReaderAttachment attachment, Consumer<byte[]> done, Consumer<String> failed,
                             Consumer<ReaderSignIn> signIn) {
                loads[0]++;
                if (unlocked) {
                    done.accept(png());
                } else {
                    signIn.accept(new FakeSignIn(""));
                }
            }
        };
        onEdt(() -> viewer[0] = new ImageSlideshowPanel(new ReaderAttachment("att1", "bild.png", "image/png", 10),
                loader, null, Runnable::run));
        ReaderSignInPanel panel = viewer[0].signInPanel();
        assertNotNull(panel);
        onEdt(() -> panel.resendButton().doClick());
        assertEquals("resend", sent.get(0));
        onEdt(() -> {
            panel.codeField().setText("482913");
            panel.submitButton().doClick();
        });
        assertNull(viewer[0].signInPanel());
        assertEquals("einmal vor und einmal nach der Bestätigung geladen", 2, loads[0]);
        assertTrue(viewer[0].hasOriginal());
    }

    private final class FakeSignIn implements ReaderSignIn {
        private final String error;

        FakeSignIn(String error) {
            this.error = error;
        }

        @Override
        public String service() {
            return "confluence.invalid";
        }

        @Override
        public String message() {
            return "";
        }

        @Override
        public String error() {
            return error;
        }

        @Override
        public boolean resendAvailable() {
            return true;
        }

        @Override
        public void submit(char[] code, Consumer<ReaderSignIn> next, Consumer<String> failed) {
            String value = new String(code);
            sent.add(value);
            if (value.equals("482913")) {
                unlocked = true;
                next.accept(null);
            } else {
                next.accept(new FakeSignIn("Ungültiger Code."));
            }
        }

        @Override
        public void resend(Consumer<ReaderSignIn> next, Consumer<String> failed) {
            sent.add("resend");
            next.accept(new FakeSignIn(""));
        }
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            throw ex;
        }
    }
}
